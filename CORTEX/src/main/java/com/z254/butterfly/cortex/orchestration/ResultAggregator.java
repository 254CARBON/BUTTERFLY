package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.llm.LLMProvider;
import com.z254.butterfly.cortex.llm.LLMProviderRegistry;
import com.z254.butterfly.cortex.llm.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for aggregating results from multiple agents.
 * Implements various aggregation strategies for different coordination patterns.
 */
@Service
@Slf4j
public class ResultAggregator {

    private final LLMProviderRegistry llmProviderRegistry;

    private static final String SYNTHESIS_PROMPT = """
        You are a result synthesizer. Your job is to combine multiple outputs from different
        agents into a single, coherent response.
        
        Task: %s
        
        Agent outputs:
        %s
        
        Please synthesize these outputs into a single, comprehensive response that:
        1. Incorporates the best insights from each agent
        2. Resolves any conflicts or contradictions
        3. Presents a unified, well-structured answer
        4. Maintains factual accuracy
        
        Provide your synthesized response:
        """;

    public ResultAggregator(LLMProviderRegistry llmProviderRegistry) {
        this.llmProviderRegistry = llmProviderRegistry;
    }

    /**
     * Aggregate results from a team task execution.
     *
     * @param task     The team task
     * @param team     The team that executed the task
     * @param thoughts All thoughts from the execution
     * @return Aggregated result
     */
    public TeamResult aggregate(TeamTask task, AgentTeam team, List<TeamThought> thoughts) {
        CoordinationConfig config = team.getCoordinationConfig();
        CoordinationConfig.AggregationStrategy strategy = config.getAggregationStrategy();

        log.info("Aggregating results for task {} using strategy: {}", task.getId(), strategy);

        return switch (strategy) {
            case WEIGHTED_CONFIDENCE -> aggregateByWeightedConfidence(task, team, thoughts);
            case MAJORITY_VOTE -> aggregateByMajorityVote(task, team, thoughts);
            case HIGHEST_CONFIDENCE -> aggregateByHighestConfidence(task, team, thoughts);
            case SYNTHESIS -> aggregateBySynthesis(task, team, thoughts);
            case SEQUENTIAL -> aggregateSequential(task, team, thoughts);
            case ORCHESTRATOR_DECISION -> aggregateByOrchestratorDecision(task, team, thoughts);
        };
    }

    /**
     * Aggregate using weighted confidence scores.
     */
    private TeamResult aggregateByWeightedConfidence(TeamTask task, AgentTeam team, 
                                                      List<TeamThought> thoughts) {
        List<SubTaskResult> subTaskResults = extractSubTaskResults(task, thoughts);
        
        // Calculate weighted average of outputs
        double totalWeight = 0;
        double weightedConfidence = 0;
        StringBuilder outputBuilder = new StringBuilder();

        for (SubTaskResult result : subTaskResults) {
            double weight = result.confidence != null ? result.confidence : 0.5;
            totalWeight += weight;
            weightedConfidence += weight * weight; // Square confidence for weighting
            
            if (result.output != null && !result.output.isBlank()) {
                if (outputBuilder.length() > 0) {
                    outputBuilder.append("\n\n");
                }
                outputBuilder.append(result.output);
            }
        }

        double finalConfidence = totalWeight > 0 ? weightedConfidence / totalWeight : 0.5;

        return buildResult(task, team, outputBuilder.toString(), finalConfidence, 
                subTaskResults, "weighted_confidence");
    }

