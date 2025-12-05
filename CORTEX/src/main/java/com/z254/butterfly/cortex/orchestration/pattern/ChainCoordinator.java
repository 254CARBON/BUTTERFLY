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

/**
 * Chain-of-thought coordination pattern.
 * Agents process sequentially, each refining the output of the previous one.
 * 
 * Flow:
 * 1. First agent processes the original task
 * 2. Each subsequent agent receives previous output + original task
 * 3. Each agent refines and improves the response
 * 4. Final agent produces the polished output
 */
@Component
@Slf4j
public class ChainCoordinator implements CoordinationPatternExecutor {

    @Override
    public CoordinationConfig.CoordinationPattern getPattern() {
        return CoordinationConfig.CoordinationPattern.CHAIN;
    }

    @Override
    public boolean supportsTeam(AgentTeam team) {
        // Chain pattern requires at least 2 agents for meaningful refinement
        return team.getMemberCount() >= 2;
    }

    @Override
    public Flux<TeamThought> execute(TeamTask task, AgentTeam team, AgentService agentService,
                                      TaskDecomposer taskDecomposer, ResultAggregator resultAggregator,
                                      AgentMessageBus messageBus) {
        log.info("Executing chain coordination for task: {}", task.getId());
        
        Sinks.Many<TeamThought> thoughtSink = Sinks.many().multicast().onBackpressureBuffer();
        
        task.markExecuting();
        
        // Get ordered list of agents (orchestrator last if present)
        List<TeamMember> orderedMembers = getOrderedMembers(team);
        
        // Execute chain
        executeChain(task, team, orderedMembers, agentService, messageBus, thoughtSink)
                .doOnSuccess(result -> {
                    task.setFinalOutput(result);
                    thoughtSink.tryEmitComplete();
                })
                .doOnError(error -> {
                    log.error("Chain coordination failed: {}", error.getMessage());
                    thoughtSink.tryEmitNext(TeamThought.error(task.getId(), null, error.getMessage()));
                    thoughtSink.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        
        return thoughtSink.asFlux();
    }

    private List<TeamMember> getOrderedMembers(AgentTeam team) {
        List<TeamMember> members = new ArrayList<>(team.getMembers());
        
        // Sort by priority (lower priority first, orchestrator last)
        members.sort((a, b) -> {
            if (a.getRole() == TeamRole.ORCHESTRATOR) return 1;
            if (b.getRole() == TeamRole.ORCHESTRATOR) return -1;
            return Integer.compare(a.getPriority(), b.getPriority());
        });
        
        return members;
    }

    private Mono<String> executeChain(TeamTask task, AgentTeam team, List<TeamMember> members,
                                       AgentService agentService, AgentMessageBus messageBus,
                                       Sinks.Many<TeamThought> thoughtSink) {
        // Start with original input
        String originalTask = task.getInput();
        
        return Flux.fromIterable(members)
                .index()
                .reduce(Mono.just(new ChainState(originalTask, null, 0.5)),
                        (stateMono, indexed) -> stateMono.flatMap(state -> 
                                processChainStep(task, team, indexed.getT2(), indexed.getT1().intValue(),
                                        members.size(), state, agentService, messageBus, thoughtSink)))
                .flatMap(state -> state)
                .map(state -> state.currentOutput);
    }

    private Mono<ChainState> processChainStep(TeamTask task, AgentTeam team, TeamMember member,
                                               int stepIndex, int totalSteps, ChainState state,
                                               AgentService agentService, AgentMessageBus messageBus,
                                               Sinks.Many<TeamThought> thoughtSink) {
        boolean isFirst = stepIndex == 0;
        boolean isLast = stepIndex == totalSteps - 1;
        
        String prompt;
        if (isFirst) {
            prompt = buildFirstPrompt(state.originalTask);
        } else if (isLast) {
            prompt = buildFinalPrompt(state.originalTask, state.currentOutput);
        } else {
            prompt = buildRefinementPrompt(state.originalTask, state.currentOutput, stepIndex + 1, totalSteps);
        }
        
        // Emit thinking thought
        thoughtSink.tryEmitNext(TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(task.getId())
                .agentId(member.getAgentId())
                .agentName(getAgentName(member, stepIndex))
                .agentRole(member.getRole())
                .type(TeamThought.TeamThoughtType.AGENT_THINKING)
                .content("Step " + (stepIndex + 1) + "/" + totalSteps + ": " + 
                        (isFirst ? "Initial processing" : isLast ? "Final refinement" : "Refining"))
                .iteration(stepIndex)
                .phase("chain")
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
                    String output = result.getOutput();
                    double confidence = extractConfidence(output, state.confidence);
                    
                    // Emit completion thought
                    thoughtSink.tryEmitNext(TeamThought.builder()
                            .id(UUID.randomUUID().toString())
                            .teamTaskId(task.getId())
                            .agentId(member.getAgentId())
                            .agentName(getAgentName(member, stepIndex))
                            .agentRole(member.getRole())
                            .type(isLast ? TeamThought.TeamThoughtType.FINAL_OUTPUT : 
                                    TeamThought.TeamThoughtType.TASK_COMPLETED)
                            .content(output)
                            .confidence(confidence)
                            .iteration(stepIndex)
                            .tokenCount(result.getTokenUsage() != null ? 
                                    result.getTokenUsage().getTotalTokens() : 0)
                            .phase("chain")
                            .timestamp(Instant.now())
                            .build());
                    
                    // Send handoff message to next agent (if not last)
                    if (!isLast && stepIndex + 1 < totalSteps) {
                        TeamMember nextMember = getNextMember(team.getMembers(), stepIndex + 1);
                        if (nextMember != null) {
                            AgentMessage handoff = AgentMessage.handoff(
                                    task.getId(), member.getAgentId(), nextMember.getAgentId(),
                                    null, "Chain handoff", Map.of(
                                            "step", stepIndex,
                                            "output", output,
                                            "confidence", confidence
                                    ));
                            messageBus.send(handoff).subscribe();
                        }
                    }
                    
                    return new ChainState(state.originalTask, output, confidence);
                })
                .onErrorResume(error -> {
                    log.warn("Chain step {} failed: {}", stepIndex, error.getMessage());
                    // Continue with previous output on error
                    return Mono.just(new ChainState(state.originalTask, 
                            state.currentOutput != null ? state.currentOutput : state.originalTask,
                            state.confidence * 0.9));
                });
    }

    private String buildFirstPrompt(String task) {
        return String.format("""
                You are the first agent in a chain of experts. Your task is to provide an initial response 
                to the following request:
                
                %s
                
                Please provide a thorough initial response. This will be refined by subsequent experts.
                Focus on accuracy and completeness. Include any relevant context or considerations.
                """, task);
    }

    private String buildRefinementPrompt(String originalTask, String previousOutput, int step, int totalSteps) {
        return String.format("""
                You are agent %d of %d in a chain of experts. Your task is to refine and improve 
                the previous agent's response.
                
                Original request:
                %s
                
                Previous agent's response:
                %s
                
                Please:
                1. Identify any errors, gaps, or areas for improvement
                2. Add missing information or perspectives
                3. Clarify or restructure if needed
                4. Provide your refined version
                
                Your improved response:
                """, step, totalSteps, originalTask, previousOutput);
    }

    private String buildFinalPrompt(String originalTask, String previousOutput) {
        return String.format("""
                You are the final expert in a chain. Your task is to polish and finalize the response.
                
                Original request:
                %s
                
                Current response (after multiple refinements):
                %s
                
                Please:
                1. Ensure accuracy and completeness
                2. Improve clarity and readability
                3. Fix any remaining issues
                4. Provide the final, polished response
                
                Final response:
                """, originalTask, previousOutput);
    }

    private double extractConfidence(String output, double previousConfidence) {
        // Simple heuristic: confidence increases slightly with each refinement
        // In practice, this could be more sophisticated
        return Math.min(1.0, previousConfidence * 1.05);
    }

    private String getAgentName(TeamMember member, int index) {
        return "Chain-Agent-" + (index + 1);
    }

    private TeamMember getNextMember(List<TeamMember> members, int index) {
        if (index < members.size()) {
            return members.get(index);
        }
        return null;
    }

    private static class ChainState {
        final String originalTask;
        final String currentOutput;
        final double confidence;
        
        ChainState(String originalTask, String currentOutput, double confidence) {
            this.originalTask = originalTask;
            this.currentOutput = currentOutput;
            this.confidence = confidence;
        }
    }
}
