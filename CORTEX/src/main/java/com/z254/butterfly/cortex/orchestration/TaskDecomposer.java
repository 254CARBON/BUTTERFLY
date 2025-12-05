package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import com.z254.butterfly.cortex.llm.LLMProvider;
import com.z254.butterfly.cortex.llm.LLMProviderRegistry;
import com.z254.butterfly.cortex.llm.LLMRequest;
import com.z254.butterfly.cortex.llm.LLMResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for decomposing complex tasks into subtasks.
 * Uses LLM to intelligently break down tasks and select appropriate agents.
 */
@Service
@Slf4j
public class TaskDecomposer {

    private final AgentRepository agentRepository;
    private final LLMProviderRegistry llmProviderRegistry;

    private static final String DECOMPOSITION_PROMPT = """
        You are a task decomposition expert. Your job is to break down complex tasks into 
        smaller, manageable subtasks that can be executed by specialized agents.
        
        Given the following task, decompose it into subtasks. For each subtask, provide:
        1. A clear description of what needs to be done
        2. Required capabilities (e.g., analysis, code-generation, research, writing)
        3. Required domains (e.g., finance, software, data, general)
        4. Dependencies on other subtasks (by number)
        5. Estimated complexity (low, medium, high)
        
        Format your response as a numbered list with the following structure:
        SUBTASK 1:
        Description: [description]
        Capabilities: [comma-separated list]
        Domains: [comma-separated list]
        Dependencies: [comma-separated list of subtask numbers, or "none"]
        Complexity: [low/medium/high]
        
        Task to decompose:
        %s
        
        Available team capabilities: %s
        
        Important guidelines:
        - Create 2-7 subtasks depending on complexity
        - Ensure subtasks are atomic and independently executable
        - Order subtasks logically considering dependencies
        - Match subtasks to available capabilities when possible
        """;

    public TaskDecomposer(AgentRepository agentRepository, LLMProviderRegistry llmProviderRegistry) {
        this.agentRepository = agentRepository;
        this.llmProviderRegistry = llmProviderRegistry;
    }

    /**
     * Decompose a team task into subtasks.
     *
     * @param task The task to decompose
     * @param team The team that will execute the task
     * @return List of subtasks
     */
    public Mono<List<SubTask>> decompose(TeamTask task, AgentTeam team) {
        log.info("Decomposing task: {} for team: {}", task.getId(), team.getId());

        // Collect team capabilities
        String teamCapabilities = collectTeamCapabilities(team);

        // Get the orchestrator's LLM configuration
        return getOrchestratorLLM(team)
                .flatMap(provider -> {
                    String prompt = String.format(DECOMPOSITION_PROMPT, task.getInput(), teamCapabilities);
                    
                    LLMRequest request = LLMRequest.builder()
                            .systemPrompt("You are a task decomposition assistant.")
                            .userMessage(prompt)
                            .maxTokens(2000)
                            .temperature(0.3) // Lower temperature for more structured output
                            .build();

                    return provider.generate(request)
                            .map(response -> parseDecompositionResponse(response, task.getId()));
                })
                .doOnSuccess(subtasks -> log.info("Decomposed task {} into {} subtasks", 
                        task.getId(), subtasks.size()));
    }

    /**
     * Select the best agent for a subtask based on capability matching.
     *
     * @param subTask    The subtask to assign
     * @param candidates List of candidate agents
     * @return The best matching agent, or empty if none suitable
     */
    public Mono<Agent> selectBestAgent(SubTask subTask, List<Agent> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.empty();
        }

        Agent best = null;
        double bestScore = -1;

        for (Agent candidate : candidates) {
            if (!candidate.isActive()) {
                continue;
            }

            double score = candidate.calculateMatchScore(
                    subTask.getRequiredDomains(),
                    subTask.getRequiredCapabilities());

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            log.debug("Selected agent {} for subtask {} with score {}", 
                    best.getId(), subTask.getId(), bestScore);
            return Mono.just(best);
        }