    /**
     * Aggregate using majority voting (for debate scenarios).
     */
    private TeamResult aggregateByMajorityVote(TeamTask task, AgentTeam team, 
                                                List<TeamThought> thoughts) {
        // Extract vote/position thoughts
        Map<String, Integer> positionVotes = new HashMap<>();
        Map<String, Double> positionConfidence = new HashMap<>();
        Map<String, String> positionContent = new HashMap<>();

        for (TeamThought thought : thoughts) {
            if (thought.getType() == TeamThought.TeamThoughtType.VOTE ||
                thought.getType() == TeamThought.TeamThoughtType.POSITION_STATED ||
                thought.getType() == TeamThought.TeamThoughtType.PROPOSAL) {
                
                String position = normalizePosition(thought.getContent());
                positionVotes.merge(position, 1, Integer::sum);
                
                double conf = thought.getConfidence() != null ? thought.getConfidence() : 0.5;
                positionConfidence.merge(position, conf, (old, newConf) -> Math.max(old, newConf));
                
                // Keep the most detailed content for each position
                String existing = positionContent.get(position);
                if (existing == null || thought.getContent().length() > existing.length()) {
                    positionContent.put(position, thought.getContent());
                }
            }
        }

        // Find majority position
        String majorityPosition = positionVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (majorityPosition == null) {
            // Fallback to highest confidence
            return aggregateByHighestConfidence(task, team, thoughts);
        }

        double confidence = positionConfidence.getOrDefault(majorityPosition, 0.5);
        String output = positionContent.getOrDefault(majorityPosition, majorityPosition);

        // Calculate consensus level
        int totalVotes = positionVotes.values().stream().mapToInt(Integer::intValue).sum();
        int majorityVotes = positionVotes.getOrDefault(majorityPosition, 0);
        double consensusLevel = totalVotes > 0 ? (double) majorityVotes / totalVotes : 0.0;

        TeamResult result = buildResult(task, team, output, confidence, 
                extractSubTaskResults(task, thoughts), "majority_vote");
        result.setConsensusLevel(consensusLevel);
        result.setDebateRounds(extractMaxRound(thoughts));
        
        return result;
    }

    /**
     * Aggregate by taking the highest confidence result.
     */
    private TeamResult aggregateByHighestConfidence(TeamTask task, AgentTeam team, 
                                                     List<TeamThought> thoughts) {
        TeamThought best = null;
        double highestConfidence = -1;

        for (TeamThought thought : thoughts) {
            if (thought.getType() == TeamThought.TeamThoughtType.TASK_COMPLETED ||
                thought.getType() == TeamThought.TeamThoughtType.FINAL_OUTPUT) {
                
                double conf = thought.getConfidence() != null ? thought.getConfidence() : 0.5;
                if (conf > highestConfidence) {
                    highestConfidence = conf;
                    best = thought;
                }
            }
        }

        String output = best != null ? best.getContent() : findFinalOutput(thoughts);
        double confidence = best != null && best.getConfidence() != null ? best.getConfidence() : 0.5;

        return buildResult(task, team, output, confidence, 
                extractSubTaskResults(task, thoughts), "highest_confidence");
    }

    /**
     * Aggregate by synthesizing all outputs using LLM.
     */
    private TeamResult aggregateBySynthesis(TeamTask task, AgentTeam team, 
                                             List<TeamThought> thoughts) {
        List<SubTaskResult> subTaskResults = extractSubTaskResults(task, thoughts);
        
        if (subTaskResults.size() <= 1) {
            // No need to synthesize single result
            String output = subTaskResults.isEmpty() ? 
                    findFinalOutput(thoughts) : 
                    subTaskResults.get(0).output;
            double confidence = subTaskResults.isEmpty() ? 0.5 :
                    (subTaskResults.get(0).confidence != null ? subTaskResults.get(0).confidence : 0.5);
            
            return buildResult(task, team, output, confidence, subTaskResults, "synthesis");
        }

        // Build agent outputs string
        StringBuilder outputsBuilder = new StringBuilder();
        int i = 1;
        for (SubTaskResult result : subTaskResults) {
            outputsBuilder.append(String.format("Agent %d (%s):\n%s\n\n", 
                    i++, 
                    result.agentName != null ? result.agentName : "Unknown",
                    result.output != null ? result.output : "(no output)"));
        }

        // Use LLM to synthesize
        try {
            String synthesized = synthesizeWithLLM(task.getInput(), outputsBuilder.toString());
            double avgConfidence = subTaskResults.stream()
                    .filter(r -> r.confidence != null)
                    .mapToDouble(r -> r.confidence)
                    .average()
                    .orElse(0.5);
            
            return buildResult(task, team, synthesized, avgConfidence, subTaskResults, "synthesis");
        } catch (Exception e) {
            log.warn("LLM synthesis failed, falling back to concatenation: {}", e.getMessage());
            String fallback = subTaskResults.stream()
                    .map(r -> r.output)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n"));
            return buildResult(task, team, fallback, 0.5, subTaskResults, "synthesis");
        }
    }

