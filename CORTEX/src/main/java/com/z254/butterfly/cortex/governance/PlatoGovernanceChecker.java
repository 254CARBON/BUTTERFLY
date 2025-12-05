package com.z254.butterfly.cortex.governance;

import com.z254.butterfly.cortex.client.PlatoGovernanceClient;
import com.z254.butterfly.cortex.client.PlatoGovernanceClient.PolicyEvaluation;
import com.z254.butterfly.cortex.client.PlatoGovernanceClient.PolicyEvaluationRequest;
import com.z254.butterfly.cortex.config.CortexProperties;
import com.z254.butterfly.cortex.domain.model.Agent;
import com.z254.butterfly.cortex.domain.model.AgentContext;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Governance checker for agent operations.
 * Evaluates policies before task execution and tool calls.
 */
@Component
@Slf4j
public class PlatoGovernanceChecker {

    private final PlatoGovernanceClient platoClient;
    private final CortexProperties cortexProperties;
    private final Counter policyCheckCounter;
    private final Counter policyDenyCounter;

    public PlatoGovernanceChecker(
            PlatoGovernanceClient platoClient,
            CortexProperties cortexProperties,
            MeterRegistry meterRegistry) {
        this.platoClient = platoClient;
        this.cortexProperties = cortexProperties;
        
        this.policyCheckCounter = Counter.builder("cortex.governance.policy_checks").register(meterRegistry);
        this.policyDenyCounter = Counter.builder("cortex.governance.policy_denies").register(meterRegistry);
    }

    /**
     * Check if a task is allowed to execute.
     */
    public Mono<GovernanceDecision> checkTaskExecution(Agent agent, AgentTask task, AgentContext context) {
        if (!isGovernanceEnabled(agent)) {
            return Mono.just(GovernanceDecision.allowed("Governance disabled"));
        }

        policyCheckCounter.increment();
        
        String policyId = agent.getGovernanceConfig() != null 
                ? agent.getGovernanceConfig().getDefaultPolicyId()
                : "default";

        Map<String, Object> evalContext = buildTaskContext(agent, task, context);

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                policyId,
                agent.getId(),
                task.getId(),
                "task_execution",
                evalContext,
                Map.of("source", "cortex")
        );

