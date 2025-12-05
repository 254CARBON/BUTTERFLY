package com.z254.butterfly.aurora.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

/**
 * Incident entity representing a group of correlated anomalies.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    /** Unique incident identifier */
    private String id;

    /** Incident title/summary */
    private String title;

    /** Detailed description */
    private String description;

    /** Current status */
    @Builder.Default
    private IncidentStatus status = IncidentStatus.OPEN;

    /** Priority level (1 = critical, 5 = low) */
    private int priority;

    /** Severity score (0.0 to 1.0) */
    private double severity;

    /** Primary affected component */
    private String primaryComponent;

    /** All affected components */
    @Builder.Default
    private Set<String> affectedComponents = new HashSet<>();

    /** Related anomaly IDs */
    @Builder.Default
    private List<Object> relatedAnomalies = new ArrayList<>();

    /** RCA hypotheses generated */
    @Builder.Default
    private List<RcaHypothesis> hypotheses = new ArrayList<>();

    /** Remediation history */
    @Builder.Default
    private List<RemediationRecord> remediationHistory = new ArrayList<>();

    /** Incident timeline */
    @Builder.Default
    private List<TimelineEvent> timeline = new ArrayList<>();

    /** Creation timestamp */
    private Instant createdAt;

    /** Last update timestamp */
    private Instant updatedAt;

    /** Resolution timestamp */
    private Instant resolvedAt;

    /** Mean time to resolution (ms) */
    private Long mttrMs;

    /** Assigned team/user */
    private String assignee;

    /** Correlation ID for tracing */
    private String correlationId;

    /** Tenant identifier */
    private String tenantId;

    /** Tags for categorization */
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    /** Additional metadata */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();

    /**
     * Mark incident as resolved.
     */
    public void resolve(String resolution) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
        
        if (createdAt != null) {
            this.mttrMs = java.time.Duration.between(createdAt, resolvedAt).toMillis();
        }
        
        addTimelineEvent(TimelineEventType.RESOLVED, resolution);
    }

    /**
     * Add a timeline event.
     */
    public void addTimelineEvent(TimelineEventType type, String description) {
        timeline.add(TimelineEvent.builder()
                .timestamp(Instant.now())
                .type(type)
                .description(description)
                .build());
        this.updatedAt = Instant.now();
    }

    /**
     * Add a remediation record.
     */
    public void addRemediationRecord(RemediationRecord record) {
        remediationHistory.add(record);
        addTimelineEvent(TimelineEventType.REMEDIATION_ATTEMPTED, 
                "Remediation attempted: " + record.getActionType());
        this.updatedAt = Instant.now();
    }

    /**
     * Check if incident is still active.
     */
    public boolean isActive() {
        return status == IncidentStatus.OPEN || 
               status == IncidentStatus.INVESTIGATING ||
               status == IncidentStatus.REMEDIATING;
    }

    /**
     * Incident status enum.
     */
    public enum IncidentStatus {
        OPEN,
        INVESTIGATING,
        REMEDIATING,
        RESOLVED,
        CLOSED,
        ESCALATED
    }

    /**
     * Timeline event types.
     */
    public enum TimelineEventType {
        CREATED,
        UPDATED,
        RCA_STARTED,
        RCA_COMPLETED,
        HYPOTHESIS_GENERATED,
        REMEDIATION_ATTEMPTED,
        REMEDIATION_COMPLETED,
        REMEDIATION_FAILED,
        ROLLBACK_INITIATED,
        ESCALATED,
        RESOLVED,
        CLOSED,
        COMMENT
    }

    /**
     * Timeline event record.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEvent {
        private Instant timestamp;
        private TimelineEventType type;
        private String description;
        private String actor;
        private Map<String, String> details;
    }

    /**
     * Remediation record.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemediationRecord {
        private String remediationId;
        private String hypothesisId;
        private String actionType;
        private String targetComponent;
        private Instant startedAt;
        private Instant completedAt;
        private boolean success;
        private String result;
        private boolean rolledBack;
    }
}
