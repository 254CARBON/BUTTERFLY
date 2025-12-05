package com.z254.butterfly.cortex.llm.guardrails;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.llm.LLMRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token budget enforcer for LLM calls.
 * Enforces limits at task, conversation, and daily levels.
 */
@Component
@Slf4j
public class TokenBudgetEnforcer {

    private final CortexProperties.SafetyProperties.TokenBudgetProperties config;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final Counter budgetExceededCounter;
    private final Counter budgetWarningCounter;
    
    // In-memory tracking for fast checks (Redis for persistence)
    private final ConcurrentHashMap<String, AtomicInteger> taskBudgets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> conversationBudgets = new ConcurrentHashMap<>();

    private static final String DAILY_BUDGET_KEY_PREFIX = "cortex:budget:daily:";
    private static final String CONVERSATION_BUDGET_KEY_PREFIX = "cortex:budget:conversation:";

    public TokenBudgetEnforcer(
            CortexProperties cortexProperties,
            ReactiveRedisTemplate<String, String> redisTemplate,
            MeterRegistry meterRegistry) {
        this.config = cortexProperties.getSafety().getTokenBudget();
        this.redisTemplate = redisTemplate;
        
        this.budgetExceededCounter = Counter.builder("cortex.safety.budget_exceeded")
                .register(meterRegistry);
        this.budgetWarningCounter = Counter.builder("cortex.safety.budget_warning")
                .register(meterRegistry);
    }

    /**
     * Check if a request can be made within budget.
     *
     * @param context the agent context
     * @param estimatedTokens estimated tokens for the request
     * @return budget check result
     */
    public Mono<BudgetCheckResult> checkBudget(AgentContext context, int estimatedTokens) {
        return Mono.fromCallable(() -> {
            BudgetCheckResult result = new BudgetCheckResult();
            result.setAllowed(true);
            
            // Check task budget
            if (context.getTokenBudget() != null) {
                AgentContext.TokenBudget taskBudget = context.getTokenBudget();
                int remaining = taskBudget.getRemainingTokens();
                
                if (estimatedTokens > remaining) {
                    result.setAllowed(false);
                    result.setReason("Task token budget exceeded");
                    result.setBudgetType(BudgetType.TASK);
                    budgetExceededCounter.increment();
                    return result;
                }
                
                // Warning threshold
                double usage = taskBudget.getUsagePercentage();
                if (usage >= config.getWarningThreshold()) {
                    result.setWarning(true);
                    result.setWarningMessage(String.format(
                            "Task budget at %.0f%% (used %d of %d tokens)",
                            usage * 100, taskBudget.getUsedTokens(), taskBudget.getMaxTokens()));
                    budgetWarningCounter.increment();
                }
            }
            
            return result;
        }).flatMap(result -> {
            if (!result.isAllowed()) {
                return Mono.just(result);
            }
            
            // Check daily budget from Redis
            return checkDailyBudget(context.getUserId(), estimatedTokens)
                    .map(dailyResult -> {
                        if (!dailyResult.isAllowed()) {
                            return dailyResult;
                        }
                        return result;
                    });
        });
    }

    /**
     * Record token usage.
     *
     * @param context the agent context
     * @param inputTokens input tokens used
     * @param outputTokens output tokens used
     * @return completion signal
     */
    public Mono<Void> recordUsage(AgentContext context, int inputTokens, int outputTokens) {
        int totalTokens = inputTokens + outputTokens;
        
        // Update context budget
        if (context.getTokenBudget() != null) {
            context.getTokenBudget().addUsage(inputTokens, outputTokens);
        }
        
        // Update task tracking
        String taskId = context.getTaskId();
        if (taskId != null) {
            taskBudgets.computeIfAbsent(taskId, k -> new AtomicInteger(0))
                    .addAndGet(totalTokens);
        }
        
        // Update conversation tracking
        String conversationId = context.getConversationId();
        if (conversationId != null) {
            conversationBudgets.computeIfAbsent(conversationId, k -> new AtomicInteger(0))
                    .addAndGet(totalTokens);
        }
        
        // Update daily budget in Redis
        return recordDailyUsage(context.getUserId(), totalTokens);
    }

    /**
     * Get remaining budget for a task.
     *
     * @param taskId the task ID
     * @return remaining tokens
     */
    public int getRemainingTaskBudget(String taskId) {
        AtomicInteger used = taskBudgets.get(taskId);
        int usedTokens = used != null ? used.get() : 0;
        return Math.max(0, config.getMaxPerTask() - usedTokens);
    }

