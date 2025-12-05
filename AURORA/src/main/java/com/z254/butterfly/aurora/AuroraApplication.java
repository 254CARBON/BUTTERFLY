package com.z254.butterfly.aurora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AURORA - Self-Healing Engine for the BUTTERFLY Ecosystem.
 * 
 * <p>AURORA provides:
 * <ul>
 *   <li>Root Cause Analysis (RCA) - Causal reasoning from correlated anomalies</li>
 *   <li>Auto-Remediation - Governed, automated healing actions</li>
 *   <li>Chaos Immunization - Learning from incidents to prevent recurrence</li>
 *   <li>Safety Controls - Blast-radius limits, pre-checks, rollback management</li>
 * </ul>
 * 
 * <p>AURORA integrates with:
 * <ul>
 *   <li>PERCEPTION - Consumes anomaly events via Kafka Streams</li>
 *   <li>PLATO - For governance approval of remediation plans</li>
 *   <li>SYNAPSE - For executing healing actions via connectors</li>
 *   <li>CAPSULE - For storing evidence and chaos learnings</li>
 *   <li>NEXUS - For cross-system incident correlation</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class AuroraApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuroraApplication.class, args);
    }
}
