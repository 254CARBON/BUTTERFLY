package com.z254.butterfly.cortex.client;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Client interface for SYNAPSE tool service.
 * SYNAPSE provides tool execution and action management.
 */
public interface SynapseToolClient {

    /**
     * Execute a tool action via SYNAPSE.
     *
     * @param request the action request
     * @return the action result
     */
    Mono<ActionResult> executeAction(ActionRequest request);

    /**
     * Get status of an executing action.
     *
     * @param actionId the action ID
     * @return the action status
     */
    Mono<ActionStatus> getActionStatus(String actionId);

    /**
     * Cancel an executing action.
     *
     * @param actionId the action ID
     * @param reason cancellation reason
     * @return true if cancelled
     */
    Mono<Boolean> cancelAction(String actionId, String reason);

    /**
     * List available tools from SYNAPSE registry.
     *
     * @return flux of tool definitions
     */
    Flux<ToolDefinition> listTools();

    /**
     * Get details of a specific tool.
     *
     * @param toolId the tool ID
     * @return tool definition
     */
    Mono<ToolDefinition> getTool(String toolId);

    /**
     * Check if SYNAPSE is available.
     *
     * @return true if available
     */
    Mono<Boolean> isAvailable();

    /**
     * Action execution request.
     */
    record ActionRequest(
            String actionId,
            String toolId,
            Map<String, Object> parameters,
            String correlationId,
            String platoPlanId,
            String rimNodeId,
            String requesterId,
            ExecutionMode executionMode,
            Map<String, Object> metadata
    ) {
        public enum ExecutionMode {
            SYNC,       // Wait for result
            ASYNC,      // Return immediately, poll for result
            FIRE_FORGET // Don't track result
        }
    }

    /**
     * Action execution result.
     */
    record ActionResult(
            String actionId,
            String toolId,
            boolean success,
            Object result,
            String output,
            String errorCode,
            String errorMessage,
            long durationMs,
            Map<String, Object> metadata
    ) {}

    /**
     * Action status.
     */
    record ActionStatus(
            String actionId,
            String toolId,
            Status status,
            Object partialResult,
            Integer progressPercent,
            String statusMessage
    ) {
        public enum Status {
            PENDING, QUEUED, EXECUTING, COMPLETED, FAILED, CANCELLED, TIMEOUT
        }
    }

    /**
     * Tool definition from SYNAPSE registry.
     */
    record ToolDefinition(
            String id,
            String name,
            String description,
            String connectorType,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            String ownerService,
            String status,
            String riskLevel,
            List<String> governanceTags,
            List<String> requiredPermissions
    ) {}
}
