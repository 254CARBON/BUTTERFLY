package com.z254.butterfly.cortex.llm.guardrails;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.llm.LLMRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenBudgetEnforcer}.
 */
@ExtendWith(MockitoExtension.class)
class TokenBudgetEnforcerTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private CortexProperties cortexProperties;
    private MeterRegistry meterRegistry;
    private TokenBudgetEnforcer enforcer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cortexProperties = new CortexProperties();
        cortexProperties.getSafety().getTokenBudget().setMaxPerTask(4000);
        cortexProperties.getSafety().getTokenBudget().setMaxPerConversation(16000);
        cortexProperties.getSafety().getTokenBudget().setMaxPerDay(100000);
        cortexProperties.getSafety().getTokenBudget().setWarningThreshold(0.8);
        
        enforcer = new TokenBudgetEnforcer(cortexProperties, redisTemplate, meterRegistry);
    }

    @Nested
    @DisplayName("Budget Creation")
    class BudgetCreationTests {

        @Test
        @DisplayName("should create task budget with default max tokens")
        void createTaskBudgetWithDefaultMaxTokens() {
            AgentContext.TokenBudget budget = enforcer.createTaskBudget(null);

            assertThat(budget.getMaxTokens()).isEqualTo(4000);
            assertThat(budget.getUsedTokens()).isEqualTo(0);
            assertThat(budget.getUsedInputTokens()).isEqualTo(0);
            assertThat(budget.getUsedOutputTokens()).isEqualTo(0);
        }

        @Test
        @DisplayName("should create task budget with custom max tokens")
        void createTaskBudgetWithCustomMaxTokens() {
            AgentContext.TokenBudget budget = enforcer.createTaskBudget(2000);

            assertThat(budget.getMaxTokens()).isEqualTo(2000);
        }

        @Test
        @DisplayName("should cap max tokens at configured limit")
        void capMaxTokensAtConfiguredLimit() {
            AgentContext.TokenBudget budget = enforcer.createTaskBudget(10000);

            assertThat(budget.getMaxTokens()).isEqualTo(4000);
        }
    }

    @Nested
    @DisplayName("Budget Checking")
    class BudgetCheckingTests {

        @Test
        @DisplayName("should allow request within task budget")
        void allowRequestWithinTaskBudget() {
            AgentContext context = createContext(1000, 4000);
            // These may not be called since task budget check might short-circuit
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(valueOperations.get(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(enforcer.checkBudget(context, 500))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.isWarning()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny request exceeding task budget")
        void denyRequestExceedingTaskBudget() {
            AgentContext context = createContext(3500, 4000);
            
            StepVerifier.create(enforcer.checkBudget(context, 1000))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getReason()).contains("Task token budget exceeded");
                        assertThat(result.getBudgetType())
                                .isEqualTo(TokenBudgetEnforcer.BudgetType.TASK);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should warn when approaching budget limit")
        void warnWhenApproachingBudgetLimit() {
            AgentContext context = createContext(3300, 4000); // 82.5% used
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            lenient().when(valueOperations.get(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(enforcer.checkBudget(context, 100))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.isWarning()).isTrue();
                        assertThat(result.getWarningMessage()).contains("budget at");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny when daily budget exceeded")
        void denyWhenDailyBudgetExceeded() {
            AgentContext context = createContext(1000, 4000);
            context.setUserId("user-123");
            
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("100000")); // At daily limit

            StepVerifier.create(enforcer.checkBudget(context, 1000))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getReason()).contains("Daily token budget exceeded");
                        assertThat(result.getBudgetType())
                                .isEqualTo(TokenBudgetEnforcer.BudgetType.DAILY);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should allow when user ID is null (skip daily check)")
        void allowWhenUserIdIsNull() {
            AgentContext context = createContext(1000, 4000);
            context.setUserId(null);

            StepVerifier.create(enforcer.checkBudget(context, 500))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Usage Recording")
    class UsageRecordingTests {

        @Test
        @DisplayName("should update context budget on usage")
        void updateContextBudgetOnUsage() {
            AgentContext context = createContext(0, 4000);
            context.setUserId(null); // Skip Redis operations
            context.setTaskId("task-123");

            StepVerifier.create(enforcer.recordUsage(context, 100, 200))
                    .verifyComplete();

            assertThat(context.getTokenBudget().getUsedInputTokens()).isEqualTo(100);
            assertThat(context.getTokenBudget().getUsedOutputTokens()).isEqualTo(200);
            assertThat(context.getTokenBudget().getUsedTokens()).isEqualTo(300);
        }

        @Test
        @DisplayName("should track task budget separately")
        void trackTaskBudgetSeparately() {
            AgentContext context = createContext(0, 4000);
            context.setUserId(null);
            context.setTaskId("task-456");

            StepVerifier.create(enforcer.recordUsage(context, 100, 50))
                    .verifyComplete();

            int remaining = enforcer.getRemainingTaskBudget("task-456");
            assertThat(remaining).isEqualTo(4000 - 150);
        }

        @Test
        @DisplayName("should clear task budget")
        void clearTaskBudget() {
            AgentContext context = createContext(0, 4000);
            context.setUserId(null);
            context.setTaskId("task-789");

            StepVerifier.create(enforcer.recordUsage(context, 500, 500))
                    .verifyComplete();

            assertThat(enforcer.getRemainingTaskBudget("task-789")).isLessThan(4000);

            enforcer.clearTaskBudget("task-789");

            assertThat(enforcer.getRemainingTaskBudget("task-789")).isEqualTo(4000);
        }
    }

    @Nested
    @DisplayName("Token Estimation")
    class TokenEstimationTests {

        @Test
        @DisplayName("should estimate tokens for text")
        void estimateTokensForText() {
            String text = "This is a test message with about forty characters.";
            int estimated = enforcer.estimateTokens(text);

            // ~4 characters per token
            assertThat(estimated).isBetween(10, 15);
        }

        @Test
        @DisplayName("should return 0 for null text")
        void returnZeroForNullText() {
            int estimated = enforcer.estimateTokens(null);
            assertThat(estimated).isEqualTo(0);
        }

        @Test
        @DisplayName("should estimate request tokens including messages and tools")
        void estimateRequestTokensIncludingMessagesAndTools() {
            LLMRequest request = LLMRequest.builder()
                    .messages(List.of(
                            LLMRequest.Message.builder()
                                    .role("user")
                                    .content("What is 2+2?")
                                    .build(),
                            LLMRequest.Message.builder()
                                    .role("assistant")
                                    .content("The answer is 4.")
                                    .build()
                    ))
                    .tools(List.of(
                            LLMRequest.Tool.builder()
                                    .type("function")
                                    .function(LLMRequest.Function.builder()
                                            .name("calculator")
                                            .description("Performs calculations")
                                            .build())
                                    .build()
                    ))
                    .maxTokens(1000)
                    .build();

            int estimated = enforcer.estimateRequestTokens(request);

            // Messages + tool overhead + max_tokens buffer
            assertThat(estimated).isGreaterThan(1000);
        }

        @Test
        @DisplayName("should handle request with no tools")
        void handleRequestWithNoTools() {
            LLMRequest request = LLMRequest.builder()
                    .messages(List.of(
                            LLMRequest.Message.builder()
                                    .role("user")
                                    .content("Hello")
                                    .build()
                    ))
                    .tools(null)
                    .maxTokens(500)
                    .build();

            int estimated = enforcer.estimateRequestTokens(request);

            // Message tokens + max_tokens buffer (no tool overhead)
            assertThat(estimated).isGreaterThanOrEqualTo(500);
            assertThat(estimated).isLessThan(600); // No tool overhead
        }
    }

    @Nested
    @DisplayName("Conversation Budget")
    class ConversationBudgetTests {

        @Test
        @DisplayName("should get remaining conversation budget from Redis")
        void getRemainingConversationBudgetFromRedis() {
            String conversationId = "conv-123";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("5000"));

            StepVerifier.create(enforcer.getRemainingConversationBudget(conversationId))
                    .assertNext(remaining -> {
                        assertThat(remaining).isEqualTo(16000 - 5000);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return full budget when no usage recorded")
        void returnFullBudgetWhenNoUsageRecorded() {
            String conversationId = "conv-new";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(enforcer.getRemainingConversationBudget(conversationId))
                    .assertNext(remaining -> {
                        assertThat(remaining).isEqualTo(16000);
                    })
                    .verifyComplete();
        }
    }

    private AgentContext createContext(int usedTokens, int maxTokens) {
        AgentContext.TokenBudget budget = AgentContext.TokenBudget.builder()
                .maxTokens(maxTokens)
                .usedTokens(usedTokens)
                .usedInputTokens(usedTokens / 2)
                .usedOutputTokens(usedTokens / 2)
                .maxInputTokens(maxTokens)
                .maxOutputTokens(maxTokens)
                .build();

        return AgentContext.builder()
                .taskId("test-task")
                .agentId("test-agent")
                .tokenBudget(budget)
                .build();
    }
}
