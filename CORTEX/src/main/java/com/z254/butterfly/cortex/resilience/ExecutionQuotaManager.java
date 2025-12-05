package com.z254.butterfly.cortex.resilience;

import com.z254.butterfly.cortex.config.CortexProperties;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages execution quotas for agents, users, and namespaces.
 * Enforces rate limits and daily quotas.
 */
@Component
@Slf4j
public class ExecutionQuotaManager {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CortexProperties.SafetyProperties.QuotaProperties config;
    private final Counter quotaExceededCounter;
    private final Counter quotaWarningCounter;

    private static final String QUOTA_KEY_PREFIX = "cortex:quota:";
    private static final String RATE_LIMIT_KEY_PREFIX = "cortex:ratelimit:";

    public ExecutionQuotaManager(
            ReactiveRedisTemplate<String, String> redisTemplate,
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.config = cortexProperties.getSafety().getQuotas();
        
        this.quotaExceededCounter = Counter.builder("cortex.quota.exceeded").register(meterRegistry);
        this.quotaWarningCounter = Counter.builder("cortex.quota.warning").register(meterRegistry);
    }

    /**
     * Check if a task can be executed within quotas.
     */
    public Mono<QuotaCheckResult> checkQuota(String userId, String agentId, String namespace) {
        return Mono.zip(
                checkUserQuota(userId),
                checkAgentQuota(agentId),
                checkNamespaceQuota(namespace),
                checkRateLimit(userId)
        ).map(tuple -> {
            QuotaCheckResult userResult = tuple.getT1();
            QuotaCheckResult agentResult = tuple.getT2();
            QuotaCheckResult namespaceResult = tuple.getT3();
            QuotaCheckResult rateResult = tuple.getT4();

            // If any quota is exceeded, return that
            if (!userResult.isAllowed()) return userResult;
            if (!agentResult.isAllowed()) return agentResult;
            if (!namespaceResult.isAllowed()) return namespaceResult;
            if (!rateResult.isAllowed()) return rateResult;

            // Return with combined remaining
            return QuotaCheckResult.builder()
                    .allowed(true)
                    .quotaType(QuotaType.COMBINED)
                    .remaining(Math.min(
                            Math.min(userResult.getRemaining(), agentResult.getRemaining()),
                            Math.min(namespaceResult.getRemaining(), rateResult.getRemaining())
                    ))
                    .build();
        });
    }

    /**
     * Record task execution against quotas.
     */
    public Mono<Void> recordExecution(String userId, String agentId, String namespace) {
        return Mono.when(
                incrementQuota(getUserQuotaKey(userId)),
                incrementQuota(getAgentQuotaKey(agentId)),
                incrementQuota(getNamespaceQuotaKey(namespace)),
                incrementRateLimit(userId)
        );
    }

    /**
     * Check user daily quota.
     */
    public Mono<QuotaCheckResult> checkUserQuota(String userId) {
        if (userId == null) {
            return Mono.just(QuotaCheckResult.allowed(config.getMaxTasksPerUserPerDay()));
        }

        String key = getUserQuotaKey(userId);
        return getQuotaUsage(key)
                .map(used -> {
                    int max = config.getMaxTasksPerUserPerDay();
                    int remaining = max - used;
                    
                    if (remaining <= 0) {
                        quotaExceededCounter.increment();
                        return QuotaCheckResult.exceeded(QuotaType.USER, 
                                "User daily quota exceeded");
                    }
                    
                    if (remaining <= max * 0.1) {
                        quotaWarningCounter.increment();
                    }
                    
                    return QuotaCheckResult.builder()
                            .allowed(true)
                            .quotaType(QuotaType.USER)
                            .remaining(remaining)
                            .used(used)
                            .limit(max)
                            .build();
                });
    }

    /**
     * Check agent daily quota.
     */
    public Mono<QuotaCheckResult> checkAgentQuota(String agentId) {
        if (agentId == null) {
            return Mono.just(QuotaCheckResult.allowed(config.getMaxTasksPerAgentPerDay()));
        }

        String key = getAgentQuotaKey(agentId);
        return getQuotaUsage(key)
                .map(used -> {
                    int max = config.getMaxTasksPerAgentPerDay();
                    int remaining = max - used;
                    
                    if (remaining <= 0) {
                        quotaExceededCounter.increment();
                        return QuotaCheckResult.exceeded(QuotaType.AGENT,
                                "Agent daily quota exceeded");
                    }
                    
                    return QuotaCheckResult.builder()
                            .allowed(true)
                            .quotaType(QuotaType.AGENT)
                            .remaining(remaining)
                            .used(used)
                            .limit(max)
                            .build();
                });
    }

