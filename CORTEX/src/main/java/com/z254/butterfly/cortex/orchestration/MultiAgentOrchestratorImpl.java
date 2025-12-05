package com.z254.butterfly.cortex.orchestration;

import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import com.z254.butterfly.cortex.domain.repository.TeamRepository;
import com.z254.butterfly.cortex.orchestration.pattern.CoordinationPatternExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the MultiAgentOrchestrator interface.
 * Provides comprehensive multi-agent coordination capabilities.
 */
@Service
@Slf4j
public class MultiAgentOrchestratorImpl implements MultiAgentOrchestrator {

    private final TeamRepository teamRepository;
    private final AgentRepository agentRepository;
    private final AgentService agentService;
    private final TaskDecomposer taskDecomposer;
    private final ResultAggregator resultAggregator;
    private final AgentMessageBus messageBus;
    private final Map<CoordinationConfig.CoordinationPattern, CoordinationPatternExecutor> patternExecutors;
    private final CortexProperties cortexProperties;

    // In-memory storage for tasks and results (for demo/dev)
    private final Map<String, TeamTask> taskStore = new ConcurrentHashMap<>();
    private final Map<String, TeamResult> resultStore = new ConcurrentHashMap<>();
    private final Map<String, TeamExecutionHandle> executionHandles = new ConcurrentHashMap<>();

    public MultiAgentOrchestratorImpl(
            TeamRepository teamRepository,
            AgentRepository agentRepository,
            AgentService agentService,
            TaskDecomposer taskDecomposer,
            ResultAggregator resultAggregator,
            AgentMessageBus messageBus,
            List<CoordinationPatternExecutor> executors,
            CortexProperties cortexProperties) {
        this.teamRepository = teamRepository;
        this.agentRepository = agentRepository;
        this.agentService = agentService;
        this.taskDecomposer = taskDecomposer;
        this.resultAggregator = resultAggregator;
        this.messageBus = messageBus;
        this.cortexProperties = cortexProperties;
        
        // Build pattern executor map
        this.patternExecutors = new EnumMap<>(CoordinationConfig.CoordinationPattern.class);
        executors.forEach(executor -> patternExecutors.put(executor.getPattern(), executor));
        log.info("Initialized MultiAgentOrchestrator with {} coordination patterns", patternExecutors.size());
    }

    // --------------------------------------------------------------------------------------------
    // Team Management
    // --------------------------------------------------------------------------------------------

    @Override
    public Mono<AgentTeam> createTeam(AgentTeam team) {
        return validateTeam(team)
                .flatMap(validated -> {
                    if (validated.getId() == null) {
                        validated.setId(UUID.randomUUID().toString());
                    }
                    validated.setCreatedAt(Instant.now());
                    validated.setUpdatedAt(Instant.now());
                    if (validated.getStatus() == null) {
                        validated.setStatus(TeamStatus.DRAFT);
                    }
                    log.info("Creating team: {} ({})", validated.getName(), validated.getId());
                    return teamRepository.save(validated);
                });
    }

    @Override
    public Mono<AgentTeam> getTeam(String teamId) {
        return teamRepository.findById(teamId);
    }

