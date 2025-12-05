package com.z254.butterfly.cortex.orchestration.pattern;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.orchestration.AgentMessageBus;
import com.z254.butterfly.cortex.orchestration.ResultAggregator;
import com.z254.butterfly.cortex.orchestration.TaskDecomposer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parallel coordination pattern.
 * All agents work independently on the same task, results are aggregated at the end.
 * 
 * Flow:
 * 1. All agents receive the same task
 * 2. Agents work independently and concurrently
 * 3. Results are collected when all complete (or timeout)
 * 4. Results are aggregated/synthesized into final output
 */
@Component
@Slf4j
public class ParallelCoordinator implements CoordinationPatternExecutor {

    @Override
    public CoordinationConfig.CoordinationPattern getPattern() {
        return CoordinationConfig.CoordinationPattern.PARALLEL;
    }

    @Override
    public boolean supportsTeam(AgentTeam team) {
        return team.getMemberCount() >= 1;
    }

    @Override
    public Flux<TeamThought> execute(TeamTask task, AgentTeam team, AgentService agentService,
                                      TaskDecomposer taskDecomposer, ResultAggregator resultAggregator,
                                      AgentMessageBus messageBus) {
        log.info("Executing parallel coordination for task: {}", task.getId());
        
        Sinks.Many<TeamThought> thoughtSink = Sinks.many().multicast().onBackpressureBuffer();
        Map<String, ParallelResult> results = new ConcurrentHashMap<>();
        
        task.markExecuting();
        
        // Get all non-orchestrator members for parallel execution
        List<TeamMember> workers = team.getMembers().stream()
                .filter(m -> m.getRole() != TeamRole.ORCHESTRATOR)
                .toList();
        
        if (workers.isEmpty()) {
            workers = team.getMembers();
        }
        
        // Execute all agents in parallel
        int totalAgents = workers.size();
        
        Flux.fromIterable(workers)
                .flatMap(member -> executeParallelAgent(task, team, member, totalAgents, 
                        agentService, thoughtSink, results))
                .collectList()
                // Aggregate results
                .flatMap(ignored -> aggregateResults(task, team, thoughtSink, results, 
                        resultAggregator))
                .doOnSuccess(result -> {
                    task.setFinalOutput(result);
                    thoughtSink.tryEmitComplete();
                })
                .doOnError(error -> {
                    log.error("Parallel coordination failed: {}", error.getMessage());
                    thoughtSink.tryEmitNext(TeamThought.error(task.getId(), null, error.getMessage()));
                    thoughtSink.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        
        return thoughtSink.asFlux();
    }

    private Mono<ParallelResult> executeParallelAgent(TeamTask task, AgentTeam team, 
                                                       TeamMember member, int totalAgents,
                                                       AgentService agentService,
                                                       Sinks.Many<TeamThought> thoughtSink,
                                                       Map<String, ParallelResult> results) {
        String prompt = buildParallelPrompt(task.getInput(), member, totalAgents);
        
        // Emit starting thought
        thoughtSink.tryEmitNext(TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(task.getId())
                .agentId(member.getAgentId())
                .agentName(getAgentName(member))
                .agentRole(member.getRole())
                .type(TeamThought.TeamThoughtType.AGENT_THINKING)
                .content("Starting parallel execution")
                .phase("parallel")
                .timestamp(Instant.now())
                .build());

        AgentTask agentTask = AgentTask.builder()
                .agentId(member.getAgentId())
                .input(prompt)
                .userId(task.getUserId())
                .namespace(task.getNamespace())
                .status(AgentTask.TaskStatus.PENDING)
                .build();

        return agentService.executeTask(agentTask)
                .map(result -> {
                    ParallelResult pr = new ParallelResult();
                    pr.agentId = member.getAgentId();
                    pr.agentName = getAgentName(member);
                    pr.output = result.getOutput();
                    pr.success = result.getStatus() == AgentResult.ResultStatus.SUCCESS;
                    pr.confidence = extractConfidence(result.getOutput());
                    pr.tokenUsage = result.getTokenUsage() != null ? 
                            result.getTokenUsage().getTotalTokens() : 0;
                    
                    results.put(member.getAgentId(), pr);
                    
                    // Emit completion thought
                    thoughtSink.tryEmitNext(TeamThought.builder()
                            .id(UUID.randomUUID().toString())
                            .teamTaskId(task.getId())
                            .agentId(member.getAgentId())
                            .agentName(pr.agentName)
                            .agentRole(member.getRole())
                            .type(TeamThought.TeamThoughtType.TASK_COMPLETED)
                            .content(pr.output)
                            .confidence(pr.confidence)
                            .tokenCount(pr.tokenUsage)
                            .phase("parallel")
                            .timestamp(Instant.now())
                            .build());
                    
                    return pr;
                })
                .onErrorResume(error -> {
                    ParallelResult pr = new ParallelResult();
                    pr.agentId = member.getAgentId();
                    pr.agentName = getAgentName(member);
                    pr.output = null;
                    pr.success = false;
                    pr.error = error.getMessage();
                    pr.confidence = 0;
                    
                    results.put(member.getAgentId(), pr);
                    
                    thoughtSink.tryEmitNext(TeamThought.builder()
                            .id(UUID.randomUUID().toString())
                            .teamTaskId(task.getId())
                            .agentId(member.getAgentId())
                            .agentName(pr.agentName)
                            .type(TeamThought.TeamThoughtType.TASK_FAILED)
                            .content(error.getMessage())
                            .phase("parallel")
                            .timestamp(Instant.now())
                            .build());
                    
                    return Mono.just(pr);
                });
    }

    private Mono<String> aggregateResults(TeamTask task, AgentTeam team,
                                          Sinks.Many<TeamThought> thoughtSink,
                                          Map<String, ParallelResult> results,
                                          ResultAggregator resultAggregator) {
        thoughtSink.tryEmitNext(TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(task.getId())
                .type(TeamThought.TeamThoughtType.AGGREGATING)
                .content("Aggregating " + results.size() + " parallel results")
                .phase("aggregation")
                .timestamp(Instant.now())
                .build());

        // Get successful results
        List<ParallelResult> successful = results.values().stream()
                .filter(r -> r.success && r.output != null)
                .toList();
        
        if (successful.isEmpty()) {
            // All failed
            String errorMsg = results.values().stream()
                    .filter(r -> !r.success)
                    .map(r -> r.error)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("All agents failed");
            return Mono.error(new RuntimeException(errorMsg));
        }
        
        if (successful.size() == 1) {
            // Only one success, use it directly
            return Mono.just(successful.get(0).output);
        }
        
        // Multiple successes - aggregate based on strategy
        CoordinationConfig.AggregationStrategy strategy = 
                team.getCoordinationConfig().getAggregationStrategy();
        
        return switch (strategy) {
            case HIGHEST_CONFIDENCE -> Mono.just(aggregateByHighestConfidence(successful));
            case WEIGHTED_CONFIDENCE -> Mono.just(aggregateByWeightedConfidence(successful));
            case MAJORITY_VOTE -> Mono.just(aggregateByMajorityVote(successful));
            case SYNTHESIS -> synthesizeResults(task, successful, team);
            default -> Mono.just(aggregateByHighestConfidence(successful));
        };
    }

    private String buildParallelPrompt(String task, TeamMember member, int totalAgents) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are one of ").append(totalAgents).append(" experts working in parallel on this task.\n\n");
        
        if (member.getSpecializations() != null && !member.getSpecializations().isEmpty()) {
            prompt.append("Your specialization: ").append(String.join(", ", member.getSpecializations())).append("\n\n");
        }
        
        prompt.append("Task:\n").append(task).append("\n\n");
        prompt.append("Please provide your complete, independent response to this task.\n");
        prompt.append("Focus on your area of expertise if applicable.\n");
        prompt.append("Your response will be combined with other experts' responses.\n");
        
        return prompt.toString();
    }

    private String aggregateByHighestConfidence(List<ParallelResult> results) {
        return results.stream()
                .max(Comparator.comparingDouble(r -> r.confidence))
                .map(r -> r.output)
                .orElse("No results");
    }

    private String aggregateByWeightedConfidence(List<ParallelResult> results) {
        // For text, weighted confidence means selecting the highest confidence result
        // and noting the agreement level
        ParallelResult best = results.stream()
                .max(Comparator.comparingDouble(r -> r.confidence))
                .orElse(null);
        
        if (best == null) {
            return "No results";
        }
        
        double avgConfidence = results.stream()
                .mapToDouble(r -> r.confidence)
                .average()
                .orElse(0.5);
        
        return String.format("%s\n\n[Confidence: %.2f, Average across %d agents: %.2f]",
                best.output, best.confidence, results.size(), avgConfidence);
    }

    private String aggregateByMajorityVote(List<ParallelResult> results) {
        // Simple majority: take the most common response (by similarity)
        // For now, just take the one with most confidence
        return aggregateByHighestConfidence(results);
    }

    private Mono<String> synthesizeResults(TeamTask task, List<ParallelResult> results, AgentTeam team) {
        // Build synthesis prompt
        StringBuilder synthesisPrompt = new StringBuilder();
        synthesisPrompt.append("Multiple experts have provided responses to this task:\n\n");
        synthesisPrompt.append("Original task: ").append(task.getInput()).append("\n\n");
        synthesisPrompt.append("Expert responses:\n\n");
        
        int i = 1;
        for (ParallelResult r : results) {
            synthesisPrompt.append("Expert ").append(i++).append(" (").append(r.agentName).append("):\n");
            synthesisPrompt.append(r.output).append("\n\n");
        }
        
        synthesisPrompt.append("Please synthesize these responses into a comprehensive final answer that:\n");
        synthesisPrompt.append("1. Incorporates the best insights from each expert\n");
        synthesisPrompt.append("2. Resolves any conflicts or differences\n");
        synthesisPrompt.append("3. Provides a unified, coherent response\n");
        
        // Use orchestrator or first available agent for synthesis
        String synthesizerAgentId = team.getOrchestratorAgentId();
        if (synthesizerAgentId == null && !team.getMembers().isEmpty()) {
            synthesizerAgentId = team.getMembers().get(0).getAgentId();
        }
        
        if (synthesizerAgentId == null) {
            // Fallback to highest confidence
            return Mono.just(aggregateByHighestConfidence(results));
        }
        
        // For simplicity, return concatenated results
        // In production, this would call the LLM for synthesis
        StringBuilder combined = new StringBuilder();
        combined.append("Synthesized from ").append(results.size()).append(" expert responses:\n\n");
        for (ParallelResult r : results) {
            combined.append("--- ").append(r.agentName).append(" ---\n");
            combined.append(r.output).append("\n\n");
        }
        return Mono.just(combined.toString());
    }

    private double extractConfidence(String output) {
        // Simple heuristic - in practice would be more sophisticated
        if (output == null) return 0;
        
        // Look for confidence markers
        String lower = output.toLowerCase();
        if (lower.contains("certain") || lower.contains("confident")) return 0.9;
        if (lower.contains("likely") || lower.contains("probably")) return 0.7;
        if (lower.contains("possibly") || lower.contains("might")) return 0.5;
        if (lower.contains("uncertain") || lower.contains("unclear")) return 0.3;
        
        return 0.7; // Default confidence
    }

    private String getAgentName(TeamMember member) {
        String agentId = member.getAgentId();
        return "Parallel-" + agentId.substring(0, Math.min(8, agentId.length()));
    }

    private static class ParallelResult {
        String agentId;
        String agentName;
        String output;
        boolean success;
        double confidence;
        int tokenUsage;
        String error;
    }
}
