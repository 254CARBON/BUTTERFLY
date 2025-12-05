package com.z254.butterfly.cortex.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Structured logging utility for CORTEX.
 * Provides consistent, machine-parseable log entries with context.
 */
@Component
@Slf4j
public class StructuredLogger {

    private final ObjectMapper objectMapper;

    // MDC keys for context
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_TASK_ID = "taskId";
    public static final String MDC_AGENT_ID = "agentId";
    public static final String MDC_CONVERSATION_ID = "conversationId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_PROVIDER_ID = "providerId";

    public StructuredLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Set MDC context for a task.
     */
    public void setTaskContext(String taskId, String agentId, String correlationId, 
                                String conversationId, String userId) {
        if (taskId != null) MDC.put(MDC_TASK_ID, taskId);
        if (agentId != null) MDC.put(MDC_AGENT_ID, agentId);
        if (correlationId != null) MDC.put(MDC_CORRELATION_ID, correlationId);
        if (conversationId != null) MDC.put(MDC_CONVERSATION_ID, conversationId);
        if (userId != null) MDC.put(MDC_USER_ID, userId);
    }

    /**
     * Clear MDC context.
     */
    public void clearContext() {
        MDC.remove(MDC_TASK_ID);
        MDC.remove(MDC_AGENT_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_CONVERSATION_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_PROVIDER_ID);
    }

    /**
     * Log agent task started.
     */
    public void logTaskStarted(String taskId, String agentId, String agentType) {
        logEvent("task_started", Map.of(
                "taskId", taskId,
                "agentId", agentId,
                "agentType", agentType
        ));
    }

    /**
     * Log agent task completed.
     */
    public void logTaskCompleted(String taskId, long durationMs, int tokenCount, 
                                  int toolCalls, String status) {
        logEvent("task_completed", Map.of(
                "taskId", taskId,
                "durationMs", durationMs,
                "tokenCount", tokenCount,
                "toolCalls", toolCalls,
                "status", status
        ));
    }

    /**
     * Log agent task failed.
     */
    public void logTaskFailed(String taskId, String errorCode, String errorMessage) {
        logEvent("task_failed", Map.of(
                "taskId", taskId,
                "errorCode", errorCode,
                "errorMessage", errorMessage != null ? errorMessage : "Unknown error"
        ));
    }

    /**
     * Log LLM call.
     */
    public void logLLMCall(String providerId, String model, int inputTokens, 
                           int outputTokens, long durationMs, boolean success) {
        MDC.put(MDC_PROVIDER_ID, providerId);
        logEvent("llm_call", Map.of(
                "providerId", providerId,
                "model", model,
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "durationMs", durationMs,
                "success", success
        ));
        MDC.remove(MDC_PROVIDER_ID);
    }

    /**
     * Log tool execution.
     */
    public void logToolExecution(String toolId, long durationMs, boolean success, 
                                  String errorCode) {
        Map<String, Object> data = new HashMap<>();
        data.put("toolId", toolId);
        data.put("durationMs", durationMs);
        data.put("success", success);
        if (errorCode != null) {
            data.put("errorCode", errorCode);
        }
        logEvent("tool_execution", data);
    }

    /**
     * Log memory operation.
     */
    public void logMemoryOperation(String operation, String memoryType, 
                                    int entriesAffected, long durationMs) {
        logEvent("memory_operation", Map.of(
                "operation", operation,
                "memoryType", memoryType,
                "entriesAffected", entriesAffected,
                "durationMs", durationMs
        ));
    }

    /**
     * Log governance check.
     */
    public void logGovernanceCheck(String checkType, boolean passed, 
                                    String policyId, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("checkType", checkType);
        data.put("passed", passed);
        if (policyId != null) data.put("policyId", policyId);
        if (reason != null) data.put("reason", reason);
        logEvent("governance_check", data);
    }

    /**
     * Log safety violation.
     */
    public void logSafetyViolation(String violationType, String details, 
                                    String action) {
        logEvent("safety_violation", Map.of(
                "violationType", violationType,
                "details", details,
                "action", action
        ));
    }

    /**
     * Log agent thought for debugging.
     */
    public void logAgentThought(String taskId, String thoughtType, String content) {
        if (log.isDebugEnabled()) {
            logEvent("agent_thought", Map.of(
                    "taskId", taskId,
                    "thoughtType", thoughtType,
                    "contentLength", content != null ? content.length() : 0
            ));
        }
    }

    private void logEvent(String eventType, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>(data);
        event.put("event", eventType);
        event.put("timestamp", Instant.now().toString());
        event.put("service", "cortex");

        // Add MDC context
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null) event.put("correlationId", correlationId);
        
        String taskId = MDC.get(MDC_TASK_ID);
        if (taskId != null) event.put("taskId", taskId);

        try {
            String json = objectMapper.writeValueAsString(event);
            log.info(json);
        } catch (JsonProcessingException e) {
            log.info("event={} data={}", eventType, data);
        }
    }
}
