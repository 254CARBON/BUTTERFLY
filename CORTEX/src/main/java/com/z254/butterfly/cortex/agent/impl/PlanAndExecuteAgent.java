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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
 * Plan and Execute Agent implementation.
 * Creates a plan first, optionally gets approval, then executes step by step.
 * 
 * <p>The Plan-and-Execute pattern:
 * <ol>
 *   <li>Analyze the task and create a multi-step plan</li>
 *   <li>Optionally submit plan for governance approval (via PLATO)</li>
 *   <li>Execute each step sequentially</li>
 *   <li>Re-plan if a step fails (optional)</li>
 *   <li>Synthesize final answer from all step results</li>
 * </ol>
 */
@Component
@Slf4j
public class PlanAndExecuteAgent implements AgentExecutor {

    private static final String PLANNING_SYSTEM_PROMPT = """
        You are a planning assistant. Given a task, create a detailed step-by-step plan to accomplish it.
        
        Output your plan as a JSON array of steps. Each step should have:
        - "step_number": sequential number starting from 1
        - "description": what this step accomplishes
        - "tool": the tool to use (or "none" if no tool needed)
        - "expected_output": what output is expected from this step
        
        Example:
        [
          {"step_number": 1, "description": "Search for user information", "tool": "user_query", "expected_output": "User details"},
          {"step_number": 2, "description": "Analyze the data", "tool": "none", "expected_output": "Analysis summary"},
          {"step_number": 3, "description": "Formulate response", "tool": "none", "expected_output": "Final answer"}
        ]
        
        Create a practical plan with no more than %d steps.
        """;

    private static final String EXECUTION_SYSTEM_PROMPT = """
        You are executing step %d of a plan: %s
        
        Previous steps results:
        %s
        
        Execute this step and provide the result. If a tool is needed, use it.
        If this is the final step, provide the complete answer to the original task.
        """;

    private final LLMProviderRegistry llmProviderRegistry;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final CortexProperties cortexProperties;
    
    private final Map<String, Boolean> runningTasks = new ConcurrentHashMap<>();

    public PlanAndExecuteAgent(
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
        return AgentType.PLAN_AND_EXECUTE;
    }

