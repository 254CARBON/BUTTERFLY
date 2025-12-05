package com.z254.butterfly.aurora.stream;

import com.z254.butterfly.aurora.config.AuroraProperties;
import com.z254.butterfly.aurora.domain.model.Incident;
import com.z254.butterfly.aurora.domain.model.RcaHypothesis;
import com.z254.butterfly.aurora.domain.service.IncidentService;
import com.z254.butterfly.aurora.observability.AuroraStructuredLogger;
import com.z254.butterfly.aurora.rca.RootCauseAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates the ingestion of enriched anomaly batches into the RCA/remediation pipeline.
 */
@Slf4j
@Component
public class IncidentIngestionService {

    private final IncidentService incidentService;
    private final RootCauseAnalyzer rootCauseAnalyzer;
    private final AuroraProperties auroraProperties;
    private final AuroraStructuredLogger logger;

    public IncidentIngestionService(IncidentService incidentService,
                                    RootCauseAnalyzer rootCauseAnalyzer,
                                    AuroraProperties auroraProperties,
                                    AuroraStructuredLogger logger) {
        this.incidentService = incidentService;
        this.rootCauseAnalyzer = rootCauseAnalyzer;
        this.auroraProperties = auroraProperties;
        this.logger = logger;
    }

    public void process(EnrichedAnomalyBatch batch) {
        if (batch == null) {
            return;
        }

        try {
            Incident incident = incidentService.upsertFromBatch(batch);

            if (!rootCauseAnalyzer.canAnalyze(batch)) {
                log.debug("Batch for incident {} does not meet RCA criteria", batch.getIncidentId());
                return;
            }

            List<RcaHypothesis> hypotheses = rootCauseAnalyzer.analyzeBlocking(batch);
            incidentService.attachHypotheses(incident.getId(), hypotheses);

            logger.logIncidentEvent(incident.getId(),
                    AuroraStructuredLogger.IncidentEventType.RCA_COMPLETED,
                    "RCA completed via stream pipeline",
                    null);
        } catch (Exception ex) {
            log.error("Failed to process enriched anomaly batch {}: {}",
                    batch.getIncidentId(), ex.getMessage(), ex);
        }
    }
}
