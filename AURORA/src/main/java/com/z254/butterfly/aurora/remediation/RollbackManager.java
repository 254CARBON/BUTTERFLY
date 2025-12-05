package com.z254.butterfly.aurora.remediation;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rollback manager for remediation actions.
 * <p>
 * Handles:
 * <ul>
 *     <li>Health-window based monitoring after remediation</li>
 *     <li>Automatic rollback on degradation detection</li>
 *     <li>Manual rollback execution</li>
 *     <li>Rollback history tracking</li>
 * </ul>
 */
@Slf4j
@Component
public class RollbackManager {

    private final AuroraProperties auroraProperties;
    private final HealingConnectorClient healingClient;
    private final AuroraMetrics metrics;
    private final AuroraStructuredLogger logger;
    private final WebClient webClient;

    // Track active health monitoring contexts
    private final Map<String, MonitoringContext> activeMonitoring = new ConcurrentHashMap<>();

    public RollbackManager(AuroraProperties auroraProperties,
                           HealingConnectorClient healingClient,
                           AuroraMetrics metrics,
                           AuroraStructuredLogger logger,
                           WebClient.Builder webClientBuilder) {
        this.auroraProperties = auroraProperties;
        this.healingClient = healingClient;
        this.metrics = metrics;
        this.logger = logger;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Start health monitoring after a successful remediation.
     */
    public Mono<MonitoringResult> startHealthMonitoring(RollbackContext context) {
        return Mono.fromCallable(() -> {
            Duration healthWindow = auroraProperties.getSafety().getRollbackHealthWindow();
            
            MonitoringContext monitoringContext = MonitoringContext.builder()
                    .remediationId(context.getRemediationId())
                    .rollbackContext(context)
                    .startedAt(Instant.now())
                    .expiresAt(Instant.now().plus(healthWindow))
                    .healthCheckUrl(context.getHealthCheckUrl())
                    .checkCount(0)
                    .failureCount(0)
                    .build();

            activeMonitoring.put(context.getRemediationId(), monitoringContext);

            log.info("Started health monitoring for remediation {}, window: {}", 
                    context.getRemediationId(), healthWindow);

            return MonitoringResult.builder()
                    .remediationId(context.getRemediationId())
                    .status(MonitoringStatus.STARTED)
                    .expiresAt(monitoringContext.getExpiresAt())
                    .build();
        });
    }

    /**
     * Execute a rollback for a remediation.
     */
    public Mono<AutoRemediator.RollbackResult> executeRollback(RollbackContext context) {
        String remediationId = context.getRemediationId();

        log.info("Executing rollback for remediation: {}", remediationId);

        if (!context.getPlaybook().isRollbackSupported()) {
            return Mono.just(AutoRemediator.RollbackResult.builder()
                    .remediationId(remediationId)
                    .success(false)
                    .message("Rollback not supported for this playbook")
                    .timestamp(Instant.now())
                    .build());
        }

        // Build rollback request
        HealingConnectorClient.HealingRequest rollbackRequest = 
                buildRollbackRequest(context);

        return healingClient.execute(rollbackRequest)
                .map(result -> {
                    // Remove from monitoring
                    activeMonitoring.remove(remediationId);

                    if (result.isSuccess()) {
                        metrics.recordRemediationRolledBack();
                        log.info("Rollback successful for remediation: {}", remediationId);
                    } else {
                        log.error("Rollback failed for remediation {}: {}", 
                                remediationId, result.getErrorMessage());
                    }

                    return AutoRemediator.RollbackResult.builder()
                            .remediationId(remediationId)
                            .success(result.isSuccess())
                            .message(result.isSuccess() ? 
                                    "Rollback completed successfully" : 
                                    result.getErrorMessage())
                            .timestamp(Instant.now())
                            .build();
                });
    }

    /**
     * Stop monitoring for a remediation (successful completion).
     */
    public void stopMonitoring(String remediationId) {
        MonitoringContext context = activeMonitoring.remove(remediationId);
        if (context != null) {
            log.info("Stopped health monitoring for remediation {} (no issues detected)", 
                    remediationId);
        }
    }

    /**
     * Get monitoring status for a remediation.
     */
    public Optional<MonitoringContext> getMonitoringStatus(String remediationId) {
        return Optional.ofNullable(activeMonitoring.get(remediationId));
    }

    /**
     * Periodic health check for active monitoring contexts.
     */
    @Scheduled(fixedDelayString = "${aurora.rollback.check-interval:30000}")
    public void performHealthChecks() {
        Instant now = Instant.now();

        for (Map.Entry<String, MonitoringContext> entry : activeMonitoring.entrySet()) {
            MonitoringContext context = entry.getValue();

            // Check if monitoring window expired
            if (now.isAfter(context.getExpiresAt())) {
                log.info("Health monitoring window expired for {}, no rollback needed", 
                        context.getRemediationId());
                activeMonitoring.remove(entry.getKey());
                continue;
            }

            // Perform health check
            checkHealth(context)
                    .subscribe(healthy -> {
                        if (!healthy) {
                            handleHealthCheckFailure(context);
                        } else {
                            context.setCheckCount(context.getCheckCount() + 1);
                            context.setLastHealthyAt(Instant.now());
                        }
                    });
        }
    }

    // ========== Private Methods ==========

    private Mono<Boolean> checkHealth(MonitoringContext context) {
        if (context.getHealthCheckUrl() == null) {
            return Mono.just(true); // No health check configured
        }

        return webClient.get()
                .uri(context.getHealthCheckUrl())
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .onErrorReturn(false)
                .timeout(Duration.ofSeconds(10))
                .onErrorReturn(false);
    }

    private void handleHealthCheckFailure(MonitoringContext context) {
        context.setFailureCount(context.getFailureCount() + 1);
        int maxFailures = 3; // Configurable threshold

        log.warn("Health check failed for remediation {}, failure count: {}/{}", 
                context.getRemediationId(), context.getFailureCount(), maxFailures);

        if (context.getFailureCount() >= maxFailures) {
            log.error("Health check threshold exceeded for {}, triggering automatic rollback", 
                    context.getRemediationId());

            // Trigger automatic rollback
            executeRollback(context.getRollbackContext())
                    .subscribe(result -> {
                        if (result.isSuccess()) {
                            logger.logRemediationEvent(context.getRemediationId(), null,
                                    AuroraStructuredLogger.RemediationEventType.ROLLED_BACK,
                                    "Automatic rollback triggered due to health check failures",
                                    Map.of("failureCount", context.getFailureCount()));
                        }
                    });

            activeMonitoring.remove(context.getRemediationId());
        }
    }

    private HealingConnectorClient.HealingRequest buildRollbackRequest(RollbackContext context) {
        String rollbackAction = determineRollbackAction(context.getPlaybook().getPrimaryAction());

        return HealingConnectorClient.HealingRequest.builder()
                .remediationId(context.getRemediationId() + "-rollback")
                .actionType(rollbackAction)
                .targetComponent(context.getPlaybook().getName())
                .parameters(buildRollbackParameters(context))
                .executionMode(AuroraProperties.ExecutionMode.PRODUCTION)
                .timeoutMs(Duration.ofMinutes(5).toMillis())
                .build();
    }

    private String determineRollbackAction(String originalAction) {
        return switch (originalAction) {
            case "SCALE_UP" -> "SCALE_DOWN";
            case "SCALE_DOWN" -> "SCALE_UP";
            case "CIRCUIT_BREAK" -> "CIRCUIT_CLOSE";
            case "TRAFFIC_REDIRECT" -> "TRAFFIC_RESTORE";
            case "FAILOVER" -> "FAILBACK";
            case "THROTTLE" -> "UNTHROTTLE";
            default -> "RESTORE";
        };
    }

    private Map<String, String> buildRollbackParameters(RollbackContext context) {
        Map<String, String> params = new HashMap<>();
        
        if (context.getOriginalState() != null) {
            context.getOriginalState().forEach((k, v) -> 
                    params.put("restore_" + k, String.valueOf(v)));
        }
        
        params.put("is_rollback", "true");
        params.put("original_remediation_id", context.getRemediationId());
        
        return params;
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class RollbackContext {
        private String remediationId;
        private RemediationPlaybook.Playbook playbook;
        private Map<String, Object> originalState;
        private String healthCheckUrl;
    }

    @Data
    @Builder
    public static class MonitoringContext {
        private String remediationId;
        private RollbackContext rollbackContext;
        private Instant startedAt;
        private Instant expiresAt;
        private String healthCheckUrl;
        private int checkCount;
        private int failureCount;
        private Instant lastHealthyAt;
    }

    @Data
    @Builder
    public static class MonitoringResult {
        private String remediationId;
        private MonitoringStatus status;
        private Instant expiresAt;
    }

    public enum MonitoringStatus {
        STARTED,
        HEALTHY,
        DEGRADED,
        EXPIRED,
        ROLLED_BACK
    }
}
