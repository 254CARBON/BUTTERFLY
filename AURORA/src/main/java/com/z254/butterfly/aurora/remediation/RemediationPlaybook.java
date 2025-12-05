package com.z254.butterfly.aurora.remediation;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Remediation playbook repository and selector.
 * <p>
 * Maintains available remediation playbooks and selects the appropriate
 * playbook based on RCA hypothesis characteristics.
 */
@Slf4j
@Component
public class RemediationPlaybook {

    private final AuroraProperties auroraProperties;
    private final Map<String, Playbook> playbookRegistry = new HashMap<>();

    public RemediationPlaybook(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
        initializePlaybooks();
    }

    /**
     * Select the best playbook for an RCA hypothesis.
     */
    public Mono<Playbook> selectPlaybook(RcaHypothesis hypothesis) {
        return Mono.fromCallable(() -> {
            // First try to find a playbook matching the suggested remediation
            Optional<Playbook> suggested = hypothesis.getPrimaryRemediation()
                    .map(RcaHypothesis.SuggestedRemediation::getActionType)
                    .map(playbookRegistry::get);

            if (suggested.isPresent()) {
                log.debug("Selected suggested playbook {} for hypothesis {}",
                        suggested.get().getName(), hypothesis.getHypothesisId());
                return suggested.get();
            }

            // Otherwise select based on root cause type
            Playbook selected = selectByRootCauseType(hypothesis.getRootCauseType());
            log.debug("Selected playbook {} based on root cause type {}",
                    selected.getName(), hypothesis.getRootCauseType());
            return selected;
        });
    }

    /**
     * Get a playbook by action type.
     */
    public Optional<Playbook> getPlaybook(String actionType) {
        return Optional.ofNullable(playbookRegistry.get(actionType));
    }

    /**
     * List all available playbooks.
     */
    public List<Playbook> listPlaybooks() {
        return new ArrayList<>(playbookRegistry.values());
    }

    /**
     * Register a custom playbook.
     */
    public void registerPlaybook(Playbook playbook) {
        playbookRegistry.put(playbook.getPrimaryAction(), playbook);
        log.info("Registered playbook: {}", playbook.getName());
    }

    // ========== Private Methods ==========

