package com.z254.butterfly.aurora.immunity;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.remediation.AutoRemediator;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Extracts resilience patterns from incidents and remediations.
 * <p>
 * Pattern types include:
 * <ul>
 *     <li>Retry escalation patterns</li>
 *     <li>Circuit breaker thresholds</li>
 *     <li>Timeout tuning recommendations</li>
 *     <li>Scaling triggers</li>
 *     <li>Graceful degradation sequences</li>
 * </ul>
 */
@Slf4j
@Component
public class ResiliencePatternLearner {

    private final AuroraProperties auroraProperties;

    // Historical patterns for reference
    private final Map<String, List<ResiliencePattern>> patternHistory = new HashMap<>();

    public ResiliencePatternLearner(AuroraProperties auroraProperties) {
        this.auroraProperties = auroraProperties;
    }

    /**
     * Extract resilience pattern from an incident and its remediation.
     */
    public ResiliencePattern extractPattern(Incident incident,
                                             AutoRemediator.RemediationResult remediationResult) {
        log.debug("Extracting pattern from incident: {}", incident.getId());

        // Determine pattern type based on root cause and remediation
        PatternType patternType = determinePatternType(incident, remediationResult);

        // Extract pattern-specific parameters
        Map<String, String> parameters = extractPatternParameters(incident, 
                remediationResult, patternType);

        // Calculate confidence based on historical effectiveness
        double confidence = calculatePatternConfidence(incident, patternType);

        // Determine trigger condition
        String triggerCondition = determineTriggerCondition(incident, patternType);

        // Build recommended action
        String recommendedAction = determineRecommendedAction(remediationResult, patternType);

        ResiliencePattern pattern = ResiliencePattern.builder()
                .patternId(generatePatternId(incident, patternType))
                .patternType(patternType)
                .sourceIncidentId(incident.getId())
                .component(incident.getPrimaryComponent())
                .anomalyType(extractDominantAnomalyType(incident))
                .triggerCondition(triggerCondition)
                .recommendedAction(recommendedAction)
                .parameters(parameters)
                .confidence(confidence)
                .mttrMs(incident.getMttrMs() != null ? incident.getMttrMs() : 0)
                .extractedAt(Instant.now())
                .build();

        // Store in history for future reference
        patternHistory.computeIfAbsent(incident.getPrimaryComponent(), k -> new ArrayList<>())
                .add(pattern);

        return pattern;
    }

    /**
     * Find similar patterns from history.
     */
    public List<ResiliencePattern> findSimilarPatterns(String component, PatternType type) {
        return patternHistory.getOrDefault(component, Collections.emptyList()).stream()
                .filter(p -> p.getPatternType() == type)
                .sorted(Comparator.comparingDouble(ResiliencePattern::getConfidence).reversed())
                .limit(5)
                .toList();
    }

    // ========== Private Methods ==========

    private PatternType determinePatternType(Incident incident,
                                              AutoRemediator.RemediationResult result) {
        // Determine based on successful remediation type
        String remediationAction = extractRemediationAction(incident);

        return switch (remediationAction) {
            case "RESTART", "SCALE_UP" -> PatternType.SCALING_TRIGGER;
            case "CIRCUIT_BREAK" -> PatternType.CIRCUIT_BREAKER_THRESHOLD;
            case "RETRY" -> PatternType.RETRY_ESCALATION;
            case "FAILOVER", "TRAFFIC_REDIRECT" -> PatternType.FAILOVER_SEQUENCE;
            case "THROTTLE" -> PatternType.RATE_LIMIT_TUNING;
            case "CACHE_CLEAR" -> PatternType.GRACEFUL_DEGRADATION;
            default -> PatternType.CUSTOM;
        };
    }

    private Map<String, String> extractPatternParameters(Incident incident,
                                                          AutoRemediator.RemediationResult result,
                                                          PatternType patternType) {
        Map<String, String> params = new HashMap<>();

        switch (patternType) {
            case CIRCUIT_BREAKER_THRESHOLD -> {
                params.put("failure_threshold", "50");
                params.put("slow_call_threshold", "100");
                params.put("wait_duration_seconds", "30");
            }
            case RETRY_ESCALATION -> {
                params.put("max_retries", "3");
                params.put("initial_backoff_ms", "100");
                params.put("max_backoff_ms", "5000");
                params.put("multiplier", "2.0");
            }
            case TIMEOUT_TUNING -> {
                params.put("connect_timeout_ms", "1000");
                params.put("read_timeout_ms", "5000");
                params.put("write_timeout_ms", "3000");
            }
            case SCALING_TRIGGER -> {
                params.put("scale_up_threshold_cpu", "80");
                params.put("scale_down_threshold_cpu", "30");
                params.put("cooldown_seconds", "300");
                params.put("min_instances", "2");
                params.put("max_instances", "10");
            }
            case FAILOVER_SEQUENCE -> {
                params.put("health_check_interval_ms", "10000");
                params.put("unhealthy_threshold", "3");
                params.put("failover_timeout_ms", "30000");
            }
            case RATE_LIMIT_TUNING -> {
                params.put("requests_per_second", "1000");
                params.put("burst_size", "100");
                params.put("queue_size", "500");
            }
            case BULKHEAD_CONFIG -> {
                params.put("max_concurrent_calls", "25");
                params.put("max_wait_duration_ms", "100");
            }
            case GRACEFUL_DEGRADATION -> {
                params.put("degradation_threshold", "0.7");
                params.put("feature_flags", "non-critical-features");
            }
            default -> {
                // Custom pattern - extract from incident metadata
            }
        }

        // Add incident-specific metrics
        if (incident.getMttrMs() != null) {
            params.put("learned_mttr_ms", String.valueOf(incident.getMttrMs()));
        }
        params.put("severity", String.valueOf(incident.getSeverity()));

        return params;
    }

