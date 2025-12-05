package com.z254.butterfly.cortex.tool;

import com.z254.butterfly.cortex.domain.model.AgentContext;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Interface for tools that agents can use.
 * Tools are executable actions that agents can invoke during task execution.
 */
public interface AgentTool {

    /**
     * Get the unique tool ID.
     *
     * @return tool ID
     */
    String getId();

    /**
     * Get the tool name (for display and LLM).
     *
     * @return tool name
     */
    String getName();

    /**
     * Get the tool description (used by LLM to decide when to use).
     *
     * @return tool description
     */
    String getDescription();

    /**
     * Get the JSON Schema for tool parameters.
     *
     * @return parameter schema as map (JSON Schema format)
     */
    Map<String, Object> getParameterSchema();

    /**
     * Execute the tool with given parameters.
     *
     * @param parameters tool parameters
     * @param context agent execution context
     * @return tool result
     */
    Mono<ToolResult> execute(Map<String, Object> parameters, AgentContext context);

    /**
     * Check if the tool requires governance approval.
     *
     * @return true if approval is required
     */
    default boolean requiresApproval() {
        return false;
    }

    /**
     * Get the risk level of this tool.
     *
     * @return risk level
     */
    default RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    /**
     * Check if this tool is available in the given context.
     *
     * @param context the execution context
     * @return true if available
     */
    default boolean isAvailable(AgentContext context) {
        return true;
    }

    /**
     * Validate parameters before execution.
     *
     * @param parameters the parameters to validate
     * @return validation result
     */
    default Mono<ValidationResult> validate(Map<String, Object> parameters) {
        return Mono.just(ValidationResult.success());
    }

    /**
     * Risk levels for tools.
     */
    enum RiskLevel {
        LOW,      // Safe, no approval needed
        MEDIUM,   // Moderate risk
        HIGH,     // High risk, may need approval
        CRITICAL  // Critical, always needs approval
    }

    /**
     * Parameter validation result.
     */
    record ValidationResult(
            boolean valid,
            String message
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
    }
}
