package com.z254.butterfly.cortex.agent.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.cortex.agent.AgentExecutor;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.config.CortexProperties.AgentType;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.llm.LLMProvider;
import com.z254.butterfly.cortex.llm.LLMProviderRegistry;
import com.z254.butterfly.cortex.llm.LLMRequest;
import com.z254.butterfly.cortex.llm.LLMResponse;
import com.z254.butterfly.cortex.tool.ToolRegistry;
import com.z254.butterfly.cortex.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReAct Agent implementation.
 * Implements the Reason-Act-Observe loop for tool-using tasks.
 * 
 * <p>The ReAct pattern:
 * <ol>
 *   <li>Reason: Think about what to do next based on the goal and observations</li>
 *   <li>Act: Choose and execute a tool, or provide final answer</li>
 *   <li>Observe: Process the tool result</li>
 *   <li>Repeat until final answer or max iterations</li>
 * </ol>
 */
@Component
@Slf4j
public class ReActAgent implements AgentExecutor {

    private static final String REACT_SYSTEM_PROMPT = """
        You are a helpful AI assistant that can use tools to help answer questions and complete tasks.
        
        When given a task, think step by step:
        1. Analyze what information or actions are needed
        2. Use available tools when helpful
        3. Observe the results and decide next steps
        4. Continue until you can provide a complete answer
        
        Always explain your reasoning before taking actions.
        When you have enough information to answer, provide your final response.
        """;

    private final LLMProviderRegistry llmProviderRegistry;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final CortexProperties cortexProperties;
    
    // Track running tasks for cancellation
    private final Map<String, Boolean> runningTasks = new ConcurrentHashMap<>();