        return platoClient.evaluatePolicy(request)
                .map(eval -> mapToDecision(eval, "task_execution"))
                .doOnSuccess(decision -> {
                    if (!decision.isAllowed()) {
                        policyDenyCounter.increment();
                        log.warn("Task {} denied by governance: {}", task.getId(), decision.getReason());
                    }
                });
    }

    /**
     * Check if a tool call is allowed.
     */
    public Mono<GovernanceDecision> checkToolCall(
            Agent agent, AgentTask task, String toolId, 
            Map<String, Object> parameters, AgentContext context) {
        
        if (!isGovernanceEnabled(agent)) {
            return Mono.just(GovernanceDecision.allowed("Governance disabled"));
        }

        // Skip governance for low-risk tools
        if (!requiresGovernance(toolId, agent)) {
            return Mono.just(GovernanceDecision.allowed("Tool exempt from governance"));
        }

        policyCheckCounter.increment();

        String policyId = getToolPolicyId(toolId, agent);

        Map<String, Object> evalContext = new HashMap<>();
        evalContext.put("agentId", agent.getId());
        evalContext.put("taskId", task.getId());
        evalContext.put("toolId", toolId);
        evalContext.put("parameters", parameters);
        evalContext.put("userId", context.getUserId());

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                policyId,
                agent.getId(),
                task.getId(),
                "tool_call:" + toolId,
                evalContext,
                Map.of("toolId", toolId)
        );

        return platoClient.evaluatePolicy(request)
                .map(eval -> mapToDecision(eval, "tool_call"))
                .doOnSuccess(decision -> {
                    if (!decision.isAllowed()) {
                        policyDenyCounter.increment();
                        log.warn("Tool call {} denied for task {}: {}", 
                                toolId, task.getId(), decision.getReason());
                    }
                });
    }

    /**
     * Check if output is allowed to be returned.
     */
    public Mono<GovernanceDecision> checkOutput(
            Agent agent, AgentTask task, String output, AgentContext context) {
        
        if (!isGovernanceEnabled(agent)) {
            return Mono.just(GovernanceDecision.allowed("Governance disabled"));
        }

        Agent.GovernanceConfig govConfig = agent.getGovernanceConfig();
        if (govConfig == null || !govConfig.isOutputValidationEnabled()) {
            return Mono.just(GovernanceDecision.allowed("Output validation disabled"));
        }

        policyCheckCounter.increment();

        Map<String, Object> evalContext = new HashMap<>();
        evalContext.put("agentId", agent.getId());
        evalContext.put("taskId", task.getId());
        evalContext.put("output", output);
        evalContext.put("outputLength", output != null ? output.length() : 0);
        evalContext.put("userId", context.getUserId());

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                govConfig.getOutputPolicyId() != null ? govConfig.getOutputPolicyId() : "output-policy",
                agent.getId(),
                task.getId(),
                "output_validation",
                evalContext,
                Map.of()
        );

        return platoClient.evaluatePolicy(request)
                .map(eval -> mapToDecision(eval, "output_validation"));
    }

    private boolean isGovernanceEnabled(Agent agent) {
        return agent.getGovernanceConfig() != null && agent.getGovernanceConfig().isEnabled();
    }

    private boolean requiresGovernance(String toolId, Agent agent) {
        Agent.GovernanceConfig govConfig = agent.getGovernanceConfig();
        if (govConfig == null) return false;
        
        // Check if tool is in high-risk list
        if (govConfig.getHighRiskTools() != null && govConfig.getHighRiskTools().contains(toolId)) {
            return true;
        }
        
        // Check if tool requires approval
        return govConfig.isRequireApprovalForTools();
    }

    private String getToolPolicyId(String toolId, Agent agent) {
        Agent.GovernanceConfig govConfig = agent.getGovernanceConfig();
        if (govConfig != null && govConfig.getToolPolicies() != null) {
            String specific = govConfig.getToolPolicies().get(toolId);
            if (specific != null) return specific;
        }
        return govConfig != null ? govConfig.getDefaultPolicyId() : "default";
    }

    private Map<String, Object> buildTaskContext(Agent agent, AgentTask task, AgentContext context) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("agentId", agent.getId());
        ctx.put("agentType", agent.getType().name());
        ctx.put("taskId", task.getId());
        ctx.put("input", task.getInput());
        ctx.put("inputLength", task.getInput() != null ? task.getInput().length() : 0);
        ctx.put("userId", context.getUserId());
        ctx.put("namespace", context.getNamespace());
        ctx.put("conversationId", context.getConversationId());
        ctx.put("toolCount", context.getAvailableTools() != null ? context.getAvailableTools().size() : 0);
        return ctx;
    }

    private GovernanceDecision mapToDecision(PolicyEvaluation eval, String action) {
        return GovernanceDecision.builder()
                .allowed(eval.allowed())
                .policyId(eval.policyId())
                .reason(eval.reason())
                .violations(eval.violations())
                .recommendations(eval.recommendations())
                .action(action)
                .evaluatedAt(eval.evaluatedAt())
                .build();
    }

    /**
     * Governance decision result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GovernanceDecision {
        private boolean allowed;
        private String policyId;
        private String reason;
        private List<String> violations;
        private Map<String, Object> recommendations;
        private String action;
        private Instant evaluatedAt;

        public static GovernanceDecision allowed(String reason) {
            return GovernanceDecision.builder()
                    .allowed(true)
                    .reason(reason)
                    .evaluatedAt(Instant.now())
                    .build();
        }

        public static GovernanceDecision denied(String reason, List<String> violations) {
            return GovernanceDecision.builder()
                    .allowed(false)
                    .reason(reason)
                    .violations(violations)
                    .evaluatedAt(Instant.now())
                    .build();
        }
    }
}