    /**
     * Aggregate sequential outputs (chain pattern).
     */
    private TeamResult aggregateSequential(TeamTask task, AgentTeam team, 
                                           List<TeamThought> thoughts) {
        // For chain pattern, take the final output from the last agent
        String output = findFinalOutput(thoughts);
        double confidence = findFinalConfidence(thoughts);

        return buildResult(task, team, output, confidence, 
                extractSubTaskResults(task, thoughts), "sequential");
    }

    /**
     * Use orchestrator's final decision.
     */
    private TeamResult aggregateByOrchestratorDecision(TeamTask task, AgentTeam team, 
                                                        List<TeamThought> thoughts) {
        // Find orchestrator's final output
        String orchestratorId = team.getOrchestratorAgentId();
        
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            TeamThought thought = thoughts.get(i);
            if (orchestratorId != null && orchestratorId.equals(thought.getAgentId())) {
                if (thought.getType() == TeamThought.TeamThoughtType.FINAL_OUTPUT ||
                    thought.getType() == TeamThought.TeamThoughtType.TASK_COMPLETED) {
                    
                    return buildResult(task, team, thought.getContent(),
                            thought.getConfidence() != null ? thought.getConfidence() : 0.7,
                            extractSubTaskResults(task, thoughts), "orchestrator_decision");
                }
            }
        }