    private void initializePlaybooks() {
        // RESTART Playbook
        registerPlaybook(Playbook.builder()
                .name("Service Restart")
                .primaryAction("RESTART")
                .description("Restart the affected service to clear transient issues")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("PRE_CHECK")
                                .description("Verify service is in degraded state")
                                .timeout(Duration.ofSeconds(30))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("GRACEFUL_SHUTDOWN")
                                .description("Gracefully drain connections")
                                .timeout(Duration.ofMinutes(1))
                                .build(),
                        PlaybookStep.builder()
                                .order(3)
                                .action("RESTART")
                                .description("Restart service instance")
                                .timeout(Duration.ofMinutes(2))
                                .build(),
                        PlaybookStep.builder()
                                .order(4)
                                .action("HEALTH_CHECK")
                                .description("Verify service is healthy")
                                .timeout(Duration.ofMinutes(1))
                                .build()
                ))
                .timeout(Duration.ofMinutes(5))
                .riskLevel(0.3)
                .rollbackSupported(false)
                .healthCheckUrl("/actuator/health")
                .applicableRootCauses(Set.of("SERVICE_DEGRADATION", "RESOURCE_CONTENTION"))
                .build());

        // SCALE_UP Playbook
        registerPlaybook(Playbook.builder()
                .name("Scale Up Service")
                .primaryAction("SCALE_UP")
                .description("Increase service instance count to handle load")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("PRE_CHECK")
                                .description("Verify current instance count and capacity")
                                .timeout(Duration.ofSeconds(30))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("SCALE_UP")
                                .description("Add service instances")
                                .timeout(Duration.ofMinutes(3))
                                .parameters(Map.of("increment", "2", "max_instances", "10"))
                                .build(),
                        PlaybookStep.builder()
                                .order(3)
                                .action("HEALTH_CHECK")
                                .description("Verify new instances are healthy")
                                .timeout(Duration.ofMinutes(2))
                                .build()
                ))
                .timeout(Duration.ofMinutes(6))
                .riskLevel(0.2)
                .rollbackSupported(true)
                .healthCheckUrl("/actuator/health")
                .applicableRootCauses(Set.of("RESOURCE_CONTENTION", "LOAD_SPIKE"))
                .parameters(Map.of("increment", "2"))
                .build());

        // CIRCUIT_BREAK Playbook
        registerPlaybook(Playbook.builder()
                .name("Enable Circuit Breaker")
                .primaryAction("CIRCUIT_BREAK")
                .description("Enable circuit breaker to protect from cascading failures")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("PRE_CHECK")
                                .description("Verify circuit breaker configuration exists")
                                .timeout(Duration.ofSeconds(10))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("CIRCUIT_BREAK")
                                .description("Open circuit breaker")
                                .timeout(Duration.ofSeconds(30))
                                .build()
                ))
                .timeout(Duration.ofMinutes(1))
                .riskLevel(0.4)
                .rollbackSupported(true)
                .applicableRootCauses(Set.of("DEPENDENCY_FAILURE", "SERVICE_DEGRADATION"))
                .build());

        // TRAFFIC_REDIRECT Playbook
        registerPlaybook(Playbook.builder()
                .name("Redirect Traffic")
                .primaryAction("TRAFFIC_REDIRECT")
                .description("Redirect traffic away from failing component")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("PRE_CHECK")
                                .description("Verify backup endpoint availability")
                                .timeout(Duration.ofSeconds(30))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("TRAFFIC_REDIRECT")
                                .description("Update routing rules")
                                .timeout(Duration.ofMinutes(1))
                                .build(),
                        PlaybookStep.builder()
                                .order(3)
                                .action("HEALTH_CHECK")
                                .description("Verify traffic is flowing to new endpoint")
                                .timeout(Duration.ofMinutes(1))
                                .build()
                ))
                .timeout(Duration.ofMinutes(3))
                .riskLevel(0.5)
                .rollbackSupported(true)
                .applicableRootCauses(Set.of("NETWORK_ISSUE", "INFRASTRUCTURE_FAILURE"))
                .build());

        // FAILOVER Playbook
        registerPlaybook(Playbook.builder()
                .name("Trigger Failover")
                .primaryAction("FAILOVER")
                .description("Trigger failover to standby system")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("PRE_CHECK")
                                .description("Verify standby is ready")
                                .timeout(Duration.ofMinutes(1))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("FAILOVER")
                                .description("Execute failover procedure")
                                .timeout(Duration.ofMinutes(5))
                                .build(),
                        PlaybookStep.builder()
                                .order(3)
                                .action("HEALTH_CHECK")
                                .description("Verify standby is serving traffic")
                                .timeout(Duration.ofMinutes(2))
                                .build()
                ))
                .timeout(Duration.ofMinutes(10))
                .riskLevel(0.7)
                .rollbackSupported(true)
                .applicableRootCauses(Set.of("INFRASTRUCTURE_FAILURE", "NETWORK_ISSUE"))
                .build());

        // CACHE_CLEAR Playbook
        registerPlaybook(Playbook.builder()
                .name("Clear Cache")
                .primaryAction("CACHE_CLEAR")
                .description("Clear application caches to resolve stale data issues")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("CACHE_CLEAR")
                                .description("Clear specified cache regions")
                                .timeout(Duration.ofMinutes(1))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("HEALTH_CHECK")
                                .description("Verify cache is rebuilt")
                                .timeout(Duration.ofMinutes(2))
                                .build()
                ))
                .timeout(Duration.ofMinutes(5))
                .riskLevel(0.2)
                .rollbackSupported(false)
                .applicableRootCauses(Set.of("DATA_CORRUPTION", "CONFIGURATION_ERROR"))
                .build());

        // ROLLBACK Playbook
        registerPlaybook(Playbook.builder()
                .name("Rollback Deployment")
                .primaryAction("ROLLBACK")
                .description("Rollback to previous known-good deployment")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("PRE_CHECK")
                                .description("Identify previous stable version")
                                .timeout(Duration.ofMinutes(1))
                                .build(),
                        PlaybookStep.builder()
                                .order(2)
                                .action("ROLLBACK")
                                .description("Execute rollback to previous version")
                                .timeout(Duration.ofMinutes(5))
                                .build(),
                        PlaybookStep.builder()
                                .order(3)
                                .action("HEALTH_CHECK")
                                .description("Verify system is stable")
                                .timeout(Duration.ofMinutes(2))
                                .build()
                ))
                .timeout(Duration.ofMinutes(10))
                .riskLevel(0.5)
                .rollbackSupported(false) // Can't rollback a rollback
                .applicableRootCauses(Set.of("DEPLOYMENT_ISSUE", "CONFIGURATION_ERROR"))
                .build());

        // THROTTLE Playbook
        registerPlaybook(Playbook.builder()
                .name("Apply Rate Limiting")
                .primaryAction("THROTTLE")
                .description("Apply rate limiting to reduce load")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("THROTTLE")
                                .description("Apply rate limiting rules")
                                .timeout(Duration.ofSeconds(30))
                                .parameters(Map.of("rate_limit", "1000/s"))
                                .build()
                ))
                .timeout(Duration.ofMinutes(1))
                .riskLevel(0.3)
                .rollbackSupported(true)
                .applicableRootCauses(Set.of("LOAD_SPIKE", "RESOURCE_CONTENTION"))
                .parameters(Map.of("rate_limit", "1000"))
                .build());

        // NOTIFY Playbook (low-risk fallback)
        registerPlaybook(Playbook.builder()
                .name("Notify Operations")
                .primaryAction("NOTIFY")
                .description("Send notification to operations team")
                .steps(List.of(
                        PlaybookStep.builder()
                                .order(1)
                                .action("NOTIFY")
                                .description("Send alert to operations channel")
                                .timeout(Duration.ofSeconds(30))
                                .build()
                ))
                .timeout(Duration.ofMinutes(1))
                .riskLevel(0.0)
                .rollbackSupported(false)
                .applicableRootCauses(Set.of("UNKNOWN"))
                .build());

        log.info("Initialized {} remediation playbooks", playbookRegistry.size());
    }

    private Playbook selectByRootCauseType(String rootCauseType) {
        // Find playbook that handles this root cause type
        return playbookRegistry.values().stream()
                .filter(p -> p.getApplicableRootCauses().contains(rootCauseType))
                .min(Comparator.comparingDouble(Playbook::getRiskLevel))
                .orElse(playbookRegistry.get("NOTIFY")); // Fallback to notification
    }

    // ========== Data Classes ==========

    @Data
    @Builder
    public static class Playbook {
        private String name;
        private String primaryAction;
        private String description;
        private List<PlaybookStep> steps;
        private Duration timeout;
        private double riskLevel;
        private boolean rollbackSupported;
        private String healthCheckUrl;
        private Set<String> applicableRootCauses;
        @Builder.Default
        private Map<String, String> parameters = new HashMap<>();

        public Map<String, String> getParameters() {
            return parameters != null ? parameters : new HashMap<>();
        }
    }

    @Data
    @Builder
    public static class PlaybookStep {
        private int order;
        private String action;
        private String description;
        private Duration timeout;
        @Builder.Default
        private Map<String, String> parameters = new HashMap<>();
        @Builder.Default
        private boolean optional = false;
    }
}
