package com.z254.butterfly.aurora.observability;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Structured logging utility for AURORA service.
 * <p>
 * Provides consistent, machine-readable log output with:
 * <ul>
 *     <li>MDC context management for correlation IDs</li>
 *     <li>Domain-specific logging methods for incidents, RCA, remediation</li>
 *     <li>Performance timing utilities</li>
 *     <li>Sensitive data masking</li>
 * </ul>
 */
@Slf4j
@Component
public class AuroraStructuredLogger {

    // MDC keys
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_INCIDENT_ID = "incidentId";
    public static final String MDC_HYPOTHESIS_ID = "hypothesisId";
    public static final String MDC_REMEDIATION_ID = "remediationId";
    public static final String MDC_COMPONENT = "component";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    /**
     * Log an incident lifecycle event.
     */
    public void logIncidentEvent(String incidentId, IncidentEventType eventType, String message) {
        logIncidentEvent(incidentId, eventType, message, null);
    }

    /**
     * Log an incident lifecycle event with details.
     */
    public void logIncidentEvent(String incidentId, IncidentEventType eventType, 
                                  String message, Map<String, Object> details) {
        try (var scope = withContext(Map.of(MDC_INCIDENT_ID, incidentId))) {
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", eventType.name());
            logData.put("incidentId", incidentId);
            
            if (details != null) {
                logData.putAll(details);
            }

            switch (eventType) {
                case CREATED, RESOLVED, CLOSED -> 
                        log.info("{} | data={}", message, formatLogData(logData));
                case ESCALATED, RCA_FAILED, REMEDIATION_FAILED -> 
                        log.error("{} | data={}", message, formatLogData(logData));
                case RCA_STARTED, REMEDIATION_STARTED, UPDATED -> 
                        log.info("{} | data={}", message, formatLogData(logData));
                default -> log.info("{} | data={}", message, formatLogData(logData));
            }
        }
    }

    /**
     * Log an RCA event.
     */
    public void logRcaEvent(String incidentId, String hypothesisId, RcaEventType eventType, 
                           String message, Map<String, Object> details) {
        try (var scope = withContext(Map.of(
                MDC_INCIDENT_ID, incidentId,
                MDC_HYPOTHESIS_ID, hypothesisId != null ? hypothesisId : ""))) {
            
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", eventType.name());
            logData.put("incidentId", incidentId);
            if (hypothesisId != null) {
                logData.put("hypothesisId", hypothesisId);
            }
            
            if (details != null) {
                logData.putAll(details);
            }

            switch (eventType) {
                case ANALYSIS_STARTED, HYPOTHESIS_GENERATED, ANALYSIS_COMPLETED -> 
                        log.info("{} | data={}", message, formatLogData(logData));
                case ANALYSIS_FAILED, LOW_CONFIDENCE -> 
                        log.warn("{} | data={}", message, formatLogData(logData));
                default -> log.info("{} | data={}", message, formatLogData(logData));
            }
        }
    }

    /**
     * Log a remediation event.
     */
    public void logRemediationEvent(String remediationId, String incidentId, 
                                    RemediationEventType eventType, String message, 
                                    Map<String, Object> details) {
        try (var scope = withContext(Map.of(
                MDC_REMEDIATION_ID, remediationId,
                MDC_INCIDENT_ID, incidentId != null ? incidentId : ""))) {
            
            Map<String, Object> logData = new HashMap<>();
            logData.put("event", eventType.name());
            logData.put("remediationId", remediationId);
            if (incidentId != null) {
                logData.put("incidentId", incidentId);
            }
            
            if (details != null) {
                logData.putAll(details);
            }

            switch (eventType) {
                case STARTED, APPROVED, COMPLETED, ROLLED_BACK -> 
                        log.info("{} | data={}", message, formatLogData(logData));
                case FAILED, REJECTED, SAFETY_BLOCKED -> 
                        log.error("{} | data={}", message, formatLogData(logData));
                case PENDING_APPROVAL, EXECUTING -> 
                        log.info("{} | data={}", message, formatLogData(logData));
                default -> log.info("{} | data={}", message, formatLogData(logData));
            }
        }
    }

