package com.z254.butterfly.cortex.health;

import com.z254.butterfly.cortex.config.CortexProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Health indicator for CORTEX service.
 * Reports health status including LLM connectivity, memory systems, and task processing.
 */
@Component
@Slf4j
public class CortexHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final CortexProperties cortexProperties;

    // Runtime metrics
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);
    private final AtomicLong totalTasksProcessed = new AtomicLong(0);
    private final AtomicLong totalLLMCalls = new AtomicLong(0);
    private final AtomicLong llmErrors = new AtomicLong(0);

    public CortexHealthIndicator(
            ReactiveRedisTemplate<String, String> redisTemplate,
            CortexProperties cortexProperties) {
        this.redisTemplate = redisTemplate;
        this.cortexProperties = cortexProperties;
    }

    @Override
    public Mono<Health> health() {
        return checkRedisHealth()
                .map(redisUp -> {
                    Health.Builder builder = redisUp ? Health.up() : Health.down();
                    
                    builder.withDetail("redis", redisUp ? "UP" : "DOWN");
                    builder.withDetail("activeTasks", activeTaskCount.get());
                    builder.withDetail("totalTasksProcessed", totalTasksProcessed.get());
                    builder.withDetail("totalLLMCalls", totalLLMCalls.get());
                    builder.withDetail("llmErrorRate", calculateErrorRate());
                    builder.withDetail("defaultLLMProvider", cortexProperties.getLlm().getDefaultProvider());
                    builder.withDetail("safetyEnabled", cortexProperties.getSafety().isEnabled());

                    // Add LLM provider status
                    builder.withDetail("openai.enabled", cortexProperties.getLlm().getOpenai().isEnabled());
                    builder.withDetail("anthropic.enabled", cortexProperties.getLlm().getAnthropic().isEnabled());
                    builder.withDetail("ollama.enabled", cortexProperties.getLlm().getOllama().isEnabled());

                    return builder.build();
                })
                .onErrorResume(e -> {
                    log.error("Health check failed", e);
                    return Mono.just(Health.down()
                            .withDetail("error", e.getMessage())
                            .build());
                });
    }

    private Mono<Boolean> checkRedisHealth() {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .map(response -> "PONG".equals(response))
                .timeout(Duration.ofSeconds(5))
                .onErrorReturn(false);
    }

    private double calculateErrorRate() {
        long total = totalLLMCalls.get();
        if (total == 0) return 0.0;
        return (double) llmErrors.get() / total;
    }

    // Methods to update metrics (called by other components)

    public void taskStarted() {
        activeTaskCount.incrementAndGet();
    }

    public void taskCompleted() {
        activeTaskCount.decrementAndGet();
        totalTasksProcessed.incrementAndGet();
    }

    public void taskFailed() {
        activeTaskCount.decrementAndGet();
        totalTasksProcessed.incrementAndGet();
    }

    public void llmCallCompleted(boolean success) {
        totalLLMCalls.incrementAndGet();
        if (!success) {
            llmErrors.incrementAndGet();
        }
    }

    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    public long getTotalTasksProcessed() {
        return totalTasksProcessed.get();
    }
}
