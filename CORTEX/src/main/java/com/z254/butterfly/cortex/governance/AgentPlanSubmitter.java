package com.z254.butterfly.cortex.governance;

import com.z254.butterfly.cortex.client.PlatoGovernanceClient;
import com.z254.butterfly.cortex.client.PlatoGovernanceClient.*;
import com.z254.butterfly.cortex.domain.model.Agent;
import com.z254.butterfly.cortex.domain.model.AgentTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Submits agent execution plans to PLATO for approval.
 * Used by PlanAndExecute agents and high-risk operations.
 */
@Component
@Slf4j
public class AgentPlanSubmitter {

    private final PlatoGovernanceClient platoClient;

    public AgentPlanSubmitter(PlatoGovernanceClient platoClient) {
        this.platoClient = platoClient;
    }

    /**
     * Submit an execution plan for approval.
     */
    public Mono<SubmissionResult> submitPlan(
            Agent agent, 
            AgentTask task, 
            List<String> planSteps,
            Set<String> toolsRequired) {
        
        log.info("Submitting plan for task {} with {} steps", task.getId(), planSteps.size());

        String riskLevel = assessRiskLevel(agent, planSteps, toolsRequired);

        PlanSubmissionRequest request = new PlanSubmissionRequest(
                agent.getId(),
                task.getId(),
                agent.getType().name(),
                "Agent execution plan for task: " + task.getId(),
                planSteps,
                List.copyOf(toolsRequired),
                riskLevel,
                Map.of(
                        "userId", task.getUserId(),
                        "conversationId", task.getConversationId() != null ? task.getConversationId() : "",
                        "namespace", task.getNamespace() != null ? task.getNamespace() : ""
                )
        );

        return platoClient.submitPlan(request)
                .map(submission -> SubmissionResult.builder()
                        .planId(submission.planId())
                        .approvalId(submission.approvalId())
                        .status(submission.status())
                        .approvalUrl(submission.approvalUrl())
                        .submittedAt(submission.submittedAt())
                        .build())
                .doOnSuccess(result -> log.info("Plan submitted: {} approval: {}", 
                        result.getPlanId(), result.getApprovalId()));
    }

    /**
     * Submit evidence for a plan execution step.
     */
    public Mono<Void> submitStepEvidence(
            String planId, 
            int stepNumber, 
            String stepDescription, 
            String result,
            boolean success) {
        
        Evidence evidence = new Evidence(
                "step_execution",
                String.format("Step %d: %s", stepNumber, stepDescription),
                Map.of(
                        "stepNumber", stepNumber,
                        "result", result,
                        "success", success,
                        "timestamp", Instant.now().toString()
                ),
                List.of()
        );

        return platoClient.submitEvidence(planId, evidence);
    }

    /**
     * Submit final plan execution evidence.
     */
    public Mono<Void> submitFinalEvidence(
            String planId,
            String finalOutput,
            int totalSteps,
            int successfulSteps,
            long durationMs) {
        
        Evidence evidence = new Evidence(
                "plan_completion",
                "Plan execution completed",
                Map.of(
                        "finalOutput", finalOutput,
                        "totalSteps", totalSteps,
                        "successfulSteps", successfulSteps,
                        "durationMs", durationMs,
                        "completedAt", Instant.now().toString()
                ),
                List.of()
        );

        return platoClient.submitEvidence(planId, evidence);
    }

    /**
     * Assess the risk level of a plan.
     */
    private String assessRiskLevel(Agent agent, List<String> steps, Set<String> tools) {
        // Check agent configuration
        if (agent.getGovernanceConfig() != null) {
            Set<String> highRiskTools = agent.getGovernanceConfig().getHighRiskTools();
            if (highRiskTools != null) {
                for (String tool : tools) {
                    if (highRiskTools.contains(tool)) {
                        return "HIGH";
                    }
                }
            }
        }

        // Assess based on number of steps and tools
        if (steps.size() > 10 || tools.size() > 5) {
            return "MEDIUM";
        }

        // Check for potentially risky step descriptions
        for (String step : steps) {
            String lower = step.toLowerCase();
            if (lower.contains("delete") || lower.contains("modify") || 
                lower.contains("execute") || lower.contains("update")) {
                return "MEDIUM";
            }
        }

        return "LOW";
    }

    /**
     * Plan submission result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionResult {
        private String planId;
        private String approvalId;
        private String status;
        private String approvalUrl;
        private Instant submittedAt;
        
        public boolean requiresApproval() {
            return "PENDING".equals(status) || "IN_REVIEW".equals(status);
        }
        
        public boolean isAutoApproved() {
            return "AUTO_APPROVED".equals(status) || "APPROVED".equals(status);
        }
    }
}