    @Override
    public Mono<AgentTeam> updateTeam(String teamId, AgentTeam team) {
        return teamRepository.findById(teamId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Team not found: " + teamId)))
                .flatMap(existing -> {
                    team.setId(teamId);
                    team.setCreatedAt(existing.getCreatedAt());
                    team.setUpdatedAt(Instant.now());
                    return validateTeam(team);
                })
                .flatMap(teamRepository::save);
    }

    @Override
    public Mono<Void> deleteTeam(String teamId) {
        log.info("Deleting team: {}", teamId);
        return teamRepository.deleteById(teamId);
    }

    @Override
    public Flux<AgentTeam> listTeams() {
        return teamRepository.findAll();
    }

    @Override
    public Flux<AgentTeam> listTeamsByNamespace(String namespace) {
        return teamRepository.findByNamespace(namespace);
    }

    @Override
    public Mono<AgentTeam> activateTeam(String teamId) {
        return teamRepository.findById(teamId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Team not found: " + teamId)))
                .flatMap(team -> {
                    try {
                        team.activate();
                        log.info("Activated team: {}", teamId);
                        return teamRepository.save(team);
                    } catch (IllegalStateException e) {
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<AgentTeam> pauseTeam(String teamId) {
        return teamRepository.findById(teamId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Team not found: " + teamId)))
                .flatMap(team -> {
                    team.pause();
                    log.info("Paused team: {}", teamId);
                    return teamRepository.save(team);
                });
    }

    // --------------------------------------------------------------------------------------------
    // Task Execution
    // --------------------------------------------------------------------------------------------

    @Override
    public Mono<TeamTask> submitTask(TeamTask task) {
        return prepareTask(task)
                .flatMap(prepared -> teamRepository.findById(prepared.getTeamId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Team not found: " + prepared.getTeamId())))
                        .flatMap(team -> {
                            if (!team.isReady()) {
                                return Mono.error(new IllegalStateException("Team is not ready: " + team.getId()));
                            }
                            taskStore.put(prepared.getId(), prepared);
                            log.info("Submitted team task: {} for team: {}", prepared.getId(), team.getId());
                            return Mono.just(prepared);
                        }));
    }

    @Override
    public Flux<TeamThought> executeTaskStreaming(TeamTask task) {
        return startExecution(task)
                .flatMapMany(handle -> handle.thoughtStream);
    }

    @Override
    public Mono<TeamResult> executeTask(TeamTask task) {
        return startExecution(task)
                .flatMap(handle -> handle.resultMono);
    }

    @Override
    public Mono<TeamTask> getTask(String taskId) {
        return Mono.justOrEmpty(taskStore.get(taskId));
    }

    @Override
    public Mono<TeamResult> getTaskResult(String taskId) {
        return Mono.justOrEmpty(resultStore.get(taskId));
    }

    @Override
    public Mono<TeamTask> cancelTask(String taskId, String reason) {
        TeamExecutionHandle handle = executionHandles.remove(taskId);
        TeamTask task = taskStore.get(taskId);
        
        if (task != null) {
            task.markCancelled();
            log.info("Cancelled team task: {} - {}", taskId, reason);
        }
        
        if (handle != null) {
            handle.streamSink.tryEmitComplete();
            handle.resultSink.tryEmitValue(TeamResult.failure(taskId, 
                    task != null ? task.getTeamId() : null, "Cancelled: " + reason));
        }
        
        return Mono.justOrEmpty(task);
    }

    @Override
    public Flux<TeamTask> listTasksForTeam(String teamId) {
        return Flux.fromIterable(taskStore.values())
                .filter(task -> teamId.equals(task.getTeamId()));
    }

    // --------------------------------------------------------------------------------------------
    // Internal Execution Logic
    // --------------------------------------------------------------------------------------------

    private Mono<TeamExecutionHandle> startExecution(TeamTask rawTask) {
        return prepareTask(rawTask)
                .flatMap(task -> teamRepository.findById(task.getTeamId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Team not found: " + task.getTeamId())))
                        .flatMap(team -> {
                            if (!team.isReady()) {
                                return Mono.error(new IllegalStateException("Team is not ready: " + team.getId()));
                            }
                            return executeWithTeam(task, team);
                        }));
    }

    private Mono<TeamExecutionHandle> executeWithTeam(TeamTask task, AgentTeam team) {
        Sinks.Many<TeamThought> streamSink = Sinks.many().multicast().onBackpressureBuffer();
        Sinks.One<TeamResult> resultSink = Sinks.one();
        List<TeamThought> thoughts = Collections.synchronizedList(new ArrayList<>());

        task.markDecomposing();
        task.setCoordinationPattern(team.getCoordinationConfig().getPattern());
        taskStore.put(task.getId(), task);

        Instant startTime = Instant.now();
        
        // Emit initial thought
        TeamThought received = TeamThought.taskReceived(task.getId(), 
                team.getOrchestratorAgentId(), "Orchestrator", task.getInput());
        emitThought(received, streamSink, thoughts);

        // Execute the coordination pattern
        CoordinationPatternExecutor executor = patternExecutors.get(team.getCoordinationConfig().getPattern());
        if (executor == null) {
            // Default to hierarchical if pattern not found
            executor = patternExecutors.get(CoordinationConfig.CoordinationPattern.HIERARCHICAL);
        }

        if (executor == null) {
            String error = "No coordination pattern executor available";
            log.error(error);
            TeamResult failResult = TeamResult.failure(task.getId(), team.getId(), error);
            resultStore.put(task.getId(), failResult);
            task.markFailed(error);
            streamSink.tryEmitComplete();
            resultSink.tryEmitValue(failResult);
            return Mono.just(new TeamExecutionHandle(task.getId(), streamSink.asFlux(), resultSink.asMono()));
        }

        Duration timeout = team.getCoordinationConfig().getTaskTimeout();
        
        executor.execute(task, team, agentService, taskDecomposer, resultAggregator, messageBus)
                .timeout(timeout)
                .doOnNext(thought -> emitThought(thought, streamSink, thoughts))
                .doOnError(error -> finalizeExecution(task, team, thoughts, streamSink, resultSink, startTime, error))
                .doOnComplete(() -> finalizeExecution(task, team, thoughts, streamSink, resultSink, startTime, null))
                .subscribe();

        TeamExecutionHandle handle = new TeamExecutionHandle(task.getId(), streamSink.asFlux(), resultSink.asMono());
        handle.streamSink = streamSink;
        handle.resultSink = resultSink;
        executionHandles.put(task.getId(), handle);
        
        return Mono.just(handle);
    }

    private void emitThought(TeamThought thought, Sinks.Many<TeamThought> sink, List<TeamThought> collector) {
        collector.add(thought);
        sink.tryEmitNext(thought);
    }

    private void finalizeExecution(TeamTask task, AgentTeam team, List<TeamThought> thoughts,
                                   Sinks.Many<TeamThought> streamSink, Sinks.One<TeamResult> resultSink,
                                   Instant startTime, Throwable error) {
        TeamResult result;
        
        if (error != null) {
            log.error("Team task failed: {} - {}", task.getId(), error.getMessage());
            task.markFailed(error.getMessage());
            result = TeamResult.failure(task.getId(), team.getId(), error.getMessage());
            
            // Emit error thought
            TeamThought errorThought = TeamThought.error(task.getId(), null, error.getMessage());
            emitThought(errorThought, streamSink, thoughts);
        } else {
            // Aggregate results from subtasks
            result = resultAggregator.aggregate(task, team, thoughts);
            task.markCompleted(result.getOutput(), result.getConfidence());
            
            // Emit final output thought
            TeamThought finalThought = TeamThought.finalOutput(task.getId(), 
                    result.getOutput(), result.getConfidence());
            emitThought(finalThought, streamSink, thoughts);
        }

        result.finalize(startTime);
        result.setAgentCount(team.getMemberCount());
        resultStore.put(task.getId(), result);
        
        // Update team statistics
        team.recordTaskCompletion(result.isSuccess(), result.getDuration() != null ? 
                result.getDuration().toMillis() : 0);
        teamRepository.save(team).subscribe();
        
        executionHandles.remove(task.getId());
        streamSink.tryEmitComplete();
        resultSink.tryEmitValue(result);
        
        log.info("Team task completed: {} - status: {} duration: {}ms", 
                task.getId(), result.getStatus(), 
                result.getDuration() != null ? result.getDuration().toMillis() : 0);
    }

    // --------------------------------------------------------------------------------------------
    // Validation and Preparation
    // --------------------------------------------------------------------------------------------

    private Mono<AgentTeam> validateTeam(AgentTeam team) {
        if (team.getName() == null || team.getName().isBlank()) {
            return Mono.error(new IllegalArgumentException("Team name is required"));
        }
        
        // Validate member agents exist
        if (team.getMembers() != null && !team.getMembers().isEmpty()) {
            return Flux.fromIterable(team.getMembers())
                    .flatMap(member -> agentRepository.findById(member.getAgentId())
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                    "Agent not found: " + member.getAgentId()))))
                    .then(Mono.just(team));
        }
        
        return Mono.just(team);
    }

    private Mono<TeamTask> prepareTask(TeamTask task) {
        if (task.getId() == null) {
            task.setId(UUID.randomUUID().toString());
        }
        if (task.getCorrelationId() == null) {
            task.setCorrelationId(UUID.randomUUID().toString());
        }
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(Instant.now());
        }
        if (task.getStatus() == null) {
            task.setStatus(TeamTaskStatus.PENDING);
        }
        if (task.getCoordinationConfig() == null && cortexProperties.getMultiAgent() != null) {
            task.setTokenBudget(cortexProperties.getMultiAgent().getTokenBudget());
        }
        task.setUpdatedAt(Instant.now());
        return Mono.just(task);
    }

    /**
     * Handle representing an executing team task.
     */
    private static class TeamExecutionHandle {
        final String taskId;
        final Flux<TeamThought> thoughtStream;
        final Mono<TeamResult> resultMono;
        Sinks.Many<TeamThought> streamSink;
        Sinks.One<TeamResult> resultSink;

        TeamExecutionHandle(String taskId, Flux<TeamThought> thoughtStream, Mono<TeamResult> resultMono) {
            this.taskId = taskId;
            this.thoughtStream = thoughtStream;
            this.resultMono = resultMono;
        }
    }
}