    /**
     * Get remaining budget for a conversation.
     *
     * @param conversationId the conversation ID
     * @return remaining tokens
     */
    public Mono<Integer> getRemainingConversationBudget(String conversationId) {
        return redisTemplate.opsForValue()
                .get(CONVERSATION_BUDGET_KEY_PREFIX + conversationId)
                .map(value -> {
                    int used = Integer.parseInt(value);
                    return Math.max(0, config.getMaxPerConversation() - used);
                })
                .defaultIfEmpty(config.getMaxPerConversation());
    }

    /**
     * Create a token budget for a task.
     *
     * @param maxTokens maximum tokens (or use default)
     * @return token budget
     */
    public AgentContext.TokenBudget createTaskBudget(Integer maxTokens) {
        int max = maxTokens != null ? Math.min(maxTokens, config.getMaxPerTask()) : config.getMaxPerTask();
        return AgentContext.TokenBudget.builder()
                .maxTokens(max)
                .usedTokens(0)
                .maxInputTokens(max)
                .maxOutputTokens(max)
                .usedInputTokens(0)
                .usedOutputTokens(0)
                .build();
    }

    /**
     * Clear budget tracking for a completed task.
     *
     * @param taskId the task ID
     */
    public void clearTaskBudget(String taskId) {
        taskBudgets.remove(taskId);
    }

    /**
     * Estimate tokens for a request (rough approximation).
     *
     * @param request the LLM request
     * @return estimated token count
     */
    public int estimateRequestTokens(LLMRequest request) {
        int total = 0;
        
        // Estimate message tokens
        if (request.getMessages() != null) {
            for (LLMRequest.Message msg : request.getMessages()) {
                if (msg.getContent() != null) {
                    total += estimateTokens(msg.getContent());
                }
            }
        }
        
        // Estimate tool definition overhead
        if (request.getTools() != null) {
            total += request.getTools().size() * 100;
        }
        
        // Add buffer for response
        return total + (request.getMaxTokens() != null ? request.getMaxTokens() : 1000);
    }

    /**
     * Estimate tokens for text.
     *
     * @param text the text
     * @return estimated token count
     */
    public int estimateTokens(String text) {
        // Rough approximation: ~4 characters per token
        return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
    }

    private Mono<BudgetCheckResult> checkDailyBudget(String userId, int estimatedTokens) {
        if (userId == null) {
            return Mono.just(BudgetCheckResult.allowed());
        }
        
        String key = getDailyBudgetKey(userId);
        
        return redisTemplate.opsForValue().get(key)
                .map(value -> {
                    int used = Integer.parseInt(value);
                    if (used + estimatedTokens > config.getMaxPerDay()) {
                        BudgetCheckResult result = new BudgetCheckResult();
                        result.setAllowed(false);
                        result.setReason("Daily token budget exceeded");
                        result.setBudgetType(BudgetType.DAILY);
                        budgetExceededCounter.increment();
                        return result;
                    }
                    return BudgetCheckResult.allowed();
                })
                .defaultIfEmpty(BudgetCheckResult.allowed());
    }

    private Mono<Void> recordDailyUsage(String userId, int tokens) {
        if (userId == null) {
            return Mono.empty();
        }
        
        String key = getDailyBudgetKey(userId);
        
        return redisTemplate.opsForValue()
                .increment(key, tokens)
                .flatMap(newValue -> {
                    // Set expiry to end of day if this is the first increment
                    if (newValue <= tokens) {
                        return redisTemplate.expire(key, Duration.ofHours(24)).then();
                    }
                    return Mono.empty();
                });
    }

    private String getDailyBudgetKey(String userId) {
        return DAILY_BUDGET_KEY_PREFIX + LocalDate.now() + ":" + userId;
    }

    /**
     * Budget check result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetCheckResult {
        private boolean allowed;
        private String reason;
        private BudgetType budgetType;
        private boolean warning;
        private String warningMessage;
        
        public static BudgetCheckResult allowed() {
            BudgetCheckResult result = new BudgetCheckResult();
            result.setAllowed(true);
            return result;
        }
    }

    /**
     * Types of budgets.
     */
    public enum BudgetType {
        TASK,
        CONVERSATION,
        DAILY,
        USER
    }
}