    /**
     * Check namespace daily quota.
     */
    public Mono<QuotaCheckResult> checkNamespaceQuota(String namespace) {
        if (namespace == null) {
            return Mono.just(QuotaCheckResult.allowed(config.getMaxTasksPerNamespacePerDay()));
        }

        String key = getNamespaceQuotaKey(namespace);
        return getQuotaUsage(key)
                .map(used -> {
                    int max = config.getMaxTasksPerNamespacePerDay();
                    int remaining = max - used;
                    
                    if (remaining <= 0) {
                        quotaExceededCounter.increment();
                        return QuotaCheckResult.exceeded(QuotaType.NAMESPACE,
                                "Namespace daily quota exceeded");
                    }
                    
                    return QuotaCheckResult.builder()
                            .allowed(true)
                            .quotaType(QuotaType.NAMESPACE)
                            .remaining(remaining)
                            .used(used)
                            .limit(max)
                            .build();
                });
    }

    /**
     * Check rate limit (requests per minute).
     */
    public Mono<QuotaCheckResult> checkRateLimit(String userId) {
        if (userId == null) {
            return Mono.just(QuotaCheckResult.allowed(config.getMaxRequestsPerMinute()));
        }

        String key = getRateLimitKey(userId);
        return getQuotaUsage(key)
                .map(used -> {
                    int max = config.getMaxRequestsPerMinute();
                    int remaining = max - used;
                    
                    if (remaining <= 0) {
                        quotaExceededCounter.increment();
                        return QuotaCheckResult.exceeded(QuotaType.RATE_LIMIT,
                                "Rate limit exceeded. Please wait and try again.");
                    }
                    
                    return QuotaCheckResult.builder()
                            .allowed(true)
                            .quotaType(QuotaType.RATE_LIMIT)
                            .remaining(remaining)
                            .used(used)
                            .limit(max)
                            .build();
                });
    }

    private Mono<Integer> getQuotaUsage(String key) {
        return redisTemplate.opsForValue().get(key)
                .map(Integer::parseInt)
                .defaultIfEmpty(0);
    }

    private Mono<Void> incrementQuota(String key) {
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(newValue -> {
                    if (newValue == 1) {
                        // First increment of the day, set expiry
                        return redisTemplate.expire(key, Duration.ofHours(24)).then();
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> incrementRateLimit(String userId) {
        String key = getRateLimitKey(userId);
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(newValue -> {
                    if (newValue == 1) {
                        // First request in this window, set 1 minute expiry
                        return redisTemplate.expire(key, Duration.ofMinutes(1)).then();
                    }
                    return Mono.empty();
                });
    }

    private String getUserQuotaKey(String userId) {
        return QUOTA_KEY_PREFIX + "user:" + LocalDate.now() + ":" + userId;
    }

    private String getAgentQuotaKey(String agentId) {
        return QUOTA_KEY_PREFIX + "agent:" + LocalDate.now() + ":" + agentId;
    }

    private String getNamespaceQuotaKey(String namespace) {
        return QUOTA_KEY_PREFIX + "namespace:" + LocalDate.now() + ":" + namespace;
    }

    private String getRateLimitKey(String userId) {
        long minute = Instant.now().getEpochSecond() / 60;
        return RATE_LIMIT_KEY_PREFIX + userId + ":" + minute;
    }

    /**
     * Quota check result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaCheckResult {
        private boolean allowed;
        private QuotaType quotaType;
        private String reason;
        private int remaining;
        private int used;
        private int limit;

        public static QuotaCheckResult allowed(int remaining) {
            return QuotaCheckResult.builder()
                    .allowed(true)
                    .remaining(remaining)
                    .build();
        }

        public static QuotaCheckResult exceeded(QuotaType type, String reason) {
            return QuotaCheckResult.builder()
                    .allowed(false)
                    .quotaType(type)
                    .reason(reason)
                    .remaining(0)
                    .build();
        }
    }

    /**
     * Quota types.
     */
    public enum QuotaType {
        USER, AGENT, NAMESPACE, RATE_LIMIT, COMBINED
    }
}
