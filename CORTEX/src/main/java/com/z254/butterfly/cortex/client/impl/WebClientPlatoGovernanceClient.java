package com.z254.butterfly.cortex.client.impl;

import com.z254.butterfly.cortex.client.PlatoGovernanceClient;
import com.z254.butterfly.cortex.config.CortexProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry as ReactorRetry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebClient-based implementation of PlatoGovernanceClient.
 * Follows the same patterns as SYNAPSE's PlatoGovernanceClient.
 */
@Component
@Slf4j
public class WebClientPlatoGovernanceClient implements PlatoGovernanceClient {

    private final WebClient webClient;
    private final CortexProperties.ClientProperties.PlatoClientProperties config;
    private final boolean stubMode;

    public WebClientPlatoGovernanceClient(CortexProperties cortexProperties) {
        this.config = cortexProperties.getClients().getPlato();
        
        this.stubMode = config.getBaseUrl() == null || config.getBaseUrl().isEmpty();
        
        if (!stubMode) {
            this.webClient = WebClient.builder()
                    .baseUrl(config.getBaseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.webClient = null;
            log.warn("PLATO client running in stub mode - governance checks will auto-approve");
        }
    }

    @Override
    @CircuitBreaker(name = "plato")
    @Retry(name = "llm")
    public Mono<PolicyEvaluation> evaluatePolicy(PolicyEvaluationRequest request) {
        if (stubMode) {
            return Mono.just(new PolicyEvaluation(
                    request.policyId(),
                    true,  // Always allowed in stub mode
                    "Stub mode - auto-approved",
                    List.of(),
                    Map.of(),
                    Instant.now()
            ));
        }

        return webClient.post()
                .uri("/api/v1/policies/evaluate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PolicyEvaluation.class)
                .timeout(config.getTimeout())
                .doOnError(e -> log.error("Policy evaluation failed: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "plato")
    @Retry(name = "llm")
    public Mono<PlanSubmission> submitPlan(PlanSubmissionRequest request) {
        if (stubMode) {
            String planId = "stub-plan-" + System.currentTimeMillis();
            String approvalId = "stub-approval-" + System.currentTimeMillis();
            return Mono.just(new PlanSubmission(
                    planId,
                    approvalId,
                    "AUTO_APPROVED",
                    null,
                    Instant.now()
            ));
        }

        return webClient.post()
                .uri("/api/v1/plans/submit")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PlanSubmission.class)
                .timeout(config.getTimeout())
                .doOnSuccess(r -> log.debug("Submitted plan: {} approval: {}", r.planId(), r.approvalId()))
                .doOnError(e -> log.error("Plan submission failed: {}", e.getMessage()));
    }

    @Override
    @CircuitBreaker(name = "plato")
    public Mono<ApprovalStatus> getApprovalStatus(String approvalId) {
        if (stubMode) {
            return Mono.just(new ApprovalStatus(
                    approvalId,
                    "stub-plan",
                    ApprovalStatus.Status.APPROVED,
                    "system",
                    Instant.now(),
                    Instant.now()
            ));
        }

        return webClient.get()
                .uri("/api/v1/approvals/{id}/status", approvalId)
                .retrieve()
                .bodyToMono(ApprovalStatus.class)
                .timeout(config.getTimeout());
    }

    @Override
    public Mono<ApprovalDecision> waitForApproval(String approvalId) {
        if (stubMode) {
            return Mono.just(new ApprovalDecision(
                    approvalId,
                    "stub-plan",
                    true,
                    "system",
                    "Auto-approved in stub mode",
                    List.of(),
                    Instant.now()
            ));
        }

        // Poll for approval with exponential backoff
        return Mono.defer(() -> getApprovalStatus(approvalId))
                .flatMap(status -> {
                    if (status.status() == ApprovalStatus.Status.PENDING ||
                        status.status() == ApprovalStatus.Status.IN_REVIEW) {
                        return Mono.error(new RuntimeException("Approval pending"));
                    }
                    return Mono.just(new ApprovalDecision(
                            status.approvalId(),
                            status.planId(),
                            status.status() == ApprovalStatus.Status.APPROVED,
                            status.assignedTo(),
                            null,
                            List.of(),
                            status.updatedAt()
                    ));
                })
                .retryWhen(ReactorRetry.backoff(config.getApprovalPollMaxRetries(), 
                        Duration.ofSeconds(5))
                        .maxBackoff(Duration.ofMinutes(1))
                        .filter(e -> e.getMessage().contains("pending")))
                .timeout(config.getApprovalTimeout());
    }

    @Override
    @CircuitBreaker(name = "plato")
    public Mono<Void> submitEvidence(String planId, Evidence evidence) {
        if (stubMode) {
            log.debug("Stub mode - evidence submission ignored for plan {}", planId);
            return Mono.empty();
        }

        return webClient.post()
                .uri("/api/v1/plans/{id}/evidence", planId)
                .bodyValue(evidence)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(config.getTimeout());
    }

    @Override
    @CircuitBreaker(name = "plato")
    public Mono<GovernanceSpec> getSpec(String specId) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.get()
                .uri("/api/v1/specs/{id}", specId)
                .retrieve()
                .bodyToMono(GovernanceSpec.class)
                .timeout(config.getTimeout());
    }

    @Override
    @CircuitBreaker(name = "plato")
    public Mono<GovernanceArtifact> getArtifact(String artifactId) {
        if (stubMode) {
            return Mono.empty();
        }

        return webClient.get()
                .uri("/api/v1/artifacts/{id}", artifactId)
                .retrieve()
                .bodyToMono(GovernanceArtifact.class)
                .timeout(config.getTimeout());
    }

    @Override
    public Mono<Boolean> isAvailable() {
        if (stubMode) {
            return Mono.just(false);
        }

        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> "UP".equals(response.get("status")))
                .onErrorReturn(false);
    }
}