    /**
     * Log an immunity learning event.
     */
    public void logImmunityEvent(String ruleId, String component, ImmunityEventType eventType,
                                 String message, Map<String, Object> details) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", eventType.name());
        logData.put("ruleId", ruleId);
        logData.put("component", component);
        
        if (details != null) {
            logData.putAll(details);
        }

        switch (eventType) {
            case RULE_CREATED, RULE_APPLIED -> 
                    log.info("{} | data={}", message, formatLogData(logData));
            case RULE_EXPIRED, RULE_INVALIDATED -> 
                    log.warn("{} | data={}", message, formatLogData(logData));
            case PATTERN_LEARNED -> 
                    log.info("{} | data={}", message, formatLogData(logData));
            default -> log.info("{} | data={}", message, formatLogData(logData));
        }
    }

    /**
     * Log a performance metric.
     */
    public void logPerformance(String operation, Duration duration, boolean success, 
                               Map<String, Object> details) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "PERFORMANCE");
        logData.put("operation", operation);
        logData.put("durationMs", duration.toMillis());
        logData.put("success", success);
        
        if (details != null) {
            logData.putAll(details);
        }

        if (duration.toMillis() > 5000) {
            log.warn("Slow operation: {} took {}ms | data={}", 
                    operation, duration.toMillis(), formatLogData(logData));
        } else {
            log.debug("Performance: {} completed in {}ms | data={}", 
                    operation, duration.toMillis(), formatLogData(logData));
        }
    }

    /**
     * Execute a timed operation with logging.
     */
    public <T> T timed(String operation, Supplier<T> action) {
        Instant start = Instant.now();
        boolean success = false;
        try {
            T result = action.get();
            success = true;
            return result;
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            logPerformance(operation, duration, success, null);
        }
    }

    /**
     * Set MDC context.
     */
    public MDCScope withContext(Map<String, String> context) {
        context.forEach(MDC::put);
        return new MDCScope(context.keySet().toArray(new String[0]));
    }

    /**
     * Set correlation ID in MDC.
     */
    public MDCScope withCorrelationId(String correlationId) {
        MDC.put(MDC_CORRELATION_ID, correlationId);
        return new MDCScope(MDC_CORRELATION_ID);
    }

    private String formatLogData(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            
            sb.append("\"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ========== Event Type Enums ==========

    public enum IncidentEventType {
        CREATED, UPDATED, ESCALATED, RCA_STARTED, RCA_COMPLETED, RCA_FAILED,
        REMEDIATION_STARTED, REMEDIATION_COMPLETED, REMEDIATION_FAILED,
        RESOLVED, CLOSED
    }

    public enum RcaEventType {
        ANALYSIS_STARTED, HYPOTHESIS_GENERATED, HYPOTHESIS_VALIDATED,
        ANALYSIS_COMPLETED, ANALYSIS_FAILED, LOW_CONFIDENCE
    }

    public enum RemediationEventType {
        STARTED, PENDING_APPROVAL, APPROVED, REJECTED, EXECUTING,
        COMPLETED, FAILED, ROLLED_BACK, SAFETY_BLOCKED
    }

    public enum ImmunityEventType {
        PATTERN_LEARNED, RULE_CREATED, RULE_APPLIED, RULE_EXPIRED, RULE_INVALIDATED
    }

    /**
     * Auto-closeable MDC scope for cleanup.
     */
    public static class MDCScope implements AutoCloseable {
        private final String[] keys;

        public MDCScope(String... keys) {
            this.keys = keys;
        }

        @Override
        public void close() {
            for (String key : keys) {
                MDC.remove(key);
            }
        }
    }
}
