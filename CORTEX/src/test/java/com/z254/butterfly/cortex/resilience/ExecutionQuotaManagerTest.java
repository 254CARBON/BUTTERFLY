package com.z254.butterfly.cortex.resilience;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager.QuotaCheckResult;
import com.z254.butterfly.cortex.resilience.ExecutionQuotaManager.QuotaType;
import io.micrometer.core.instrument.Counter;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExecutionQuotaManager}.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionQuotaManagerTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private CortexProperties cortexProperties;
    private MeterRegistry meterRegistry;
    private ExecutionQuotaManager quotaManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cortexProperties = new CortexProperties();
        
        var quotaConfig = cortexProperties.getSafety().getQuotas();
        quotaConfig.setMaxTasksPerUserPerDay(100);
        quotaConfig.setMaxTasksPerAgentPerDay(500);
        quotaConfig.setMaxTasksPerNamespacePerDay(1000);
        quotaConfig.setMaxRequestsPerMinute(30);
        
        quotaManager = new ExecutionQuotaManager(redisTemplate, cortexProperties, meterRegistry);
    }

    @Nested
    @DisplayName("User Quota")
    class UserQuotaTests {

        @Test
        @DisplayName("should allow when user quota not exceeded")
        void allowWhenUserQuotaNotExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("50"));

            StepVerifier.create(quotaManager.checkUserQuota("user-123"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.USER);
                        assertThat(result.getRemaining()).isEqualTo(50);
                        assertThat(result.getUsed()).isEqualTo(50);
                        assertThat(result.getLimit()).isEqualTo(100);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny when user quota exceeded")
        void denyWhenUserQuotaExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("100"));

            StepVerifier.create(quotaManager.checkUserQuota("user-123"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.USER);
                        assertThat(result.getReason()).contains("User daily quota exceeded");
                    })
                    .verifyComplete();

            // Verify metric was incremented
            Counter counter = meterRegistry.find("cortex.quota.exceeded").counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("should allow when no prior usage")
        void allowWhenNoPriorUsage() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(quotaManager.checkUserQuota("new-user"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getRemaining()).isEqualTo(100);
                        assertThat(result.getUsed()).isEqualTo(0);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should allow when user ID is null")
        void allowWhenUserIdIsNull() {
            StepVerifier.create(quotaManager.checkUserQuota(null))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getRemaining()).isEqualTo(100);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Agent Quota")
    class AgentQuotaTests {

        @Test
        @DisplayName("should allow when agent quota not exceeded")
        void allowWhenAgentQuotaNotExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("250"));

            StepVerifier.create(quotaManager.checkAgentQuota("agent-456"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.AGENT);
                        assertThat(result.getRemaining()).isEqualTo(250);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny when agent quota exceeded")
        void denyWhenAgentQuotaExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("500"));

            StepVerifier.create(quotaManager.checkAgentQuota("agent-456"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.AGENT);
                        assertThat(result.getReason()).contains("Agent daily quota exceeded");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should allow when agent ID is null")
        void allowWhenAgentIdIsNull() {
            StepVerifier.create(quotaManager.checkAgentQuota(null))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getRemaining()).isEqualTo(500);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Namespace Quota")
    class NamespaceQuotaTests {

        @Test
        @DisplayName("should allow when namespace quota not exceeded")
        void allowWhenNamespaceQuotaNotExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("500"));

            StepVerifier.create(quotaManager.checkNamespaceQuota("production"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.NAMESPACE);
                        assertThat(result.getRemaining()).isEqualTo(500);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny when namespace quota exceeded")
        void denyWhenNamespaceQuotaExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("1000"));

            StepVerifier.create(quotaManager.checkNamespaceQuota("production"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.NAMESPACE);
                        assertThat(result.getReason()).contains("Namespace daily quota exceeded");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitTests {

        @Test
        @DisplayName("should allow when rate limit not exceeded")
        void allowWhenRateLimitNotExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("15"));

            StepVerifier.create(quotaManager.checkRateLimit("user-123"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.RATE_LIMIT);
                        assertThat(result.getRemaining()).isEqualTo(15);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny when rate limit exceeded")
        void denyWhenRateLimitExceeded() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("30"));

            StepVerifier.create(quotaManager.checkRateLimit("user-123"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.RATE_LIMIT);
                        assertThat(result.getReason()).contains("Rate limit exceeded");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should allow when user ID is null")
        void allowWhenUserIdIsNull() {
            StepVerifier.create(quotaManager.checkRateLimit(null))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getRemaining()).isEqualTo(30);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Combined Quota Check")
    class CombinedQuotaTests {

        @Test
        @DisplayName("should allow when all quotas pass")
        void allowWhenAllQuotasPass() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(Mono.just("10"));

            StepVerifier.create(quotaManager.checkQuota("user-1", "agent-1", "namespace-1"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.COMBINED);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should deny when user quota exceeded")
        void denyWhenUserQuotaExceededInCombined() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                if (key.contains("user")) {
                    return Mono.just("100"); // At limit
                }
                return Mono.just("10"); // Others under limit
            });

            StepVerifier.create(quotaManager.checkQuota("user-1", "agent-1", "namespace-1"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isFalse();
                        assertThat(result.getQuotaType()).isEqualTo(QuotaType.USER);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return minimum remaining across all quotas")
        void returnMinimumRemainingAcrossAllQuotas() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenAnswer(inv -> {
                String key = inv.getArgument(0);
                // Rate limit key contains both "rate" and user ID, check it first
                if (key.contains("rate")) {
                    return Mono.just("20"); // rate limit: 10 remaining (out of 30)
                } else if (key.contains("user")) {
                    return Mono.just("95"); // 5 remaining (out of 100)
                } else if (key.contains("agent")) {
                    return Mono.just("490"); // 10 remaining (out of 500)
                } else if (key.contains("namespace")) {
                    return Mono.just("985"); // 15 remaining (out of 1000)
                } else {
                    return Mono.just("0");
                }
            });

            StepVerifier.create(quotaManager.checkQuota("user-1", "agent-1", "namespace-1"))
                    .assertNext(result -> {
                        assertThat(result.isAllowed()).isTrue();
                        assertThat(result.getRemaining()).isEqualTo(5); // Minimum (user quota)
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Execution Recording")
    class ExecutionRecordingTests {

        @Test
        @DisplayName("should increment all quota counters")
        void incrementAllQuotaCounters() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            StepVerifier.create(quotaManager.recordExecution("user-1", "agent-1", "namespace-1"))
                    .verifyComplete();

            // Verify increment was called for user, agent, namespace, and rate limit
            verify(valueOperations, atLeast(1)).increment(org.mockito.ArgumentMatchers.contains("user"));
            verify(valueOperations, atLeast(1)).increment(org.mockito.ArgumentMatchers.contains("agent"));
            verify(valueOperations, atLeast(1)).increment(org.mockito.ArgumentMatchers.contains("namespace"));
        }

        @Test
        @DisplayName("should set expiry on first increment")
        void setExpiryOnFirstIncrement() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L)); // First increment
            when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

            StepVerifier.create(quotaManager.recordExecution("user-1", "agent-1", "namespace-1"))
                    .verifyComplete();

            verify(redisTemplate, atLeast(1)).expire(anyString(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("QuotaCheckResult")
    class QuotaCheckResultTests {

        @Test
        @DisplayName("should create allowed result")
        void createAllowedResult() {
            QuotaCheckResult result = QuotaCheckResult.allowed(50);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemaining()).isEqualTo(50);
        }

        @Test
        @DisplayName("should create exceeded result")
        void createExceededResult() {
            QuotaCheckResult result = QuotaCheckResult.exceeded(
                    QuotaType.USER, "Quota exceeded");

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getQuotaType()).isEqualTo(QuotaType.USER);
            assertThat(result.getReason()).isEqualTo("Quota exceeded");
            assertThat(result.getRemaining()).isEqualTo(0);
        }
    }
}
