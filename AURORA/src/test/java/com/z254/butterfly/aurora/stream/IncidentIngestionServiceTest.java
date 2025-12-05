package com.z254.butterfly.aurora.stream;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.AnomalySignal;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.domain.service.IncidentService;
import com.z254.butterfly.aurora.kafka.RcaHypothesisProducer;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import com.z254.butterfly.aurora.rca.RootCauseAnalyzer;
import com.z254.butterfly.aurora.remediation.AutoRemediator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentIngestionServiceTest {

    @Mock
    private IncidentService incidentService;
    @Mock
    private RootCauseAnalyzer rootCauseAnalyzer;
    @Mock
    private AutoRemediator autoRemediator;
    @Mock
    private RcaHypothesisProducer rcaHypothesisProducer;
    @Mock
    private AuroraStructuredLogger logger;

    private IncidentIngestionService ingestionService;
    private AuroraProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuroraProperties();
        ingestionService = new IncidentIngestionService(incidentService, rootCauseAnalyzer,
                autoRemediator, rcaHypothesisProducer, properties, logger);
    }

    @Test
    void processPublishesHypothesesAndTriggersRemediation() {
        EnrichedAnomalyBatch batch = EnrichedAnomalyBatch.builder()
                .incidentId("INC-test")
                .componentKey("order-service")
                .anomalies(List.of(buildSignal("A1"), buildSignal("A2")))
                .affectedComponents(Set.of("order-service"))
                .maxSeverity(0.9)
                .priority(1)
                .correlationId("corr")
                .build();

        RcaHypothesis hypothesis = RcaHypothesis.builder()
                .hypothesisId("HYP-1")
                .incidentId("INC-test")
                .rootCauseComponent("order-service")
                .confidence(0.8)
                .timestamp(Instant.now())
                .build();

        when(rootCauseAnalyzer.canAnalyze(batch)).thenReturn(true);
        when(rootCauseAnalyzer.analyzeBlocking(batch)).thenReturn(List.of(hypothesis));
        when(autoRemediator.remediate(eq(hypothesis), any())).thenReturn(Mono.empty());

        ingestionService.process(batch);

        verify(incidentService).upsertFromBatch(batch);
        verify(incidentService).attachHypotheses("INC-test", List.of(hypothesis));
        verify(rcaHypothesisProducer).publish(hypothesis);
        verify(autoRemediator).remediate(eq(hypothesis), ArgumentMatchers.any(AuroraProperties.ExecutionMode.class));
    }

    private AnomalySignal buildSignal(String id) {
        return AnomalySignal.builder()
                .anomalyId(id)
                .rimNodeId("order-service")
                .severity(0.9)
                .timestamp(System.currentTimeMillis())
                .anomalyType("LATENCY_SPIKE")
                .build();
    }
}
