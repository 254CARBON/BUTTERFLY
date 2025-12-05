package com.z254.butterfly.cortex.agent.impl;

import com.z254.butterfly.cortex.agent.AgentExecutor;
import com.z254.butterfly.cortex.agent.AgentExecutorFactory;
import com.z254.butterfly.cortex.agent.AgentService;
import com.z254.butterfly.cortex.domain.model.Agent;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.domain.model.AgentResult;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import com.z254.butterfly.cortex.domain.model.AgentThought;
import com.z254.butterfly.cortex.domain.model.Conversation;
import com.z254.butterfly.cortex.domain.repository.AgentRepository;
import com.z254.butterfly.cortex.domain.repository.ConversationRepository;
import com.z254.butterfly.cortex.kafka.AgentResultProducer;
import com.z254.butterfly.cortex.llm.guardrails.TokenBudgetEnforcer;
import com.z254.butterfly.cortex.memory.EpisodicMemory;
import com.z254.butterfly.cortex.memory.capsule.CapsuleMemoryAdapter;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager.QuotaCheckResult;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager.QuotaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link AgentService}.
 * Provides an in-memory orchestration layer that wires agents, memory, governance and streaming.
 */
@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;
    private final ConversationRepository conversationRepository;
    private final AgentExecutorFactory executorFactory;
    private final TokenBudgetEnforcer tokenBudgetEnforcer;
    private final ExecutionQuotaManager quotaManager;
    private final EpisodicMemory episodicMemory;
    private final CapsuleMemoryAdapter capsuleMemoryAdapter;
    private final AgentResultProducer resultProducer;

    private final Map<String, AgentTask> taskStore = new ConcurrentHashMap<>();
    private final Map<String, AgentResult> resultStore = new ConcurrentHashMap<>();
    private final Map<String, TaskExecutionHandle> executionHandles = new ConcurrentHashMap<>();

    public AgentServiceImpl(
            AgentRepository agentRepository,
            ConversationRepository conversationRepository,
            AgentExecutorFactory executorFactory,
            TokenBudgetEnforcer tokenBudgetEnforcer,
            ExecutionQuotaManager quotaManager,
            EpisodicMemory episodicMemory,
            CapsuleMemoryAdapter capsuleMemoryAdapter,
            AgentResultProducer resultProducer) {
        this.agentRepository = agentRepository;
        this.conversationRepository = conversationRepository;
        this.executorFactory = executorFactory;
        this.tokenBudgetEnforcer = tokenBudgetEnforcer;
        this.quotaManager = quotaManager;
        this.episodicMemory = episodicMemory;
        this.capsuleMemoryAdapter = capsuleMemoryAdapter;
        this.resultProducer = resultProducer;
    }

    // --------------------------------------------------------------------------------------------
    // Agent management
    // --------------------------------------------------------------------------------------------

    @Override
    public Mono<Agent> createAgent(Agent agent) {
        Agent toSave = prepareAgent(agent);
        log.info("Creating agent {}", toSave.getName());
        return agentRepository.save(toSave);
    }

    @Override
    public Mono<Agent> getAgent(String agentId) {
        return agentRepository.findById(agentId);
    }

    @Override
    public Mono<Agent> updateAgent(String agentId, Agent agent) {
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Agent not found: " + agentId)))
                .flatMap(existing -> {
                    Agent updated = prepareAgent(agent);
                    updated.setId(agentId);
                    updated.setCreatedAt(existing.getCreatedAt());
                    return agentRepository.save(updated);
                });
    }

    @Override
    public Mono<Void> deleteAgent(String agentId) {
        log.info("Deleting agent {}", agentId);
        return agentRepository.deleteById(agentId);
    }

    @Override
    public Flux<Agent> listAgents() {
        return agentRepository.findAll();
    }

    @Override
    public Flux<Agent> listAgentsByNamespace(String namespace) {
        return agentRepository.findByNamespace(namespace);
    }

    // --------------------------------------------------------------------------------------------
    // Task execution
    // --------------------------------------------------------------------------------------------

    @Override
    public Mono<AgentTask> submitTask(AgentTask task) {
        AgentTask normalized = prepareTask(task);
        return agentRepository.findById(normalized.getAgentId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Agent not found: " + normalized.getAgentId())))
                .map(agent -> {
                    taskStore.put(normalized.getId(), normalized);
                    return normalized;
                });
    }

    @Override
    public Flux<AgentThought> executeTaskStreaming(AgentTask task) {
        return startExecution(task)
                .flatMapMany(TaskExecutionHandle::thoughtStream);
    }

    @Override
    public Mono<AgentResult> executeTask(AgentTask task) {
        return startExecution(task)
                .flatMap(TaskExecutionHandle::resultMono);
    }

    @Override
    public Mono<AgentTask> getTask(String taskId) {
        AgentTask existing = taskStore.get(taskId);
        return existing != null ? Mono.just(existing) : Mono.empty();
    }

    @Override
    public Mono<AgentResult> getTaskResult(String taskId) {
        AgentResult result = resultStore.get(taskId);
        return result != null ? Mono.just(result) : Mono.empty();
    }

    @Override
    public Mono<AgentTask> cancelTask(String taskId, String reason) {
        TaskExecutionHandle handle = executionHandles.remove(taskId);
        if (handle == null) {
            return Mono.justOrEmpty(taskStore.get(taskId));
        }
        handle.executor().cancel(taskId).subscribe();
        AgentTask task = taskStore.get(taskId);
        if (task == null) {
            task = AgentTask.builder()
                    .id(taskId)
                    .agentId(handle.context().getAgentId())
                    .build();
        }
        if (task != null) {
            task.setStatus(AgentTask.TaskStatus.CANCELLED);
            task.setCompletedAt(Instant.now());
        }
        handle.resultSink().tryEmitValue(buildFailedResult(task, handle.context(), Collections.emptyList(),
                new IllegalStateException(reason)));
        handle.streamSink().tryEmitComplete();
        return Mono.justOrEmpty(task);
    }

    // --------------------------------------------------------------------------------------------
    // Conversation management / chat
    // --------------------------------------------------------------------------------------------

    @Override
    public Mono<Conversation> createConversation(String agentId, String userId) {
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Agent not found: " + agentId)))
                .flatMap(agent -> {
                    Conversation conversation = Conversation.builder()
                            .agentId(agentId)
                            .userId(userId)
                            .status(Conversation.ConversationStatus.ACTIVE)
                            .title(agent.getName() + " conversation")
                            .namespace(agent.getNamespace())
                            .createdAt(Instant.now())
                            .lastActiveAt(Instant.now())
                            .build();
                    return conversationRepository.save(conversation);
                });
    }

    @Override
    public Mono<Conversation> getConversation(String conversationId) {
        return conversationRepository.findById(conversationId);
    }

    @Override
    public Flux<AgentThought> chat(String conversationId, String message) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation not found: " + conversationId)))
                .flatMapMany(conversation -> {
                    AgentTask task = AgentTask.builder()
                            .agentId(conversation.getAgentId())
                            .conversationId(conversationId)
                            .input(message)
                            .userId(conversation.getUserId())
                            .namespace(conversation.getNamespace())
                            .status(AgentTask.TaskStatus.PENDING)
                            .build();
                    recordUserMessage(conversation, message, task.getId());
                    return executeTaskStreaming(task);
                });
    }

    @Override
    public Mono<AgentResult> chatSync(String conversationId, String message) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    AgentTask task = AgentTask.builder()
                            .agentId(conversation.getAgentId())
                            .conversationId(conversationId)
                            .input(message)
                            .userId(conversation.getUserId())
                            .namespace(conversation.getNamespace())
                            .status(AgentTask.TaskStatus.PENDING)
                            .build();
                    recordUserMessage(conversation, message, task.getId());
                    return executeTask(task);
                });
    }

    @Override
    public Flux<Conversation> listConversations(String userId) {
        return conversationRepository.findByUserId(userId);
    }

    @Override
    public Mono<Conversation> archiveConversation(String conversationId) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    conversation.setStatus(Conversation.ConversationStatus.ARCHIVED);
                    conversation.setLastActiveAt(Instant.now());
                    return conversationRepository.save(conversation);
                });
    }

    @Override
    public Mono<Void> deleteConversation(String conversationId) {
        log.info("Deleting conversation: {}", conversationId);
        return conversationRepository.deleteById(conversationId);
    }

    // --------------------------------------------------------------------------------------------
    // Internal helpers
    // --------------------------------------------------------------------------------------------

    private Agent prepareAgent(Agent agent) {
        if (agent.getId() == null) {
            agent.setId(UUID.randomUUID().toString());
        }
        if (agent.getStatus() == null) {
            agent.setStatus(Agent.AgentStatus.ACTIVE);
        }
        if (agent.getCreatedAt() == null) {
            agent.setCreatedAt(Instant.now());
        }
        agent.setUpdatedAt(Instant.now());
        return agent;
    }

    private AgentTask prepareTask(AgentTask task) {
        if (task.getId() == null) {
            task.setId(UUID.randomUUID().toString());
        }
        if (task.getCorrelationId() == null) {
            task.setCorrelationId(UUID.randomUUID().toString());
        }
        if (task.getIdempotencyKey() == null) {
            task.setIdempotencyKey(UUID.randomUUID().toString());
        }
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(Instant.now());
        }
        if (task.getStatus() == null) {
            task.setStatus(AgentTask.TaskStatus.PENDING);
        }
        return task;
    }

    private Mono<TaskExecutionHandle> startExecution(AgentTask rawTask) {
        AgentTask task = prepareTask(rawTask);

        return agentRepository.findById(task.getAgentId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Agent not found: " + task.getAgentId())))
                .flatMap(agent -> ensureAgentReady(agent, task))
                .flatMap(agent -> quotaManager.checkQuota(task.getUserId(), agent.getId(), task.getNamespace())
                        .flatMap(result -> handleQuota(agent, task, result)))
                .flatMap(agent -> {
                    AgentExecutor executor = executorFactory.getExecutor(agent.getType());
                    AgentContext context = buildAgentContext(task, agent);
                    Sinks.Many<AgentThought> streamSink = Sinks.many().multicast().onBackpressureBuffer();
                    Sinks.One<AgentResult> resultSink = Sinks.one();
                    List<AgentThought> thoughts = Collections.synchronizedList(new ArrayList<>());

                    AgentTask runningTask = task;
                    runningTask.setStatus(AgentTask.TaskStatus.RUNNING);
                    runningTask.setStartedAt(Instant.now());
                    taskStore.put(runningTask.getId(), runningTask);

                    executor.execute(runningTask, context)
                            .doOnNext(thought -> handleThought(runningTask, thought, streamSink, thoughts))
                            .doOnError(error -> finalizeExecution(runningTask, context, thoughts, streamSink, resultSink, error))
                            .doOnComplete(() -> finalizeExecution(runningTask, context, thoughts, streamSink, resultSink, null))
                            .subscribe();

                    TaskExecutionHandle handle = new TaskExecutionHandle(
                            runningTask.getId(),
                            executor,
                            context,
                            streamSink,
                            resultSink);
                    executionHandles.put(runningTask.getId(), handle);
                    return Mono.just(handle);
                });
    }

    private Mono<Agent> ensureAgentReady(Agent agent, AgentTask task) {
        if (agent.getStatus() != Agent.AgentStatus.ACTIVE) {
            return Mono.error(new IllegalStateException("Agent is not active: " + agent.getId()));
        }
        if (agent.getToolIds() == null) {
            agent.setToolIds(Collections.emptySet());
        }
        return Mono.just(agent);
    }

    private Mono<Agent> handleQuota(Agent agent, AgentTask task, QuotaCheckResult quotaCheckResult) {
        if (!quotaCheckResult.isAllowed()) {
            String reason = quotaCheckResult.getReason() != null ? quotaCheckResult.getReason()
                    : "Quota exceeded";
            return Mono.error(new IllegalStateException("Quota exceeded (" +
                    Optional.ofNullable(quotaCheckResult.getQuotaType()).map(Enum::name).orElse("UNKNOWN")
                    + "): " + reason));
        }
        return quotaManager.recordExecution(task.getUserId(), agent.getId(), task.getNamespace())
                .thenReturn(agent);
    }

    private AgentContext buildAgentContext(AgentTask task, Agent agent) {
        Integer requestedMaxTokens = agent.getLlmConfig() != null ? agent.getLlmConfig().getMaxTokens() : null;
        AgentContext.TokenBudget budget = tokenBudgetEnforcer.createTaskBudget(requestedMaxTokens);
        AgentContext context = AgentContext.forTask(task, agent, budget.getMaxTokens());
        context.setTokenBudget(budget);
        context.setCreatedAt(Instant.now());
        if (task.getConversationId() != null) {
            try {
                List<AgentContext.Message> messages = episodicMemory.retrieve(task.getConversationId(), 20)
                        .map(episode -> AgentContext.Message.builder()
                                .role(episode.getRole())
                                .content(episode.getContent())
                                .timestamp(episode.getTimestamp())
                                .build())
                        .collectList()
                        .block(Duration.ofSeconds(1));
                if (messages != null && !messages.isEmpty()) {
                    context.setMessages(messages);
                }
            } catch (Exception e) {
                log.debug("Failed to load episodic memory for conversation {}: {}", task.getConversationId(), e.getMessage());
            }
        }
        return context;
    }

    private void handleThought(AgentTask task,
                               AgentThought thought,
                               Sinks.Many<AgentThought> sink,
                               List<AgentThought> collector) {
        collector.add(thought);
        sink.tryEmitNext(thought);
        if (resultProducer != null) {
            try {
                resultProducer.publishThought(thought);
            } catch (Exception e) {
                log.debug("Failed to publish thought {}: {}", thought.getId(), e.getMessage());
            }
        }
    }

    private void finalizeExecution(AgentTask task,
                                   AgentContext context,
                                   List<AgentThought> thoughts,
                                   Sinks.Many<AgentThought> streamSink,
                                   Sinks.One<AgentResult> resultSink,
                                   Throwable error) {
        Instant completedAt = Instant.now();
        task.setCompletedAt(completedAt);
        if (error != null) {
            task.setStatus(AgentTask.TaskStatus.FAILED);
        } else {
            task.setStatus(AgentTask.TaskStatus.COMPLETED);
        }
        AgentResult result = error == null
                ? buildSuccessResult(task, context, thoughts, completedAt)
                : buildFailedResult(task, context, thoughts, error);

        resultStore.put(result.getTaskId(), result);
        if (resultProducer != null) {
            try {
                resultProducer.publishResult(result);
            } catch (Exception e) {
                log.debug("Failed to publish result for task {}: {}", task.getId(), e.getMessage());
            }
        }
        updateConversationState(task, result);
        executionHandles.remove(task.getId());
        streamSink.tryEmitComplete();
        resultSink.tryEmitValue(result);
    }

    private AgentResult buildSuccessResult(AgentTask task,
                                           AgentContext context,
                                           List<AgentThought> thoughts,
                                           Instant completedAt) {
        AgentResult.ResultStatus status = AgentResult.ResultStatus.SUCCESS;
        String output = findFinalOutput(thoughts).orElse("Task completed");
        AgentResult.TokenUsage tokenUsage = toTokenUsage(context);
        Duration duration = Duration.between(task.getStartedAt(), completedAt);
        return AgentResult.builder()
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .conversationId(task.getConversationId())
                .correlationId(task.getCorrelationId())
                .output(output)
                .thoughts(new ArrayList<>(thoughts))
                .toolCalls(toToolInvocations(thoughts))
                .tokenUsage(tokenUsage)
                .status(status)
                .iterations(thoughts.isEmpty() ? 0 : thoughts.get(thoughts.size() - 1).getIteration())
                .startedAt(task.getStartedAt())
                .completedAt(completedAt)
                .duration(duration)
                .build();
    }

    private AgentResult buildFailedResult(AgentTask task,
                                          AgentContext context,
                                          List<AgentThought> thoughts,
                                          Throwable error) {
        Instant completedAt = Instant.now();
        String output = error != null ? error.getMessage() : "Task failed";
        AgentResult.TokenUsage tokenUsage = toTokenUsage(context);
        Duration duration = task.getStartedAt() != null
                ? Duration.between(task.getStartedAt(), completedAt)
                : Duration.ZERO;
        return AgentResult.builder()
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .conversationId(task.getConversationId())
                .correlationId(task.getCorrelationId())
                .output("Error: " + output)
                .thoughts(new ArrayList<>(thoughts))
                .toolCalls(toToolInvocations(thoughts))
                .tokenUsage(tokenUsage)
                .status(AgentResult.ResultStatus.FAILURE)
                .errorMessage(output)
                .startedAt(task.getStartedAt())
                .completedAt(completedAt)
                .duration(duration)
                .build();
    }

    private Optional<String> findFinalOutput(List<AgentThought> thoughts) {
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            AgentThought thought = thoughts.get(i);
            if (thought.getType() == AgentThought.ThoughtType.FINAL_ANSWER
                    || thought.getType() == AgentThought.ThoughtType.ERROR) {
                return Optional.ofNullable(thought.getContent());
            }
        }
        return Optional.empty();
    }

    private List<AgentResult.ToolInvocation> toToolInvocations(List<AgentThought> thoughts) {
        List<AgentResult.ToolInvocation> invocations = new ArrayList<>();
        for (AgentThought thought : thoughts) {
            if (thought.getType() == AgentThought.ThoughtType.TOOL_CALL) {
                invocations.add(AgentResult.ToolInvocation.builder()
                        .id(thought.getId())
                        .toolId(thought.getToolId())
                        .toolName(thought.getToolId())
                        .parameters(thought.getToolParameters())
                        .timestamp(thought.getTimestamp())
                        .build());
            }
        }
        return invocations;
    }

    private AgentResult.TokenUsage toTokenUsage(AgentContext context) {
        if (context == null || context.getTokenBudget() == null) {
            return null;
        }
        AgentContext.TokenBudget budget = context.getTokenBudget();
        return AgentResult.TokenUsage.builder()
                .inputTokens(budget.getUsedInputTokens())
                .outputTokens(budget.getUsedOutputTokens())
                .totalTokens(budget.getUsedTokens())
                .build();
    }

    private void updateConversationState(AgentTask task, AgentResult result) {
        if (task.getConversationId() == null) {
            return;
        }
        conversationRepository.findById(task.getConversationId())
                .flatMap(conversation -> {
                    conversation.addTaskId(task.getId());
                    conversation.addMessage("assistant", result.getOutput(), result.getTokenUsage() != null
                            ? result.getTokenUsage().getTotalTokens()
                            : null);
                    conversation.setLastActiveAt(Instant.now());
                    return conversationRepository.save(conversation)
                            .doOnSuccess(capsuleMemoryAdapter::markForSync);
                })
                .subscribe();
        episodicMemory.record(task.getConversationId(),
                EpisodicMemory.Episode.assistantMessage(result.getOutput(), task.getId()))
                .subscribe();
    }

    private void recordUserMessage(Conversation conversation, String message, String taskId) {
        conversation.addTaskId(taskId);
        conversation.addMessage("user", message, null);
        conversation.setLastActiveAt(Instant.now());
        conversationRepository.save(conversation).subscribe();
        if (conversation.getId() != null) {
            episodicMemory.record(conversation.getId(),
                    EpisodicMemory.Episode.userMessage(message, taskId)).subscribe();
        }
    }

    /**
     * Handle representing an executing task.
     */
    private record TaskExecutionHandle(
            String taskId,
            AgentExecutor executor,
            AgentContext context,
            Sinks.Many<AgentThought> streamSink,
            Sinks.One<AgentResult> resultSink) {

        public Flux<AgentThought> thoughtStream() {
            return streamSink.asFlux();
        }

        public Mono<AgentResult> resultMono() {
            return resultSink.asMono();
        }
    }
}
