package com.z254.butterfly.cortex.resilience;

import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.tool.AgentTool;
import com.z254.butterfly.cortex.tool.ToolResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sandbox for safe tool execution.
 * Provides timeouts, call quotas, and parameter validation.
 */
@Component
@Slf4j
public class ToolSandbox {

    private final CortexProperties.SafetyProperties.ToolSandboxProperties config;
    private final Timer toolExecutionTimer;
    private final Counter toolTimeoutCounter;
    private final Counter toolErrorCounter;
    
    // Track tool calls per task
    private final Map<String, TaskToolUsage> taskUsage = new ConcurrentHashMap<>();

    public ToolSandbox(
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.config = cortexProperties.getSafety().getToolSandbox();
        
        this.toolExecutionTimer = Timer.builder("cortex.tool.sandbox.execution")
                .register(meterRegistry);
        this.toolTimeoutCounter = Counter.builder("cortex.tool.sandbox.timeouts")
                .register(meterRegistry);
        this.toolErrorCounter = Counter.builder("cortex.tool.sandbox.errors")
                .register(meterRegistry);
    }

    /**
     * Execute a tool in the sandbox with safety controls.
     */
    public Mono<ToolResult> execute(
            AgentTool tool,
            Map<String, Object> parameters,
            AgentContext context) {
        
        String taskId = context.getTaskId();
        String toolId = tool.getId();
        Instant startTime = Instant.now();

        // Check if tool calls are within quota
        SandboxCheckResult check = checkToolQuota(taskId, toolId);
        if (!check.isAllowed()) {
            log.warn("Tool quota exceeded for task {}: {}", taskId, check.getReason());
            return Mono.just(ToolResult.failure(toolId, "QUOTA_EXCEEDED", check.getReason()));
        }

        // Check parameter size limits
        SandboxCheckResult paramCheck = validateParameters(parameters);
        if (!paramCheck.isAllowed()) {
            log.warn("Invalid parameters for tool {}: {}", toolId, paramCheck.getReason());
            return Mono.just(ToolResult.failure(toolId, "INVALID_PARAMS", paramCheck.getReason()));
        }

        // Get timeout for this tool
        Duration timeout = getToolTimeout(tool);

        log.debug("Executing tool {} in sandbox (timeout: {})", toolId, timeout);

        // Execute with timeout
        return tool.execute(parameters, context)
                .timeout(timeout)
                .doOnSuccess(result -> {
                    recordToolCall(taskId, toolId, true);
                    toolExecutionTimer.record(Duration.between(startTime, Instant.now()));
                    log.debug("Tool {} completed in {}ms", toolId, 
                            Duration.between(startTime, Instant.now()).toMillis());
                })
                .onErrorResume(e -> {
                    recordToolCall(taskId, toolId, false);
                    
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        toolTimeoutCounter.increment();
                        log.warn("Tool {} timed out after {}", toolId, timeout);
                        return Mono.just(ToolResult.timeout(toolId, timeout));
                    }
                    
                    toolErrorCounter.increment();
                    log.error("Tool {} failed: {}", toolId, e.getMessage());
                    return Mono.just(ToolResult.failure(toolId, "EXECUTION_ERROR", e.getMessage()));
                });
    }

    /**
     * Check if a tool can be called within quota.
     */
    public SandboxCheckResult checkToolQuota(String taskId, String toolId) {
        TaskToolUsage usage = taskUsage.computeIfAbsent(taskId, k -> new TaskToolUsage());
        
        // Check total calls
        int totalCalls = usage.getTotalCalls();
        if (totalCalls >= config.getMaxToolCallsPerTask()) {
            return SandboxCheckResult.denied(
                    "Maximum tool calls per task reached: " + config.getMaxToolCallsPerTask());
        }

        // Check consecutive calls to same tool
        int consecutiveCalls = usage.getConsecutiveCalls(toolId);
        if (consecutiveCalls >= config.getMaxConsecutiveSameToolCalls()) {
            return SandboxCheckResult.denied(
                    "Maximum consecutive calls to same tool reached: " + 
                    config.getMaxConsecutiveSameToolCalls());
        }

        return SandboxCheckResult.allowed(config.getMaxToolCallsPerTask() - totalCalls);
    }

    /**
     * Validate parameters for safety.
     */
    public SandboxCheckResult validateParameters(Map<String, Object> parameters) {
        if (parameters == null) {
            return SandboxCheckResult.allowed(Integer.MAX_VALUE);
        }

        // Check parameter count
        if (parameters.size() > config.getMaxParameterCount()) {
            return SandboxCheckResult.denied(
                    "Too many parameters: " + parameters.size() + 
                    " (max: " + config.getMaxParameterCount() + ")");
        }

        // Check parameter sizes
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() instanceof String) {
                String value = (String) entry.getValue();
                if (value.length() > config.getMaxParameterSize()) {
                    return SandboxCheckResult.denied(
                            "Parameter too large: " + entry.getKey() + 
                            " (" + value.length() + " chars, max: " + 
                            config.getMaxParameterSize() + ")");
                }
            }
        }

        return SandboxCheckResult.allowed(Integer.MAX_VALUE);
    }

    /**
     * Get timeout for a specific tool.
     */
    public Duration getToolTimeout(AgentTool tool) {
        // High-risk tools get shorter timeouts
        if (tool.getRiskLevel() == AgentTool.RiskLevel.HIGH) {
            return config.getHighRiskTimeout();
        }
        
        // Use tool-specific timeout if configured
        // For now, use default
        return config.getDefaultTimeout();
    }

    /**
     * Record a tool call.
     */
    private void recordToolCall(String taskId, String toolId, boolean success) {
        TaskToolUsage usage = taskUsage.computeIfAbsent(taskId, k -> new TaskToolUsage());
        usage.recordCall(toolId, success);
    }

    /**
     * Clear usage tracking for a completed task.
     */
    public void clearTaskUsage(String taskId) {
        taskUsage.remove(taskId);
    }

    /**
     * Sandbox check result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SandboxCheckResult {
        private boolean allowed;
        private String reason;
        private int remaining;

        public static SandboxCheckResult allowed(int remaining) {
            return SandboxCheckResult.builder()
                    .allowed(true)
                    .remaining(remaining)
                    .build();
        }

        public static SandboxCheckResult denied(String reason) {
            return SandboxCheckResult.builder()
                    .allowed(false)
                    .reason(reason)
                    .remaining(0)
                    .build();
        }
    }

    /**
     * Tracks tool usage for a task.
     */
    private static class TaskToolUsage {
        private final AtomicInteger totalCalls = new AtomicInteger(0);
        private final Map<String, AtomicInteger> toolCalls = new ConcurrentHashMap<>();
        private String lastToolId = null;
        private int consecutiveCount = 0;

        public int getTotalCalls() {
            return totalCalls.get();
        }

        public int getConsecutiveCalls(String toolId) {
            if (toolId.equals(lastToolId)) {
                return consecutiveCount;
            }
            return 0;
        }

        public void recordCall(String toolId, boolean success) {
            totalCalls.incrementAndGet();
            toolCalls.computeIfAbsent(toolId, k -> new AtomicInteger(0)).incrementAndGet();
            
            if (toolId.equals(lastToolId)) {
                consecutiveCount++;
            } else {
                lastToolId = toolId;
                consecutiveCount = 1;
            }
        }
    }
}
