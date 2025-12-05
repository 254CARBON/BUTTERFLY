package com.z254.butterfly.aurora.domain.service;

import com.z254.butterfly.aurora.domain.model.AnomalySignal;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.domain.repository.InMemoryIncidentRepository;
import com.z254.butterfly.aurora.health.AuroraHealthIndicator;
import com.z254.butterfly.aurora.observability.AuroraMetrics;
import com.z254.butterfly.aurora.stream.EnrichedAnomalyBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private AuroraMetrics metrics;

    @Mock
    private AuroraHealthIndicator healthIndicator;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(new InMemoryIncidentRepository(), metrics, healthIndicator);
    }

    @Test
    void upsertFromBatchCreatesIncident() {
        EnrichedAnomalyBatch batch = EnrichedAnomalyBatch.builder()
                .incidentId("INC-123")
                .componentKey("order-service")
                .anomalies(List.of(buildSignal("A-1")))
                .affectedComponents(Set.of("order-service"))
                .maxSeverity(0.8)
                .priority(2)
                .correlationId("corr-1")
                .build();

        Incident incident = incidentService.upsertFromBatch(batch);

        assertThat(incident.getId()).isEqualTo("INC-123");
        assertThat(incident.getSeverity()).isEqualTo(0.8);
        assertThat(incident.getRelatedAnomalies()).hasSize(1);
        verify(metrics).recordIncidentCreated();
        verify(healthIndicator).incrementActiveIncidents();
    }

    @Test
    void attachHypothesesStoresOnIncident() {
        Incident incident = incidentService.upsertFromBatch(EnrichedAnomalyBatch.builder()
                .incidentId("INC-attach")
                .componentKey("payment-service")
                .anomalies(List.of(buildSignal("A-2")))
                .affectedComponents(Set.of("payment-service"))
                .priority(3)
                .maxSeverity(0.5)
                .correlationId("corr-2")
                .build());

        RcaHypothesis hypothesis = RcaHypothesis.builder()
                .hypothesisId("HYP-1")
                .incidentId(incident.getId())
                .rootCauseComponent("payment-service")
                .confidence(0.8)
                .timestamp(Instant.now())
                .build();

        incidentService.attachHypotheses(incident.getId(), List.of(hypothesis));

        Incident stored = incidentService.getIncident(incident.getId()).orElseThrow();
        assertThat(stored.getHypotheses()).hasSize(1);
        assertThat(stored.getHypotheses().get(0).getHypothesisId()).isEqualTo("HYP-1");
    }

    private AnomalySignal buildSignal(String anomalyId) {
        return AnomalySignal.builder()
                .anomalyId(anomalyId)
                .rimNodeId("order-service")
                .timestamp(System.currentTimeMillis())
                .anomalyType("LATENCY_SPIKE")
                .severity(0.8)
                .build();
    }
}
