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
 * Hierarchical coordination pattern.
 * The orchestrator decomposes tasks and delegates to specialists.
 * 
 * Flow:
 * 1. Orchestrator receives task
 * 2. Task is decomposed into subtasks
 * 3. Subtasks are assigned to specialists based on capabilities
 * 4. Specialists execute subtasks (potentially in parallel)
 * 5. Critic reviews outputs (if enabled)
 * 6. Orchestrator synthesizes final result
 */
@Component
@Slf4j
public class HierarchicalCoordinator implements CoordinationPatternExecutor {

    @Override
    public CoordinationConfig.CoordinationPattern getPattern() {
        return CoordinationConfig.CoordinationPattern.HIERARCHICAL;
    }

    @Override
    public boolean supportsTeam(AgentTeam team) {
        // Hierarchical pattern requires an orchestrator
        return team.getOrchestratorAgentId() != null;
    }

    @Override
    public Flux<TeamThought> execute(TeamTask task, AgentTeam team, AgentService agentService,
                                      TaskDecomposer taskDecomposer, ResultAggregator resultAggregator,
                                      AgentMessageBus messageBus) {
        log.info("Executing hierarchical coordination for task: {}", task.getId());
        
        Sinks.Many<TeamThought> thoughtSink = Sinks.many().multicast().onBackpressureBuffer();
        Map<String, SubTask> completedSubTasks = new ConcurrentHashMap<>();
        
        // Phase 1: Decompose task
        taskDecomposer.decompose(task, team)
                .doOnNext(subtasks -> {
                    task.getSubTasks().addAll(subtasks);
                    task.markExecuting();
                    
                    thoughtSink.tryEmitNext(TeamThought.decomposing(
                            task.getId(),
                            team.getOrchestratorAgentId(),
                            "Orchestrator",
                            subtasks.size()));
                })
                // Phase 2: Assign agents
                .flatMap(subtasks -> taskDecomposer.assignAgentsToSubTasks(subtasks, team)
                        .thenReturn(subtasks))
                // Phase 3: Execute subtasks
                .flatMapMany(subtasks -> executeSubTasks(task, team, subtasks, agentService, 
                        messageBus, thoughtSink, completedSubTasks))
                // Phase 4: Optional critic review
                .then(Mono.defer(() -> {
                    if (team.getCoordinationConfig().isEnableCriticReview()) {
                        return executeCriticReview(task, team, agentService, thoughtSink, 
                                completedSubTasks);
                    }
                    return Mono.empty();
                }))
                // Phase 5: Synthesize result
                .then(Mono.defer(() -> synthesizeResult(task, team, agentService, thoughtSink, 
                        completedSubTasks)))
                .doOnSuccess(result -> {
                    task.setFinalOutput(result);
                    thoughtSink.tryEmitComplete();
                })
                .doOnError(error -> {
                    log.error("Hierarchical coordination failed: {}", error.getMessage());
                    thoughtSink.tryEmitNext(TeamThought.error(task.getId(), null, error.getMessage()));
                    thoughtSink.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        
        return thoughtSink.asFlux();
    }

    private Flux<TeamThought> executeSubTasks(TeamTask task, AgentTeam team, List<SubTask> subtasks,
                                               AgentService agentService, AgentMessageBus messageBus,
                                               Sinks.Many<TeamThought> thoughtSink,
                                               Map<String, SubTask> completedSubTasks) {
        CoordinationConfig config = team.getCoordinationConfig();
        
        // Process subtasks respecting dependencies
        return Flux.defer(() -> {
            List<SubTask> executable = new ArrayList<>();
            List<SubTask> pending = new ArrayList<>(subtasks);
            
            // Find initially executable subtasks (no dependencies)
            Iterator<SubTask> iter = pending.iterator();
            while (iter.hasNext()) {
                SubTask st = iter.next();
                if (st.dependenciesSatisfied(subtasks)) {
                    executable.add(st);
                    iter.remove();
                }
            }
            
            if (executable.isEmpty() && !pending.isEmpty()) {
                // All remaining have unmet dependencies - execute first pending
                executable.add(pending.remove(0));
            }
            
            return Flux.fromIterable(executable);
        })
        .flatMap(subtask -> executeSubTask(task, team, subtask, agentService, messageBus, thoughtSink)
                .doOnSuccess(result -> {
                    completedSubTasks.put(subtask.getId(), subtask);
                }), 
                config.getMaxConcurrentSubTasks()) // Limit concurrency
        .thenMany(Flux.defer(() -> {
            // Check for more subtasks that can now be executed
            List<SubTask> remaining = task.getSubTasks().stream()
                    .filter(st -> !completedSubTasks.containsKey(st.getId()))
                    .filter(st -> st.dependenciesSatisfied(task.getSubTasks()))
                    .toList();
            
            if (remaining.isEmpty()) {
                return Flux.empty();
            }
            
            return executeSubTasks(task, team, remaining, agentService, messageBus, 
                    thoughtSink, completedSubTasks);
        }));
    }

    private Mono<SubTask> executeSubTask(TeamTask task, AgentTeam team, SubTask subtask,
                                          AgentService agentService, AgentMessageBus messageBus,
                                          Sinks.Many<TeamThought> thoughtSink) {
        String agentId = subtask.getAssignedAgentId();
        if (agentId == null) {
            subtask.markFailed("No agent assigned");
            return Mono.just(subtask);
        }
        
        subtask.markExecuting();
        
        // Emit thinking thought
        thoughtSink.tryEmitNext(TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(task.getId())
                .subTaskId(subtask.getId())
                .agentId(agentId)
                .type(TeamThought.TeamThoughtType.AGENT_THINKING)
                .content("Starting subtask: " + subtask.getDescription())
                .phase("execution")
                .timestamp(Instant.now())
                .build());

        // Create agent task from subtask
        AgentTask agentTask = AgentTask.builder()
                .agentId(agentId)
                .input(subtask.getInput() != null ? subtask.getInput() : subtask.getDescription())
                .userId(task.getUserId())
                .namespace(task.getNamespace())
                .correlationId(task.getCorrelationId())
                .status(AgentTask.TaskStatus.PENDING)
                .build();

        return agentService.executeTask(agentTask)
                .map(result -> {
                    if (result.getStatus() == AgentResult.ResultStatus.SUCCESS) {
                        subtask.markCompleted(result.getOutput(), 0.8);
                        
                        thoughtSink.tryEmitNext(TeamThought.builder()
                                .id(UUID.randomUUID().toString())
                                .teamTaskId(task.getId())
                                .subTaskId(subtask.getId())
                                .agentId(agentId)
                                .type(TeamThought.TeamThoughtType.TASK_COMPLETED)
                                .content(result.getOutput())
                                .confidence(0.8)
                                .tokenCount(result.getTokenUsage() != null ? 
                                        result.getTokenUsage().getTotalTokens() : 0)
                                .phase("execution")
                                .timestamp(Instant.now())
                                .build());
                    } else {
                        subtask.markFailed(result.getErrorMessage());
                        
                        thoughtSink.tryEmitNext(TeamThought.builder()
                                .id(UUID.randomUUID().toString())
                                .teamTaskId(task.getId())
                                .subTaskId(subtask.getId())
                                .agentId(agentId)
                                .type(TeamThought.TeamThoughtType.TASK_FAILED)
                                .content(result.getErrorMessage())
                                .phase("execution")
                                .timestamp(Instant.now())
                                .build());
                    }
                    return subtask;
                })
                .onErrorResume(error -> {
                    subtask.markFailed(error.getMessage());
                    return Mono.just(subtask);
                });
    }

    private Mono<Void> executeCriticReview(TeamTask task, AgentTeam team, AgentService agentService,
                                           Sinks.Many<TeamThought> thoughtSink,
                                           Map<String, SubTask> completedSubTasks) {
        // Find critic agent
        Optional<TeamMember> criticOpt = team.getMembersByRole(TeamRole.CRITIC).stream()
                .filter(TeamMember::canAcceptTasks)
                .findFirst();
        
        if (criticOpt.isEmpty()) {
            log.debug("No critic agent available for review");
            return Mono.empty();
        }
        
        TeamMember critic = criticOpt.get();
        
        // Build review request
        StringBuilder reviewInput = new StringBuilder("Review the following task outputs:\n\n");
        for (SubTask subtask : completedSubTasks.values()) {
            if (subtask.getStatus() == SubTaskStatus.COMPLETED && subtask.getResult() != null) {
                reviewInput.append("Subtask: ").append(subtask.getDescription()).append("\n");
                reviewInput.append("Output: ").append(subtask.getResult()).append("\n\n");
            }
        }
        reviewInput.append("Please identify any issues, inconsistencies, or areas for improvement.");

        thoughtSink.tryEmitNext(TeamThought.builder()
                .id(UUID.randomUUID().toString())
                .teamTaskId(task.getId())
                .agentId(critic.getAgentId())
                .agentRole(TeamRole.CRITIC)
                .type(TeamThought.TeamThoughtType.REVIEW_STARTED)
                .content("Starting critic review")
                .phase("review")
                .timestamp(Instant.now())
                .build());

        AgentTask reviewTask = AgentTask.builder()
                .agentId(critic.getAgentId())
                .input(reviewInput.toString())
                .userId(task.getUserId())
                .namespace(task.getNamespace())
                .status(AgentTask.TaskStatus.PENDING)
                .build();

        return agentService.executeTask(reviewTask)
                .doOnNext(result -> {
                    thoughtSink.tryEmitNext(TeamThought.critiqueGiven(
                            task.getId(), null, critic.getAgentId(), "Critic",
                            result.getOutput(), 0.8));
                })
                .then();
    }

    private Mono<String> synthesizeResult(TeamTask task, AgentTeam team, AgentService agentService,
                                          Sinks.Many<TeamThought> thoughtSink,
                                          Map<String, SubTask> completedSubTasks) {
        String orchestratorId = team.getOrchestratorAgentId();
        
        thoughtSink.tryEmitNext(TeamThought.synthesizing(
                task.getId(), orchestratorId, "Orchestrator",
                "Synthesizing results from " + completedSubTasks.size() + " subtasks"));

        // Build synthesis input
        StringBuilder synthesisInput = new StringBuilder();
        synthesisInput.append("Original task: ").append(task.getInput()).append("\n\n");
        synthesisInput.append("Subtask results:\n\n");
        
        for (SubTask subtask : completedSubTasks.values()) {
            if (subtask.getStatus() == SubTaskStatus.COMPLETED && subtask.getResult() != null) {
                synthesisInput.append("- ").append(subtask.getDescription()).append(":\n");
                synthesisInput.append("  ").append(subtask.getResult()).append("\n\n");
            }
        }
        synthesisInput.append("\nPlease synthesize these results into a comprehensive final answer.");

        AgentTask synthesisTask = AgentTask.builder()
                .agentId(orchestratorId)
                .input(synthesisInput.toString())
                .userId(task.getUserId())
                .namespace(task.getNamespace())
                .status(AgentTask.TaskStatus.PENDING)
                .build();

        return agentService.executeTask(synthesisTask)
                .map(AgentResult::getOutput)
                .defaultIfEmpty("Task completed with " + completedSubTasks.size() + " subtask results.");
    }
}
