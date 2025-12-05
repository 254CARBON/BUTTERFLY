package com.z254.butterfly.aurora.observability;

import io.micrometer.core.instrument.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized metrics for AURORA service.
 * <p>
 * Provides metrics for:
 * <ul>
 *     <li>Incident lifecycle (created, resolved, MTTR)</li>
 *     <li>RCA performance (latency, confidence, accuracy)</li>
 *     <li>Remediation execution (success rate, duration)</li>
 *     <li>Immunity rules (applied, effectiveness)</li>
 * </ul>
 */
@Component
public class AuroraMetrics {

    private final MeterRegistry meterRegistry;

    // Incident metrics
    @Getter
    private final Counter incidentsCreated;
    @Getter
    private final Counter incidentsResolved;
    @Getter
    private final Counter incidentsEscalated;
    private final Timer incidentMttr;
    private final AtomicInteger activeIncidents;

    // RCA metrics
    @Getter
    private final Counter rcaAnalysisStarted;
    @Getter
    private final Counter rcaAnalysisCompleted;
    @Getter
    private final Counter rcaAnalysisFailed;
    private final Timer rcaLatency;
    private final DistributionSummary rcaConfidence;
    private final DistributionSummary rcaHypothesesGenerated;

    // Remediation metrics
    @Getter
    private final Counter remediationsStarted;
    @Getter
    private final Counter remediationsCompleted;
    @Getter
    private final Counter remediationsFailed;
    @Getter
    private final Counter remediationsRolledBack;
    @Getter
    private final Counter remediationsSafetyBlocked;
    private final Timer remediationDuration;
    private final AtomicInteger activeRemediations;
    private final Map<String, Counter> remediationsByType = new ConcurrentHashMap<>();

    // Immunity metrics
    @Getter
    private final Counter immunityRulesCreated;
    @Getter
    private final Counter immunityRulesApplied;
    private final DistributionSummary ruleEffectiveness;

    // Stream processing metrics
    @Getter
    private final Counter anomaliesProcessed;
    @Getter
    private final Counter anomaliesEnriched;
    @Getter
    private final Counter streamProcessingErrors;
    private final Timer streamProcessingLatency;