        // Fallback to highest confidence
        return aggregateByHighestConfidence(task, team, thoughts);
    }

    // --------------------------------------------------------------------------------------------
    // Helper methods
    // --------------------------------------------------------------------------------------------

    private TeamResult buildResult(TeamTask task, AgentTeam team, String output, 
                                   double confidence, List<SubTaskResult> subTaskResults,
                                   String method) {
        int totalTokens = subTaskResults.stream()
                .mapToInt(r -> r.tokenUsage)
                .sum();

        int successful = (int) subTaskResults.stream()
                .filter(r -> r.status == SubTaskStatus.COMPLETED)
                .count();
        int failed = (int) subTaskResults.stream()
                .filter(r -> r.status == SubTaskStatus.FAILED)
                .count();

        Map<String, Double> agentConfidences = new HashMap<>();
        Map<String, Integer> tokensByAgent = new HashMap<>();
        for (SubTaskResult r : subTaskResults) {
            if (r.agentId != null) {
                if (r.confidence != null) {
                    agentConfidences.put(r.agentId, r.confidence);
                }
                tokensByAgent.merge(r.agentId, r.tokenUsage, Integer::sum);
            }
        }

        return TeamResult.builder()
                .teamTaskId(task.getId())
                .teamId(team.getId())
                .status(TeamTaskStatus.COMPLETED)
                .output(output)
                .confidence(confidence)
                .agentConfidences(agentConfidences)
                .subTaskResults(subTaskResults.stream()
                        .map(r -> TeamResult.SubTaskResult.builder()
                                .subTaskId(r.subTaskId)
                                .agentId(r.agentId)
                                .agentName(r.agentName)
                                .status(r.status)
                                .output(r.output)
                                .confidence(r.confidence)
                                .tokenUsage(r.tokenUsage)
                                .duration(r.duration)
                                .build())
                        .toList())
                .aggregationMethod(method)
                .agentCount(team.getMemberCount())
                .subTaskCount(subTaskResults.size())
                .successfulSubTasks(successful)
                .failedSubTasks(failed)
                .totalTokens(totalTokens)
                .tokensByAgent(tokensByAgent)
                .build();
    }

    private List<SubTaskResult> extractSubTaskResults(TeamTask task, List<TeamThought> thoughts) {
        List<SubTaskResult> results = new ArrayList<>();
        Map<String, SubTaskResult> bySubTask = new HashMap<>();

        // Group by subtask
        for (TeamThought thought : thoughts) {
            if (thought.getSubTaskId() != null && 
                (thought.getType() == TeamThought.TeamThoughtType.TASK_COMPLETED ||
                 thought.getType() == TeamThought.TeamThoughtType.TASK_FAILED)) {
                
                SubTaskResult result = new SubTaskResult();
                result.subTaskId = thought.getSubTaskId();
                result.agentId = thought.getAgentId();
                result.agentName = thought.getAgentName();
                result.status = thought.getType() == TeamThought.TeamThoughtType.TASK_COMPLETED ?
                        SubTaskStatus.COMPLETED : SubTaskStatus.FAILED;
                result.output = thought.getContent();
                result.confidence = thought.getConfidence();
                result.tokenUsage = thought.getTokenCount() != null ? thought.getTokenCount() : 0;
                result.duration = thought.getDurationMs() != null ? 
                        Duration.ofMillis(thought.getDurationMs()) : Duration.ZERO;
                
                bySubTask.put(thought.getSubTaskId(), result);
            }
        }

        results.addAll(bySubTask.values());
        
        // Also check task's subtasks
        if (task.getSubTasks() != null) {
            for (SubTask subTask : task.getSubTasks()) {
                if (!bySubTask.containsKey(subTask.getId()) && subTask.getResult() != null) {
                    SubTaskResult result = new SubTaskResult();
                    result.subTaskId = subTask.getId();
                    result.agentId = subTask.getAssignedAgentId();
                    result.status = subTask.getStatus();
                    result.output = subTask.getResult();
                    result.confidence = subTask.getResultConfidence();
                    result.tokenUsage = subTask.getTokenUsage() != null ? subTask.getTokenUsage() : 0;
                    result.duration = subTask.getDuration();
                    results.add(result);
                }
            }
        }

        return results;
    }

    private String findFinalOutput(List<TeamThought> thoughts) {
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            TeamThought thought = thoughts.get(i);
            if (thought.getType() == TeamThought.TeamThoughtType.FINAL_OUTPUT ||
                thought.getType() == TeamThought.TeamThoughtType.TASK_COMPLETED) {
                return thought.getContent();
            }
        }
        
        // Fallback: find any agent output
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            TeamThought thought = thoughts.get(i);
            if (thought.getContent() != null && !thought.getContent().isBlank()) {
                return thought.getContent();
            }
        }
        
        return "Task completed";
    }

    private double findFinalConfidence(List<TeamThought> thoughts) {
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            TeamThought thought = thoughts.get(i);
            if (thought.getConfidence() != null) {
                return thought.getConfidence();
            }
        }
        return 0.5;
    }

    private int extractMaxRound(List<TeamThought> thoughts) {
        return thoughts.stream()
                .mapToInt(TeamThought::getRound)
                .max()
                .orElse(0);
    }

    private String normalizePosition(String content) {
        if (content == null) return "";
        // Simple normalization: lowercase, trim, take first 100 chars
        String normalized = content.toLowerCase().trim();
        if (normalized.length() > 100) {
            normalized = normalized.substring(0, 100);
        }
        return normalized;
    }

    private String synthesizeWithLLM(String task, String outputs) {
        String prompt = String.format(SYNTHESIS_PROMPT, task, outputs);
        
        LLMRequest request = LLMRequest.builder()
                .systemPrompt("You are a result synthesizer assistant.")
                .userMessage(prompt)
                .maxTokens(2000)
                .temperature(0.5)
                .build();

        LLMProvider provider = llmProviderRegistry.getDefaultProvider();
        return provider.generate(request)
                .map(response -> response.getContent())
                .block(Duration.ofSeconds(30));
    }

    /**
     * Internal helper class for subtask results.
     */
    private static class SubTaskResult {
        String subTaskId;
        String agentId;
        String agentName;
        SubTaskStatus status;
        String output;
        Double confidence;
        int tokenUsage;
        Duration duration;
    }
}