        // Return first active agent as fallback
        return Mono.justOrEmpty(candidates.stream()
                .filter(Agent::isActive)
                .findFirst()
                .orElse(null));
    }

    /**
     * Select agents for all subtasks in a task.
     *
     * @param subTasks The subtasks to assign
     * @param team     The team with available agents
     * @return Map of subtask ID to assigned agent ID
     */
    public Mono<Map<String, String>> assignAgentsToSubTasks(List<SubTask> subTasks, AgentTeam team) {
        // Get all member agent IDs
        List<String> agentIds = team.getMembers().stream()
                .filter(member -> member.getRole() != TeamRole.ORCHESTRATOR)
                .map(TeamMember::getAgentId)
                .toList();

        // Load all agents
        return Flux.fromIterable(agentIds)
                .flatMap(agentRepository::findById)
                .collectList()
                .flatMap(agents -> {
                    Map<String, String> assignments = new HashMap<>();
                    Map<String, Integer> agentWorkload = new HashMap<>();
                    
                    // Initialize workload tracking
                    agents.forEach(a -> agentWorkload.put(a.getId(), 0));

                    for (SubTask subTask : subTasks) {
                        // Find best agent considering current workload
                        Agent best = findBestAvailableAgent(subTask, agents, agentWorkload, team);
                        if (best != null) {
                            assignments.put(subTask.getId(), best.getId());
                            agentWorkload.merge(best.getId(), 1, Integer::sum);
                            subTask.markAssigned(best.getId());
                        }
                    }

                    return Mono.just(assignments);
                });
    }

    private Agent findBestAvailableAgent(SubTask subTask, List<Agent> agents, 
                                          Map<String, Integer> workload, AgentTeam team) {
        Agent best = null;
        double bestScore = -1;
        int maxConcurrent = team.getCoordinationConfig().getMaxConcurrentSubTasks();

        for (Agent agent : agents) {
            if (!agent.isActive()) {
                continue;
            }

            // Check workload
            int currentLoad = workload.getOrDefault(agent.getId(), 0);
            if (currentLoad >= maxConcurrent) {
                continue;
            }

            double matchScore = agent.calculateMatchScore(
                    subTask.getRequiredDomains(),
                    subTask.getRequiredCapabilities());
            
            // Penalize agents with higher workload
            double loadPenalty = currentLoad * 0.1;
            double adjustedScore = matchScore - loadPenalty;

            if (adjustedScore > bestScore) {
                bestScore = adjustedScore;
                best = agent;
            }
        }

        return best;
    }

    /**
     * Estimate task complexity (0.0 - 1.0).
     */
    public double estimateComplexity(String taskInput) {
        // Simple heuristic based on task length and keywords
        int length = taskInput.length();
        double lengthScore = Math.min(1.0, length / 1000.0);

        // Check for complexity indicators
        String lower = taskInput.toLowerCase();
        double keywordScore = 0.0;
        
        String[] complexIndicators = {"analyze", "compare", "evaluate", "synthesize", 
                "multiple", "comprehensive", "detailed", "complex"};
        for (String indicator : complexIndicators) {
            if (lower.contains(indicator)) {
                keywordScore += 0.1;
            }
        }
        keywordScore = Math.min(1.0, keywordScore);

        return (lengthScore + keywordScore) / 2;
    }

    // --------------------------------------------------------------------------------------------
    // Internal helpers
    // --------------------------------------------------------------------------------------------

    private String collectTeamCapabilities(AgentTeam team) {
        Set<String> capabilities = new HashSet<>();
        Set<String> domains = new HashSet<>();

        if (team.getMembers() != null) {
            for (TeamMember member : team.getMembers()) {
                if (member.getSpecializations() != null) {
                    capabilities.addAll(member.getSpecializations());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!capabilities.isEmpty()) {
            sb.append("Capabilities: ").append(String.join(", ", capabilities));
        }
        if (!domains.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("Domains: ").append(String.join(", ", domains));
        }

        return sb.length() > 0 ? sb.toString() : "General purpose team";
    }

    private Mono<LLMProvider> getOrchestratorLLM(AgentTeam team) {
        if (team.getOrchestratorAgentId() == null) {
            // Use default provider
            return Mono.just(llmProviderRegistry.getDefaultProvider());
        }

        return agentRepository.findById(team.getOrchestratorAgentId())
                .map(agent -> {
                    if (agent.getLlmConfig() != null && agent.getLlmConfig().getProviderId() != null) {
                        return llmProviderRegistry.getProvider(agent.getLlmConfig().getProviderId());
                    }
                    return llmProviderRegistry.getDefaultProvider();
                })
                .defaultIfEmpty(llmProviderRegistry.getDefaultProvider());
    }

    private List<SubTask> parseDecompositionResponse(LLMResponse response, String parentTaskId) {
        List<SubTask> subtasks = new ArrayList<>();
        String content = response.getContent();

        // Parse SUBTASK blocks from the response
        Pattern subtaskPattern = Pattern.compile(
                "SUBTASK\\s+(\\d+):\\s*\\n?" +
                "Description:\\s*(.+?)\\n" +
                "Capabilities:\\s*(.+?)\\n" +
                "Domains:\\s*(.+?)\\n" +
                "Dependencies:\\s*(.+?)\\n" +
                "Complexity:\\s*(\\w+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher matcher = subtaskPattern.matcher(content);
        Map<Integer, SubTask> subtaskMap = new HashMap<>();

        while (matcher.find()) {
            int num = Integer.parseInt(matcher.group(1));
            String description = matcher.group(2).trim();
            Set<String> capabilities = parseCommaSeparated(matcher.group(3));
            Set<String> domains = parseCommaSeparated(matcher.group(4));
            List<String> deps = parseDependencies(matcher.group(5), subtaskMap);
            double complexity = parseComplexity(matcher.group(6));

            SubTask subtask = SubTask.builder()
                    .id(UUID.randomUUID().toString())
                    .parentTaskId(parentTaskId)
                    .sequenceNumber(num - 1)
                    .description(description)
                    .input(description)
                    .requiredCapabilities(capabilities)
                    .requiredDomains(domains)
                    .dependencies(deps)
                    .complexity(complexity)
                    .status(SubTaskStatus.PENDING)
                    .createdAt(Instant.now())
                    .timeout(Duration.ofMinutes(3))
                    .build();

            subtasks.add(subtask);
            subtaskMap.put(num, subtask);
        }

        // If parsing failed, create a single subtask with the original input
        if (subtasks.isEmpty()) {
            log.warn("Failed to parse decomposition response, creating single subtask");
            subtasks.add(SubTask.builder()
                    .id(UUID.randomUUID().toString())
                    .parentTaskId(parentTaskId)
                    .sequenceNumber(0)
                    .description("Execute task")
                    .input(content)
                    .status(SubTaskStatus.PENDING)
                    .createdAt(Instant.now())
                    .timeout(Duration.ofMinutes(5))
                    .build());
        }

        return subtasks;
    }

    private Set<String> parseCommaSeparated(String input) {
        Set<String> result = new HashSet<>();
        if (input != null && !input.isBlank()) {
            for (String item : input.split(",")) {
                String trimmed = item.trim().toLowerCase();
                if (!trimmed.isEmpty() && !trimmed.equals("none") && !trimmed.equals("n/a")) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private List<String> parseDependencies(String input, Map<Integer, SubTask> subtaskMap) {
        List<String> deps = new ArrayList<>();
        if (input == null || input.isBlank() || input.toLowerCase().contains("none")) {
            return deps;
        }

        for (String item : input.split(",")) {
            String trimmed = item.trim();
            try {
                int depNum = Integer.parseInt(trimmed);
                SubTask dep = subtaskMap.get(depNum);
                if (dep != null) {
                    deps.add(dep.getId());
                }
            } catch (NumberFormatException e) {
                // Ignore invalid dependency references
            }
        }
        return deps;
    }

    private double parseComplexity(String input) {
        if (input == null) return 0.5;
        String lower = input.toLowerCase().trim();
        return switch (lower) {
            case "low" -> 0.3;
            case "medium" -> 0.5;
            case "high" -> 0.8;
            default -> 0.5;
        };
    }
}
