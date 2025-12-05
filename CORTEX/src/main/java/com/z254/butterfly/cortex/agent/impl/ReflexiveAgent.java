package com.z254.butterfly.cortex.agent.impl;

import com.z254.butterfly.cortex.agent.AgentExecutor;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.config.CortexProperties.AgentType;
import com.z254.butterfly.cortex.domain.model.*;
import com.z254.butterfly.cortex.llm.LLMProvider;
import com.z254.butterfly.cortex.llm.LLMProviderRegistry;
import com.z254.butterfly.cortex.llm.LLMRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reflexive Agent implementation.
 * Fast-path simple agent for direct prompt-response without tool use.
 * 
 * <p>Use cases:
 * <ul>
 *   <li>Simple Q&A that doesn't require tools</li>
 *   <li>Text generation, summarization, translation</li>
 *   <li>Classification tasks</li>
 *   <li>Quick lookups from conversation context</li>
 * </ul>
 * 
 * <p>Features:
 * <ul>
 *   <li>No tool calling - pure LLM response</li>
 *   <li>Lower latency than ReAct or PlanAndExecute</li>
 *   <li>Can escalate to ReAct if complexity detected</li>
 * </ul>
 */
@Component
@Slf4j
public class ReflexiveAgent implements AgentExecutor {

    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are a helpful AI assistant. Provide clear, accurate, and concise responses.
        If a question requires external information, tools, or complex reasoning that you cannot provide,
        indicate that the task may require a different approach.
        """;

    private final LLMProviderRegistry llmProviderRegistry;
    private final CortexProperties cortexProperties;

    public ReflexiveAgent(
            LLMProviderRegistry llmProviderRegistry,
            CortexProperties cortexProperties) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.cortexProperties = cortexProperties;
    }

    @Override
    public AgentType getAgentType() {
        return AgentType.REFLEXIVE;
    }

    @Override
    public Flux<AgentThought> execute(AgentTask task, AgentContext context) {
        return executeInternal(task, context)
                .flatMapMany(result -> Flux.fromIterable(result.getThoughts()));
    }

    @Override
    public Mono<AgentResult> executeSync(AgentTask task, AgentContext context) {
        return executeInternal(task, context);
    }

    private Mono<AgentResult> executeInternal(AgentTask task, AgentContext context) {
        Instant startTime = Instant.now();
        String taskId = task.getId();
        
        log.debug("Reflexive agent executing task: {}", taskId);
        
        LLMProvider provider = llmProviderRegistry.getDefaultProvider();
        
        // Build messages
        List<LLMRequest.Message> messages = buildMessages(task, context);
        
        int maxTokens = cortexProperties.getAgent().getReflexive().getMaxTokens();
        
        LLMRequest request = LLMRequest.builder()
                .model(provider.getDefaultModel())
                .messages(messages)
                .temperature(0.7)
                .maxTokens(maxTokens)
                .build();
        
        // Emit analysis thought
        AgentThought analysisThought = AgentThought.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .agentId(task.getAgentId())
                .type(AgentThought.ThoughtType.ANALYSIS)
                .content("Analyzing request...")
                .iteration(0)
                .timestamp(Instant.now())
                .build();
        
        return provider.complete(request)
                .map(response -> {
                    // Update token usage
                    if (response.getUsage() != null && context.getTokenBudget() != null) {
                        context.addTokenUsage(
                                response.getUsage().getPromptTokens(),
                                response.getUsage().getCompletionTokens()
                        );
                    }
                    
                    String content = response.getContent();
                    
                    // Check if we should escalate (response indicates complexity)
                    boolean shouldEscalate = shouldEscalate(content);
                    
                    // Create final answer thought
                    AgentThought finalThought = AgentThought.builder()
                            .id(UUID.randomUUID().toString())
                            .taskId(taskId)
                            .agentId(task.getAgentId())
                            .type(AgentThought.ThoughtType.FINAL_ANSWER)
                            .content(content)
                            .iteration(0)
                            .timestamp(Instant.now())
                            .metadata(shouldEscalate ? java.util.Map.of("shouldEscalate", true) : null)
                            .build();
                    
                    // Build token usage
                    AgentResult.TokenUsage tokenUsage = null;
                    if (response.getUsage() != null) {
                        tokenUsage = AgentResult.TokenUsage.builder()
                                .inputTokens(response.getUsage().getPromptTokens())
                                .outputTokens(response.getUsage().getCompletionTokens())
                                .totalTokens(response.getUsage().getTotalTokens())
                                .model(response.getModel())
                                .providerId(response.getProviderId())
                                .build();
                    }
                    
                    Instant endTime = Instant.now();
                    
                    return AgentResult.builder()
                            .taskId(taskId)
                            .agentId(task.getAgentId())
                            .conversationId(task.getConversationId())
                            .correlationId(task.getCorrelationId())
                            .output(content)
                            .thoughts(List.of(analysisThought, finalThought))
                            .tokenUsage(tokenUsage)
                            .status(shouldEscalate 
                                    ? AgentResult.ResultStatus.PARTIAL_SUCCESS 
                                    : AgentResult.ResultStatus.SUCCESS)
                            .iterations(1)
                            .startedAt(startTime)
                            .completedAt(endTime)
                            .duration(Duration.between(startTime, endTime))
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Reflexive agent error for task {}: {}", taskId, e.getMessage());
                    
                    AgentThought errorThought = AgentThought.error(
                            taskId, task.getAgentId(), e.getMessage(), 0);
                    
                    return Mono.just(AgentResult.builder()
                            .taskId(taskId)
                            .agentId(task.getAgentId())
                            .conversationId(task.getConversationId())
                            .correlationId(task.getCorrelationId())
                            .output("Error: " + e.getMessage())
                            .thoughts(List.of(analysisThought, errorThought))
                            .status(AgentResult.ResultStatus.FAILURE)
                            .errorCode("REFLEXIVE_ERROR")
                            .errorMessage(e.getMessage())
                            .startedAt(startTime)
                            .completedAt(Instant.now())
                            .duration(Duration.between(startTime, Instant.now()))
                            .build());
                });
    }

    private List<LLMRequest.Message> buildMessages(AgentTask task, AgentContext context) {
        java.util.List<LLMRequest.Message> messages = new java.util.ArrayList<>();
        
        // System prompt
        messages.add(LLMRequest.systemMessage(DEFAULT_SYSTEM_PROMPT));
        
        // Add conversation history if available
        if (context.getMessages() != null) {
            for (AgentContext.Message msg : context.getMessages()) {
                messages.add(LLMRequest.Message.builder()
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .build());
            }
        }
        
        // Add current input
        messages.add(LLMRequest.userMessage(task.getInput()));
        
        return messages;
    }

    /**
     * Check if the response indicates we should escalate to a more capable agent.
     * Looks for phrases that suggest tools or more complex reasoning is needed.
     */
    private boolean shouldEscalate(String content) {
        if (content == null) return false;
        
        String lower = content.toLowerCase();
        
        // Keywords that suggest escalation might be needed
        String[] escalationKeywords = {
                "i cannot access",
                "i don't have access",
                "i cannot retrieve",
                "i cannot look up",
                "need to search",
                "need to query",
                "would need to check",
                "real-time data",
                "current information",
                "requires external",
                "complex analysis"
        };
        
        for (String keyword : escalationKeywords) {
            if (lower.contains(keyword)) {
                return cortexProperties.getAgent().getReflexive().isEscalateOnComplexity();
            }
        }
        
        return false;
    }

    @Override
    public boolean canHandle(AgentTask task) {
        // Reflexive agent can handle most simple tasks
        // Could add complexity detection here
        return true;
    }

    @Override
    public Mono<Boolean> cancel(String taskId) {
        // Reflexive tasks are typically fast and don't need cancellation
        return Mono.just(false);
    }
}