    private double calculatePatternConfidence(Incident incident, PatternType patternType) {
        // Base confidence from incident resolution
        double baseConfidence = 0.5;

        // Boost if we've seen similar patterns before
        List<ResiliencePattern> similar = findSimilarPatterns(
                incident.getPrimaryComponent(), patternType);
        if (!similar.isEmpty()) {
            double avgHistoricalConfidence = similar.stream()
                    .mapToDouble(ResiliencePattern::getConfidence)
                    .average()
                    .orElse(0.5);
            baseConfidence = (baseConfidence + avgHistoricalConfidence) / 2;
        }

        // Adjust based on MTTR (faster resolution = higher confidence)
        if (incident.getMttrMs() != null && incident.getMttrMs() < 300000) { // < 5 min
            baseConfidence += 0.1;
        }

        // Adjust based on incident severity (higher severity resolved = higher confidence)
        baseConfidence += incident.getSeverity() * 0.2;

        return Math.min(baseConfidence, 1.0);
    }

    private String determineTriggerCondition(Incident incident, PatternType patternType) {
        String anomalyType = extractDominantAnomalyType(incident);

        return switch (patternType) {
            case CIRCUIT_BREAKER_THRESHOLD -> 
                    String.format("anomaly_type == '%s' && error_rate > 50", anomalyType);
            case RETRY_ESCALATION -> 
                    String.format("anomaly_type == '%s' && timeout_count > 10", anomalyType);
            case TIMEOUT_TUNING -> 
                    String.format("anomaly_type == '%s' && p99_latency > 5000", anomalyType);
            case SCALING_TRIGGER -> 
                    String.format("anomaly_type == '%s' && cpu_utilization > 80", anomalyType);
            case FAILOVER_SEQUENCE -> 
                    String.format("anomaly_type == '%s' && health_check_failures > 3", anomalyType);
            case RATE_LIMIT_TUNING -> 
                    String.format("anomaly_type == '%s' && request_rate > threshold", anomalyType);
            default -> String.format("anomaly_type == '%s'", anomalyType);
        };
    }

    private String determineRecommendedAction(AutoRemediator.RemediationResult result,
                                               PatternType patternType) {
        return switch (patternType) {
            case CIRCUIT_BREAKER_THRESHOLD -> "CIRCUIT_BREAK";
            case RETRY_ESCALATION -> "ADJUST_RETRY_CONFIG";
            case TIMEOUT_TUNING -> "ADJUST_TIMEOUT_CONFIG";
            case SCALING_TRIGGER -> "SCALE_UP";
            case FAILOVER_SEQUENCE -> "FAILOVER";
            case RATE_LIMIT_TUNING -> "THROTTLE";
            case BULKHEAD_CONFIG -> "ADJUST_BULKHEAD";
            case GRACEFUL_DEGRADATION -> "ENABLE_DEGRADED_MODE";
            default -> "NOTIFY";
        };
    }

    private String extractDominantAnomalyType(Incident incident) {
        // Extract from hypotheses or incident metadata
        if (!incident.getHypotheses().isEmpty()) {
            return incident.getHypotheses().get(0).getRootCauseType();
        }
        return "UNKNOWN";
    }

    private String extractRemediationAction(Incident incident) {
        if (!incident.getRemediationHistory().isEmpty()) {
            Incident.RemediationRecord lastRemediation = incident.getRemediationHistory()
                    .get(incident.getRemediationHistory().size() - 1);
            if (lastRemediation.isSuccess()) {
                return lastRemediation.getActionType();
            }
        }
        return "UNKNOWN";
    }

    private String generatePatternId(Incident incident, PatternType type) {
        return String.format("PAT-%s-%s-%d",
                type.name().substring(0, 3),
                incident.getPrimaryComponent().hashCode(),
                System.currentTimeMillis() % 10000);
    }

    // ========== Data Classes ==========

    public enum PatternType {
        RETRY_ESCALATION,
        CIRCUIT_BREAKER_THRESHOLD,
        TIMEOUT_TUNING,
        SCALING_TRIGGER,
        FAILOVER_SEQUENCE,
        LOAD_SHEDDING,
        GRACEFUL_DEGRADATION,
        BULKHEAD_CONFIG,
        RATE_LIMIT_TUNING,
        CUSTOM
    }

    @Data
    @Builder
    public static class ResiliencePattern {
        private String patternId;
        private PatternType patternType;
        private String sourceIncidentId;
        private String component;
        private String anomalyType;
        private String triggerCondition;
        private String recommendedAction;
        private Map<String, String> parameters;
        private double confidence;
        private long mttrMs;
        private Instant extractedAt;
    }
}