    public AuroraMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize incident metrics
        this.incidentsCreated = Counter.builder("aurora.incidents.created")
                .description("Total incidents created")
                .register(meterRegistry);
        this.incidentsResolved = Counter.builder("aurora.incidents.resolved")
                .description("Total incidents resolved")
                .register(meterRegistry);
        this.incidentsEscalated = Counter.builder("aurora.incidents.escalated")
                .description("Total incidents escalated")
                .register(meterRegistry);
        this.incidentMttr = Timer.builder("aurora.incidents.mttr")
                .description("Mean time to resolution")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry);
        this.activeIncidents = meterRegistry.gauge("aurora.incidents.active", new AtomicInteger(0));

        // Initialize RCA metrics
        this.rcaAnalysisStarted = Counter.builder("aurora.rca.analysis.started")
                .description("RCA analyses started")
                .register(meterRegistry);
        this.rcaAnalysisCompleted = Counter.builder("aurora.rca.analysis.completed")
                .description("RCA analyses completed successfully")
                .register(meterRegistry);
        this.rcaAnalysisFailed = Counter.builder("aurora.rca.analysis.failed")
                .description("RCA analyses failed")
                .register(meterRegistry);
        this.rcaLatency = Timer.builder("aurora.rca.latency")
                .description("RCA analysis latency")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry);
        this.rcaConfidence = DistributionSummary.builder("aurora.rca.confidence")
                .description("RCA hypothesis confidence scores")
                .publishPercentiles(0.5, 0.75, 0.95)
                .register(meterRegistry);
        this.rcaHypothesesGenerated = DistributionSummary.builder("aurora.rca.hypotheses.count")
                .description("Number of hypotheses generated per analysis")
                .register(meterRegistry);

        // Initialize remediation metrics
        this.remediationsStarted = Counter.builder("aurora.remediations.started")
                .description("Remediations started")
                .register(meterRegistry);
        this.remediationsCompleted = Counter.builder("aurora.remediations.completed")
                .description("Remediations completed successfully")
                .register(meterRegistry);
        this.remediationsFailed = Counter.builder("aurora.remediations.failed")
                .description("Remediations failed")
                .register(meterRegistry);
        this.remediationsRolledBack = Counter.builder("aurora.remediations.rolled_back")
                .description("Remediations rolled back")
                .register(meterRegistry);
        this.remediationsSafetyBlocked = Counter.builder("aurora.remediations.safety_blocked")
                .description("Remediations blocked by safety checks")
                .register(meterRegistry);
        this.remediationDuration = Timer.builder("aurora.remediations.duration")
                .description("Remediation execution duration")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry);
        this.activeRemediations = meterRegistry.gauge("aurora.remediations.active", new AtomicInteger(0));

        // Initialize immunity metrics
        this.immunityRulesCreated = Counter.builder("aurora.immunity.rules.created")
                .description("Immunity rules created")
                .register(meterRegistry);
        this.immunityRulesApplied = Counter.builder("aurora.immunity.rules.applied")
                .description("Immunity rules applied")
                .register(meterRegistry);
        this.ruleEffectiveness = DistributionSummary.builder("aurora.immunity.effectiveness")
                .description("Immunity rule effectiveness scores")
                .publishPercentiles(0.5, 0.75, 0.95)
                .register(meterRegistry);

        // Initialize stream processing metrics
        this.anomaliesProcessed = Counter.builder("aurora.stream.anomalies.processed")
                .description("Anomalies processed by stream")
                .register(meterRegistry);
        this.anomaliesEnriched = Counter.builder("aurora.stream.anomalies.enriched")
                .description("Anomalies enriched")
                .register(meterRegistry);
        this.streamProcessingErrors = Counter.builder("aurora.stream.errors")
                .description("Stream processing errors")
                .register(meterRegistry);
        this.streamProcessingLatency = Timer.builder("aurora.stream.latency")
                .description("Stream processing latency")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry);
    }

    // ========== Incident Methods ==========

    public void recordIncidentCreated() {
        incidentsCreated.increment();
        activeIncidents.incrementAndGet();
    }

    public void recordIncidentResolved(Duration mttr) {
        incidentsResolved.increment();
        activeIncidents.decrementAndGet();
        incidentMttr.record(mttr);
    }

    public void recordIncidentEscalated() {
        incidentsEscalated.increment();
    }

    // ========== RCA Methods ==========

    public Timer.Sample startRcaTimer() {
        rcaAnalysisStarted.increment();
        return Timer.start(meterRegistry);
    }

    public void recordRcaCompleted(Timer.Sample sample, int hypothesesCount, double topConfidence) {
        sample.stop(rcaLatency);
        rcaAnalysisCompleted.increment();
        rcaHypothesesGenerated.record(hypothesesCount);
        rcaConfidence.record(topConfidence);
    }

    public void recordRcaFailed(Timer.Sample sample) {
        sample.stop(rcaLatency);
        rcaAnalysisFailed.increment();
    }

    // ========== Remediation Methods ==========

    public Timer.Sample startRemediationTimer() {
        remediationsStarted.increment();
        activeRemediations.incrementAndGet();
        return Timer.start(meterRegistry);
    }

    public void recordRemediationCompleted(Timer.Sample sample, String actionType) {
        sample.stop(remediationDuration);
        remediationsCompleted.increment();
        activeRemediations.decrementAndGet();
        getRemediationCounterByType(actionType).increment();
    }

    public void recordRemediationFailed(Timer.Sample sample) {
        sample.stop(remediationDuration);
        remediationsFailed.increment();
        activeRemediations.decrementAndGet();
    }

    public void recordRemediationRolledBack() {
        remediationsRolledBack.increment();
    }

    public void recordRemediationSafetyBlocked(String reason) {
        remediationsSafetyBlocked.increment();
        Counter.builder("aurora.remediations.safety_blocked.by_reason")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private Counter getRemediationCounterByType(String actionType) {
        return remediationsByType.computeIfAbsent(actionType, type ->
                Counter.builder("aurora.remediations.by_type")
                        .tag("action_type", type)
                        .description("Remediations by action type")
                        .register(meterRegistry));
    }

    // ========== Immunity Methods ==========

    public void recordImmunityRuleCreated() {
        immunityRulesCreated.increment();
    }

    public void recordImmunityRuleApplied(double effectiveness) {
        immunityRulesApplied.increment();
        ruleEffectiveness.record(effectiveness);
    }

    // ========== Stream Processing Methods ==========

    public void recordAnomalyProcessed() {
        anomaliesProcessed.increment();
    }

    public void recordAnomalyEnriched() {
        anomaliesEnriched.increment();
    }

    public void recordStreamError() {
        streamProcessingErrors.increment();
    }

    public Timer.Sample startStreamTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordStreamLatency(Timer.Sample sample) {
        sample.stop(streamProcessingLatency);
    }
}