    @Override
    public Flux<AgentThought> execute(AgentTask task, AgentContext context) {
        String taskId = task.getId();
        runningTasks.put(taskId, true);
        
        Sinks.Many<AgentThought> thoughtSink = Sinks.many().multicast().onBackpressureBuffer();
        
        createPlan(task, context, thoughtSink)
                .flatMap(plan -> executePlan(task, context, plan, thoughtSink))
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

    private Mono<ExecutionPlan> createPlan(
            AgentTask task,
            AgentContext context,
            Sinks.Many<AgentThought> thoughtSink) {
        
        LLMProvider provider = llmProviderRegistry.getDefaultProvider();
        int maxSteps = cortexProperties.getAgent().getPlanAndExecute().getMaxPlanSteps();
        
        String systemPrompt = String.format(PLANNING_SYSTEM_PROMPT, maxSteps);
        
        LLMRequest request = LLMRequest.builder()
                .model(provider.getDefaultModel())
                .messages(List.of(
                        LLMRequest.systemMessage(systemPrompt),
                        LLMRequest.userMessage(task.getInput())
                ))
                .temperature(0.3)  // Lower temperature for more consistent plans
                .build();
        
        // Emit planning thought
        AgentThought planningThought = AgentThought.builder()
                .id(UUID.randomUUID().toString())
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .type(AgentThought.ThoughtType.PLANNING)
                .content("Creating execution plan...")
                .iteration(0)
                .timestamp(Instant.now())
                .build();
        thoughtSink.tryEmitNext(planningThought);
        
        return provider.complete(request)
                .map(response -> {
                    // Update token usage
                    if (response.getUsage() != null) {
                        context.addTokenUsage(
                                response.getUsage().getPromptTokens(),
                                response.getUsage().getCompletionTokens()
                        );
                    }
                    
                    // Parse plan
                    ExecutionPlan plan = parsePlan(response.getContent());
                    
                    // Emit plan thought
                    AgentThought planThought = AgentThought.builder()
                            .id(UUID.randomUUID().toString())
                            .taskId(task.getId())
                            .agentId(task.getAgentId())
                            .type(AgentThought.ThoughtType.PLANNING)
                            .content("Plan created with " + plan.getSteps().size() + " steps")
                            .iteration(0)
                            .metadata(Map.of("plan", plan))
                            .timestamp(Instant.now())
                            .build();
                    thoughtSink.tryEmitNext(planThought);
                    
                    return plan;
                });
    }

    private ExecutionPlan parsePlan(String content) {
        try {
            // Try to extract JSON from response
            String json = extractJson(content);
            List<Map<String, Object>> steps = objectMapper.readValue(json, List.class);
            
            List<PlanStep> planSteps = new ArrayList<>();
            for (Map<String, Object> stepMap : steps) {
                planSteps.add(PlanStep.builder()
                        .stepNumber(((Number) stepMap.get("step_number")).intValue())
                        .description((String) stepMap.get("description"))
                        .tool((String) stepMap.get("tool"))
                        .expectedOutput((String) stepMap.get("expected_output"))
                        .status(StepStatus.PENDING)
                        .build());
            }
            
            return ExecutionPlan.builder()
                    .steps(planSteps)
                    .currentStep(0)
                    .status(PlanStatus.PENDING)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse plan JSON, using simple plan: {}", e.getMessage());
            // Fallback to single-step plan
            return ExecutionPlan.builder()
                    .steps(List.of(PlanStep.builder()
                            .stepNumber(1)
                            .description("Execute task directly")
                            .tool("none")
                            .expectedOutput("Final answer")
                            .status(StepStatus.PENDING)
                            .build()))
                    .currentStep(0)
                    .status(PlanStatus.PENDING)
                    .build();
        }
    }

    private String extractJson(String content) {
        // Find JSON array in response
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private Mono<Void> executePlan(
            AgentTask task,
            AgentContext context,
            ExecutionPlan plan,
            Sinks.Many<AgentThought> thoughtSink) {
        
        return executeSteps(task, context, plan, thoughtSink, 0, new ArrayList<>())
                .then(Mono.defer(() -> {
                    // Synthesize final answer
                    return synthesizeFinalAnswer(task, context, plan, thoughtSink);
                }));
    }

    private Mono<Void> executeSteps(
            AgentTask task,
            AgentContext context,
            ExecutionPlan plan,
            Sinks.Many<AgentThought> thoughtSink,
            int stepIndex,
            List<String> previousResults) {
        
        if (!runningTasks.getOrDefault(task.getId(), false)) {
            thoughtSink.tryEmitComplete();
            return Mono.empty();
        }
        
        if (stepIndex >= plan.getSteps().size()) {
            return Mono.empty();
        }
        
        PlanStep step = plan.getSteps().get(stepIndex);
        
        return executeStep(task, context, step, stepIndex + 1, previousResults, thoughtSink)
                .flatMap(result -> {
                    previousResults.add(result);
                    return executeSteps(task, context, plan, thoughtSink, stepIndex + 1, previousResults);
                });
    }

    private Mono<String> executeStep(
            AgentTask task,
            AgentContext context,
            PlanStep step,
            int stepNumber,
            List<String> previousResults,
            Sinks.Many<AgentThought> thoughtSink) {
        
        LLMProvider provider = llmProviderRegistry.getDefaultProvider();
        
        String previousResultsStr = previousResults.isEmpty() 
                ? "None" 
                : String.join("\n", previousResults);
        
        String systemPrompt = String.format(EXECUTION_SYSTEM_PROMPT, 
                stepNumber, step.getDescription(), previousResultsStr);
        
        // Emit step execution thought
        AgentThought stepThought = AgentThought.builder()
                .id(UUID.randomUUID().toString())
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .type(AgentThought.ThoughtType.REASONING)
                .content("Executing step " + stepNumber + ": " + step.getDescription())
                .iteration(stepNumber)
                .timestamp(Instant.now())
                .build();
        thoughtSink.tryEmitNext(stepThought);
        
        List<LLMRequest.Tool> tools = new ArrayList<>();
        if (!"none".equals(step.getTool()) && context.getAvailableTools() != null) {
            tools = toolRegistry.toLLMTools(context.getAvailableTools());
        }
        
        LLMRequest request = LLMRequest.builder()
                .model(provider.getDefaultModel())
                .messages(List.of(
                        LLMRequest.systemMessage(systemPrompt),
                        LLMRequest.userMessage(task.getInput())
                ))
                .tools(tools.isEmpty() ? null : tools)
                .temperature(0.5)
                .build();
        
        return provider.complete(request)
                .flatMap(response -> {
                    // Update token usage
                    if (response.getUsage() != null) {
                        context.addTokenUsage(
                                response.getUsage().getPromptTokens(),
                                response.getUsage().getCompletionTokens()
                        );
                    }
                    
                    // Handle tool calls if any
                    if (response.hasToolCalls()) {
                        return handleToolCalls(task, context, response, thoughtSink, stepNumber)
                                .map(toolResult -> "Step " + stepNumber + " (tool result): " + toolResult);
                    }
                    
                    // Emit step completion thought
                    AgentThought completeThought = AgentThought.builder()
                            .id(UUID.randomUUID().toString())
                            .taskId(task.getId())
                            .agentId(task.getAgentId())
                            .type(AgentThought.ThoughtType.INTERMEDIATE)
                            .content(response.getContent())
                            .iteration(stepNumber)
                            .timestamp(Instant.now())
                            .build();
                    thoughtSink.tryEmitNext(completeThought);
                    
                    return Mono.just("Step " + stepNumber + ": " + response.getContent());
                });
    }

    private Mono<String> handleToolCalls(
            AgentTask task,
            AgentContext context,
            LLMResponse response,
            Sinks.Many<AgentThought> thoughtSink,
            int stepNumber) {
        
        return Flux.fromIterable(response.getToolCalls())
                .flatMap(toolCall -> {
                    String toolName = toolCall.getFunction().getName();
                    Map<String, Object> params = parseToolArguments(toolCall.getFunction().getArguments());
                    
                    AgentThought callThought = AgentThought.toolCall(
                            task.getId(), task.getAgentId(), toolName, params, stepNumber);
                    thoughtSink.tryEmitNext(callThought);
                    
                    return toolRegistry.execute(toolName, params, context);
                })
                .map(ToolResult::getOutputForLLM)
                .collectList()
                .map(results -> String.join("\n", results));
    }

    private Mono<Void> synthesizeFinalAnswer(
            AgentTask task,
            AgentContext context,
            ExecutionPlan plan,
            Sinks.Many<AgentThought> thoughtSink) {
        
        // The last step's result should contain the final answer
        // For now, emit completion
        AgentThought finalThought = AgentThought.builder()
                .id(UUID.randomUUID().toString())
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .type(AgentThought.ThoughtType.FINAL_ANSWER)
                .content("Plan executed successfully")
                .iteration(plan.getSteps().size())
                .timestamp(Instant.now())
                .build();
        thoughtSink.tryEmitNext(finalThought);
        thoughtSink.tryEmitComplete();
        
        return Mono.empty();
    }

    private Map<String, Object> parseToolArguments(String argsJson) {
        if (argsJson == null || argsJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(argsJson, Map.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private AgentResult buildResult(AgentTask task, AgentContext context, List<AgentThought> thoughts) {
        String output = thoughts.stream()
                .filter(t -> t.getType() == AgentThought.ThoughtType.FINAL_ANSWER)
                .map(AgentThought::getContent)
                .findFirst()
                .orElse("Plan executed");
        
        return AgentResult.builder()
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .conversationId(task.getConversationId())
                .correlationId(task.getCorrelationId())
                .output(output)
                .thoughts(thoughts)
                .status(AgentResult.ResultStatus.SUCCESS)
                .completedAt(Instant.now())
                .build();
    }

    @Override
    public Mono<Boolean> cancel(String taskId) {
        runningTasks.put(taskId, false);
        return Mono.just(true);
    }

    // Inner classes for plan structure

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ExecutionPlan {
        private List<PlanStep> steps;
        private int currentStep;
        private PlanStatus status;
        private String approvalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class PlanStep {
        private int stepNumber;
        private String description;
        private String tool;
        private String expectedOutput;
        private StepStatus status;
        private String result;
        private String errorMessage;
    }

    enum PlanStatus {
        PENDING, APPROVED, EXECUTING, COMPLETED, FAILED, REJECTED
    }

    enum StepStatus {
        PENDING, EXECUTING, COMPLETED, FAILED, SKIPPED
    }
}