    public ReActAgent(
            LLMProviderRegistry llmProviderRegistry,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            CortexProperties cortexProperties) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.cortexProperties = cortexProperties;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.REACT;
    }

    @Override
    public Flux<AgentThought> execute(AgentTask task, AgentContext context) {
        String taskId = task.getId();
        runningTasks.put(taskId, true);
        
        Sinks.Many<AgentThought> thoughtSink = Sinks.many().multicast().onBackpressureBuffer();
        
        // Start the ReAct loop
        executeLoop(task, context, thoughtSink, 0, new ArrayList<>())
                .doOnTerminate(() -> runningTasks.remove(taskId))
                .subscribe();
        
        return thoughtSink.asFlux();
    }

    @Override
    public Mono<AgentResult> executeSync(AgentTask task, AgentContext context) {
        return execute(task, context)
                .collectList()
                .map(thoughts -> buildResult(task, context, thoughts));
    }

    private Mono<Void> executeLoop(
            AgentTask task,
            AgentContext context,
            Sinks.Many<AgentThought> thoughtSink,
            int iteration,
            List<AgentThought> allThoughts) {
        
        String taskId = task.getId();
        int maxIterations = cortexProperties.getAgent().getReact().getMaxIterations();
        
        // Check for cancellation
        if (!runningTasks.getOrDefault(taskId, false)) {
            log.info("Task {} cancelled", taskId);
            thoughtSink.tryEmitComplete();
            return Mono.empty();
        }
        
        // Check max iterations
        if (iteration >= maxIterations) {
            log.warn("Task {} hit max iterations ({})", taskId, maxIterations);
            AgentThought stopThought = AgentThought.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .agentId(task.getAgentId())
                    .type(AgentThought.ThoughtType.STOP)
                    .content("Reached maximum iterations without completing the task.")
                    .iteration(iteration)
                    .timestamp(Instant.now())
                    .build();
            thoughtSink.tryEmitNext(stopThought);
            allThoughts.add(stopThought);
            thoughtSink.tryEmitComplete();
            return Mono.empty();
        }
        
        // Check token budget
        if (context.getTokenBudget() != null && !context.getTokenBudget().hasRemaining()) {
            log.warn("Task {} exceeded token budget", taskId);
            AgentThought stopThought = AgentThought.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .agentId(task.getAgentId())
                    .type(AgentThought.ThoughtType.STOP)
                    .content("Token budget exceeded.")
                    .iteration(iteration)
                    .timestamp(Instant.now())
                    .build();
            thoughtSink.tryEmitNext(stopThought);
            allThoughts.add(stopThought);
            thoughtSink.tryEmitComplete();
            return Mono.empty();
        }
        
        log.debug("Task {} iteration {}", taskId, iteration);
        
        return generateResponse(task, context, iteration)
                .flatMap(response -> {
                    // Record token usage
                    if (response.getUsage() != null) {
                        context.addTokenUsage(
                                response.getUsage().getPromptTokens(),
                                response.getUsage().getCompletionTokens()
                        );
                    }
                    
                    // Handle tool calls
                    if (response.hasToolCalls()) {
                        return handleToolCalls(task, context, response, thoughtSink, iteration, allThoughts)
                                .then(Mono.defer(() -> executeLoop(task, context, thoughtSink, iteration + 1, allThoughts)));
                    }
                    
                    // Final answer
                    String content = response.getContent();
                    AgentThought finalThought = AgentThought.builder()
                            .id(UUID.randomUUID().toString())
                            .taskId(taskId)
                            .agentId(task.getAgentId())
                            .type(AgentThought.ThoughtType.FINAL_ANSWER)
                            .content(content)
                            .iteration(iteration)
                            .timestamp(Instant.now())
                            .build();
                    thoughtSink.tryEmitNext(finalThought);
                    allThoughts.add(finalThought);
                    thoughtSink.tryEmitComplete();
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.error("Error in ReAct loop for task {}: {}", taskId, e.getMessage());
                    AgentThought errorThought = AgentThought.error(taskId, task.getAgentId(), e.getMessage(), iteration);
                    thoughtSink.tryEmitNext(errorThought);
                    allThoughts.add(errorThought);
                    thoughtSink.tryEmitComplete();
                    return Mono.empty();
                });
    }

    private Mono<LLMResponse> generateResponse(AgentTask task, AgentContext context, int iteration) {
        LLMProvider provider = llmProviderRegistry.getDefaultProvider();
        
        // Build messages
        List<LLMRequest.Message> messages = buildMessages(task, context);
        
        // Build tools
        List<LLMRequest.Tool> tools = new ArrayList<>();
        if (context.getAvailableTools() != null && !context.getAvailableTools().isEmpty()) {
            tools = toolRegistry.toLLMTools(context.getAvailableTools());
        }
        
        LLMRequest request = LLMRequest.builder()
                .model(provider.getDefaultModel())
                .messages(messages)
                .tools(tools.isEmpty() ? null : tools)
                .temperature(0.7)
                .maxTokens(cortexProperties.getLlm().getOpenai().getMaxTokens())
                .build();
        
        return provider.complete(request);
    }

    private List<LLMRequest.Message> buildMessages(AgentTask task, AgentContext context) {
        List<LLMRequest.Message> messages = new ArrayList<>();
        
        // System prompt
        String systemPrompt = REACT_SYSTEM_PROMPT;
        // Could customize based on agent configuration
        
        messages.add(LLMRequest.systemMessage(systemPrompt));
        
        // Add conversation history if available
        if (context.getMessages() != null) {
            for (AgentContext.Message msg : context.getMessages()) {
                messages.add(LLMRequest.Message.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .name(msg.getName())
                        .toolCallId(msg.getToolCallId())
                        .build());
            }
        }
        
        // Add current task input as user message
        messages.add(LLMRequest.userMessage(task.getInput()));
        
        return messages;
    }

    private Mono<Void> handleToolCalls(
            AgentTask task,
            AgentContext context,
            LLMResponse response,
            Sinks.Many<AgentThought> thoughtSink,
            int iteration,
            List<AgentThought> allThoughts) {
        
        List<LLMRequest.ToolCall> toolCalls = response.getToolCalls();
        
        return Flux.fromIterable(toolCalls)
                .flatMap(toolCall -> {
                    String toolName = toolCall.getFunction().getName();
                    String argsJson = toolCall.getFunction().getArguments();
                    
                    // Emit tool call thought
                    Map<String, Object> params = parseToolArguments(argsJson);
                    AgentThought callThought = AgentThought.toolCall(
                            task.getId(), task.getAgentId(), toolName, params, iteration);
                    thoughtSink.tryEmitNext(callThought);
                    allThoughts.add(callThought);
                    
                    // Execute tool
                    return toolRegistry.execute(toolName, params, context)
                            .map(result -> {
                                // Emit observation thought
                                AgentThought obsThought = AgentThought.observation(
                                        task.getId(), task.getAgentId(), result.getOutputForLLM(), iteration);
                                thoughtSink.tryEmitNext(obsThought);
                                allThoughts.add(obsThought);
                                
                                // Add to context messages for next iteration
                                context.addMessage("assistant", null);  // Tool call message
                                context.addMessage("tool", result.getOutputForLLM());
                                
                                return result;
                            });
                })
                .then();
    }

    private Map<String, Object> parseToolArguments(String argsJson) {
        if (argsJson == null || argsJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
            return args;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool arguments: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private AgentResult buildResult(AgentTask task, AgentContext context, List<AgentThought> thoughts) {
        String output = "";
        AgentResult.ResultStatus status = AgentResult.ResultStatus.SUCCESS;
        
        // Find final answer or error
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            AgentThought thought = thoughts.get(i);
            if (thought.getType() == AgentThought.ThoughtType.FINAL_ANSWER) {
                output = thought.getContent();
                break;
            } else if (thought.getType() == AgentThought.ThoughtType.ERROR) {
                output = thought.getContent();
                status = AgentResult.ResultStatus.FAILURE;
                break;
            } else if (thought.getType() == AgentThought.ThoughtType.STOP) {
                output = thought.getContent();
                status = AgentResult.ResultStatus.MAX_ITERATIONS;
                break;
            }
        }
        
        // Build tool invocation list
        List<AgentResult.ToolInvocation> toolInvocations = thoughts.stream()
                .filter(t -> t.getType() == AgentThought.ThoughtType.TOOL_CALL)
                .map(t -> AgentResult.ToolInvocation.builder()
                        .id(t.getId())
                        .toolId(t.getToolId())
                        .toolName(t.getToolId())
                        .parameters(t.getToolParameters())
                        .timestamp(t.getTimestamp())
                        .build())
                .toList();
        
        AgentResult.TokenUsage tokenUsage = null;
        if (context.getTokenBudget() != null) {
            tokenUsage = AgentResult.TokenUsage.builder()
                    .inputTokens(context.getTokenBudget().getUsedInputTokens())
                    .outputTokens(context.getTokenBudget().getUsedOutputTokens())
                    .totalTokens(context.getTokenBudget().getUsedTokens())
                    .build();
        }
        
        return AgentResult.builder()
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .conversationId(task.getConversationId())
                .correlationId(task.getCorrelationId())
                .output(output)
                .thoughts(thoughts)
                .toolCalls(toolInvocations)
                .tokenUsage(tokenUsage)
                .status(status)
                .iterations(thoughts.isEmpty() ? 0 : thoughts.get(thoughts.size() - 1).getIteration())
                .startedAt(task.getStartedAt())
                .completedAt(Instant.now())
                .duration(Duration.between(
                        task.getStartedAt() != null ? task.getStartedAt() : Instant.now(), 
                        Instant.now()))
                .build();
    }

    @Override
    public Mono<Boolean> cancel(String taskId) {
        runningTasks.put(taskId, false);
        return Mono.just(true);
    }

    @Override
    public Mono<Integer> getCurrentIteration(String taskId) {
        return Mono.just(runningTasks.containsKey(taskId) ? 0 : -1);
    }
}
