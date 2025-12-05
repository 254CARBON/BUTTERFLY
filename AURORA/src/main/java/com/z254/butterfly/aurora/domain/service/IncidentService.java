package com.z254.butterfly.aurora.domain.service;

import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.domain.repository.IncidentRepository;
import com.z254.butterfly.aurora.health.AuroraHealthIndicator;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.remediation.AutoRemediator;
import com.z254.butterfly.aurora.stream.EnrichedAnomalyBatch;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central service for managing incident lifecycle state.
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AuroraMetrics metrics;
    private final AuroraHealthIndicator healthIndicator;

    public IncidentService(IncidentRepository incidentRepository,
                           AuroraMetrics metrics,
                           AuroraHealthIndicator healthIndicator) {
        this.incidentRepository = incidentRepository;
        this.metrics = metrics;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Create or update an incident based on the latest anomaly batch.
     */
    public Incident upsertFromBatch(EnrichedAnomalyBatch batch) {
        Instant now = Instant.now();
        Incident incident = incidentRepository.findById(batch.getIncidentId())
                .orElseGet(() -> initializeIncident(batch, now));

        incident.setUpdatedAt(now);
        incident.setSeverity(Math.max(incident.getSeverity(), batch.getMaxSeverity()));
        incident.setPriority(batch.getPriority());
        incident.setPrimaryComponent(batch.getComponentKey());
        incident.setCorrelationId(batch.getCorrelationId());
        incident.setTenantId(batch.getTenantId());

        incident.getAffectedComponents().addAll(batch.getAffectedComponents());
        incident.getRelatedAnomalies().addAll(batch.getAnomalies());

        incident.addTimelineEvent(Incident.TimelineEventType.UPDATED,
                "Anomaly batch ingested (" + batch.getAnomalyCount() + " signals)");

        incidentRepository.save(incident);
        return incident;
    }

    /**
     * Attach hypotheses to an incident and update metadata.
     */
    public List<RcaHypothesis> attachHypotheses(String incidentId, List<RcaHypothesis> hypotheses) {
        if (hypotheses == null || hypotheses.isEmpty()) {
            return Collections.emptyList();
        }

        incidentRepository.findById(incidentId).ifPresent(incident -> {
            incident.getHypotheses().clear();
            incident.getHypotheses().addAll(hypotheses);
            incident.addTimelineEvent(Incident.TimelineEventType.RCA_COMPLETED,
                    "Generated " + hypotheses.size() + " hypotheses");
            incidentRepository.save(incident);
        });

        return hypotheses;
    }

    /**
     * Record remediation history for the given incident.
     */
    public void recordRemediationResult(String incidentId,
                                        AutoRemediator.RemediationResult result) {
        incidentRepository.findById(incidentId).ifPresent(incident -> {
            Incident.RemediationRecord record = Incident.RemediationRecord.builder()
                    .remediationId(result.getRemediationId())
                    .hypothesisId(result.getSafetyResult() != null ?
                            result.getSafetyResult().getReason() : null)
                    .actionType(result.getDetails() != null ?
                            String.valueOf(result.getDetails().getOrDefault("action", "")) : null)
                    .startedAt(result.getTimestamp())
                    .completedAt(result.getTimestamp())
                    .success(result.getStatus() == AutoRemediator.RemediationStatus.COMPLETED)
                    .result(result.getReason())
                    .rolledBack(result.getStatus() == AutoRemediator.RemediationStatus.ROLLED_BACK)
                    .build();
            incident.addRemediationRecord(record);
            incidentRepository.save(incident);
        });
    }

    /**
     * Resolve an incident and update MTTR metrics.
     */
    public Optional<Incident> resolveIncident(String incidentId, String resolution) {
        return incidentRepository.findById(incidentId)
                .map(incident -> {
                    incident.resolve(resolution);
                    incidentRepository.save(incident);

                    Duration mttr = incident.getMttrMs() != null ?
                            Duration.ofMillis(incident.getMttrMs()) : Duration.ZERO;
                    metrics.recordIncidentResolved(mttr);
                    healthIndicator.decrementActiveIncidents();
                    return incident;
                });
    }

    public Optional<Incident> escalateIncident(String incidentId, String reason) {
        return incidentRepository.findById(incidentId)
                .map(incident -> {
                    incident.setStatus(Incident.IncidentStatus.ESCALATED);
                    incident.addTimelineEvent(Incident.TimelineEventType.ESCALATED, reason);
                    incidentRepository.save(incident);
                    metrics.recordIncidentEscalated();
                    return incident;
                });
    }

    public Optional<Incident> addComment(String incidentId, String comment) {
        return incidentRepository.findById(incidentId)
                .map(incident -> {
                    incident.addTimelineEvent(Incident.TimelineEventType.COMMENT, comment);
                    incidentRepository.save(incident);
                    return incident;
                });
    }

    public Optional<Incident> getIncident(String incidentId) {
        return incidentRepository.findById(incidentId);
    }

    public List<Incident> listIncidents(String status, String component, Integer priority) {
        return incidentRepository.findAll().stream()
                .filter(incident -> status == null || incident.getStatus().name().equalsIgnoreCase(status))
                .filter(incident -> component == null || matchesComponent(incident, component))
                .filter(incident -> priority == null || incident.getPriority() == priority)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public Set<String> getKnownIncidentIds() {
        return incidentRepository.findAll().stream()
                .map(Incident::getId)
                .collect(Collectors.toSet());
    }

    private boolean matchesComponent(Incident incident, String component) {
        return (incident.getPrimaryComponent() != null &&
                incident.getPrimaryComponent().contains(component)) ||
                incident.getAffectedComponents().stream()
                        .anyMatch(c -> c != null && c.contains(component));
    }

    private Incident initializeIncident(EnrichedAnomalyBatch batch, Instant createdAt) {
        Incident incident = Incident.builder()
                .id(batch.getIncidentId())
                .title("Incident on " + batch.getComponentKey())
                .description("Auto-generated incident from anomaly stream")
                .status(Incident.IncidentStatus.INVESTIGATING)
                .priority(batch.getPriority())
                .severity(batch.getMaxSeverity())
                .primaryComponent(batch.getComponentKey())
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .correlationId(batch.getCorrelationId())
                .tenantId(batch.getTenantId())
                .relatedAnomalies(new ArrayList<>(batch.getAnomalies()))
                .affectedComponents(batch.getAffectedComponents())
                .build();
        incident.addTimelineEvent(Incident.TimelineEventType.CREATED, "Incident created from anomaly stream");
        metrics.recordIncidentCreated();
        healthIndicator.incrementActiveIncidents();
        return incident;
    }
}
