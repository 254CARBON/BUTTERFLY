package com.z254.butterfly.cortex.tool.builtin;

import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.tool.AgentTool;
import com.z254.butterfly.cortex.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Built-in tool for interacting with PLATO governance.
 * Allows agents to check policies, request approvals, and query artifacts.
 */
@Component
@Slf4j
public class PlatoGovernanceTool implements AgentTool {

    private static final String TOOL_ID = "cortex.plato.governance";
    private static final String TOOL_NAME = "plato_governance";

    private static final Map<String, Object> PARAMETER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "action", Map.of(
                            "type", "string",
                            "enum", java.util.List.of("check_policy", "request_approval", "query_spec", "get_artifact"),
                            "description", "Governance action to perform"
                    ),
                    "policy_id", Map.of(
                            "type", "string",
                            "description", "Policy ID for policy checks"
                    ),
                    "action_description", Map.of(
                            "type", "string",
                            "description", "Description of action requiring approval"
                    ),
                    "spec_id", Map.of(
                            "type", "string",
                            "description", "Specification ID for queries"
                    ),
                    "artifact_id", Map.of(
                            "type", "string",
                            "description", "Artifact ID for retrieval"
                    ),
                    "context", Map.of(
                            "type", "object",
                            "description", "Additional context for the governance action"
                    )
            ),
            "required", java.util.List.of("action")
    );

    @Override
    public String getId() {
        return TOOL_ID;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "Interact with PLATO governance service. " +
               "Use 'check_policy' to verify if an action is allowed. " +
               "Use 'request_approval' to submit an action for human approval. " +
               "Use 'query_spec' to get specification details. " +
               "Use 'get_artifact' to retrieve governance artifacts.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> parameters, AgentContext context) {
        String action = (String) parameters.get("action");
        
        log.debug("Executing PLATO governance action: {}", action);

        // TODO: Replace with actual PlatoGovernanceClient call
        return Mono.fromCallable(() -> {
            switch (action) {
                case "check_policy":
                    return executePolicyCheck(parameters, context);
                case "request_approval":
                    return executeApprovalRequest(parameters, context);
                case "query_spec":
                    return executeSpecQuery(parameters);
                case "get_artifact":
                    return executeArtifactRetrieval(parameters);
                default:
                    return ToolResult.failure(TOOL_ID, "INVALID_ACTION",
                            "Unknown governance action: " + action);
            }
        });
    }

    private ToolResult executePolicyCheck(Map<String, Object> params, AgentContext context) {
        String policyId = (String) params.get("policy_id");
        
        // Stub: Always allow in stub mode
        String output = String.format(
                "Policy Check Result:\n" +
                "Policy: %s\n" +
                "Result: ALLOWED [Stub mode - actual policy evaluation pending]\n" +
                "Context: Task %s, User %s\n" +
                "Note: In production, this would evaluate the actual policy against the context.",
                policyId != null ? policyId : "default",
                context.getTaskId(),
                context.getUserId()
        );
        return ToolResult.success(TOOL_ID, Map.of(
                "allowed", true,
                "stub", true,
                "policyId", policyId != null ? policyId : "default"
        ), output);
    }

    private ToolResult executeApprovalRequest(Map<String, Object> params, AgentContext context) {
        String actionDescription = (String) params.get("action_description");
        
        // Stub: Return pending approval
        String approvalId = "approval-stub-" + System.currentTimeMillis();
        String output = String.format(
                "Approval Request Submitted:\n" +
                "Approval ID: %s\n" +
                "Action: %s\n" +
                "Status: PENDING [Stub mode - actual approval workflow pending]\n" +
                "Note: In production, this would create an approval request in PLATO.",
                approvalId,
                actionDescription != null ? actionDescription : "Unspecified action"
        );
        return ToolResult.success(TOOL_ID, Map.of(
                "approvalId", approvalId,
                "status", "PENDING",
                "stub", true
        ), output);
    }

    private ToolResult executeSpecQuery(Map<String, Object> params) {
        String specId = (String) params.get("spec_id");
        
        String output = String.format(
                "Specification Query:\n" +
                "Spec ID: %s\n" +
                "[Stub - PLATO integration pending]\n" +
                "No specification data available in stub mode.",
                specId != null ? specId : "unspecified"
        );
        return ToolResult.success(TOOL_ID, output);
    }

    private ToolResult executeArtifactRetrieval(Map<String, Object> params) {
        String artifactId = (String) params.get("artifact_id");
        
        String output = String.format(
                "Artifact Retrieval:\n" +
                "Artifact ID: %s\n" +
                "[Stub - PLATO integration pending]\n" +
                "No artifact data available in stub mode.",
                artifactId != null ? artifactId : "unspecified"
        );
        return ToolResult.success(TOOL_ID, output);
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public boolean requiresApproval() {
        return false;  // This tool checks/requests approval, doesn't need it itself
    }
}
