package com.z254.butterfly.aurora.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for AURORA service.
 * <p>
 * Provides centralized configuration for:
 * <ul>
 *     <li>Kafka topics and Streams configuration</li>
 *     <li>RCA engine parameters</li>
 *     <li>Safety limits and remediation controls</li>
 *     <li>Integration client settings (PLATO, SYNAPSE, CAPSULE)</li>
 * </ul>
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "aurora")
public class AuroraProperties {

    private final Kafka kafka = new Kafka();
    private final Rca rca = new Rca();
    private final Safety safety = new Safety();
    private final Remediation remediation = new Remediation();
    private final Plato plato = new Plato();
    private final Synapse synapse = new Synapse();
    private final Capsule capsule = new Capsule();
    private final Immunity immunity = new Immunity();

    /**
     * Kafka and Kafka Streams configuration.
     */
    @Data
    public static class Kafka {
        @NotBlank
        private String schemaRegistryUrl = "http://localhost:8081";
        
        private final Topics topics = new Topics();
        private final Streams streams = new Streams();

        @Data
        public static class Topics {
            private String anomaliesInput = "aurora.anomalies.detected";
            private String anomaliesEnriched = "aurora.anomalies.enriched";
            private String rcaHypotheses = "aurora.rca.hypotheses";
            private String remediationActions = "aurora.remediation.actions";
            private String remediationResults = "aurora.remediation.results";
            private String chaosLearnings = "aurora.chaos.learnings";
            private String incidentsDlq = "aurora.incidents.dlq";
        }

        @Data
        public static class Streams {
            private String applicationId = "aurora-streams";
            private int numStreamThreads = 3;
            private String processingGuarantee = "exactly_once_v2";
            private Duration commitInterval = Duration.ofSeconds(1);
            private int replicationFactor = 1;
            private Map<String, String> additionalProperties = new HashMap<>();
        }
    }

    /**
     * Root Cause Analysis engine configuration.
     */
    @Data
    public static class Rca {
        /** Time window for correlating anomalies */
        private Duration correlationWindow = Duration.ofMinutes(5);
        
        /** Minimum confidence threshold for RCA hypotheses */
        private double minConfidenceThreshold = 0.6;
        
        /** Maximum number of hypotheses to generate per incident */
        private int maxHypotheses = 5;
        
        /** Grace period for late-arriving anomalies */
        private Duration gracePeriod = Duration.ofSeconds(30);
        
        /** Minimum anomalies required to trigger RCA */
        private int minAnomaliesForRca = 2;
        
        /** Topology distance weight for confidence scoring */
        private double topologyDistanceWeight = 0.3;
        
        /** Timing correlation weight for confidence scoring */
        private double timingCorrelationWeight = 0.4;
        
        /** Symptom similarity weight for confidence scoring */
        private double symptomSimilarityWeight = 0.3;
    }

    /**
     * Safety and governance controls.
     */
    @Data
    public static class Safety {
        /** Maximum concurrent remediations allowed */
        @Positive
        private int maxConcurrentRemediations = 3;
        
        /** Default blast radius limit (affected entities) */
        @Positive
        private int defaultBlastRadiusLimit = 1000;
        
        /** Default to dry-run mode for safety */
        private boolean dryRunDefault = true;
        
        /** Risk threshold for auto-approval (0.0-1.0) */
        private double autoApproveRiskThreshold = 0.3;
        
        /** Require PLATO approval above this risk score */
        private double requireApprovalAbove = 0.5;
        
        /** Health window for rollback monitoring */
        private Duration rollbackHealthWindow = Duration.ofMinutes(5);
        
        /** Maximum rollback attempts */
        private int maxRollbackAttempts = 3;
        
        /** Cooldown period between remediations on same component */
        private Duration remediationCooldown = Duration.ofMinutes(10);
    }

    /**
     * Remediation execution configuration.
     */
    @Data
    public static class Remediation {
        /** Default execution timeout */
        private Duration defaultTimeout = Duration.ofMinutes(5);
        
        /** Maximum retry attempts for failed remediations */
        private int maxRetryAttempts = 3;
        
        /** Initial retry backoff */
        private Duration initialRetryBackoff = Duration.ofSeconds(5);
        
        /** Maximum retry backoff */
        private Duration maxRetryBackoff = Duration.ofMinutes(2);
        
        /** Retry backoff multiplier */
        private double retryBackoffMultiplier = 2.0;
        
        /** Playbook cache TTL */
        private Duration playbookCacheTtl = Duration.ofMinutes(5);
        
        /** SYNAPSE healing tools prefix */
        private String healingToolsPrefix = "aurora-healing-";
    }

    /**
     * PLATO governance client configuration.
     */
    @Data
    public static class Plato {
        @NotBlank
        private String url = "http://localhost:8082";
        
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration quickTimeout = Duration.ofSeconds(2);
        private int maxConnections = 50;
        
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();
        private final Retry retry = new Retry();
        private final Fallback fallback = new Fallback();
        private final Cache cache = new Cache();

        @Data
        public static class CircuitBreaker {
            private boolean enabled = true;
            private int failureRateThreshold = 50;
            private Duration waitDurationInOpenState = Duration.ofSeconds(30);
            private int minimumNumberOfCalls = 10;
            private int permittedNumberOfCallsInHalfOpenState = 5;
            private int slidingWindowSize = 20;
        }

        @Data
        public static class Retry {
            private int maxAttempts = 2;
            private Duration initialBackoff = Duration.ofMillis(200);
            private Duration maxBackoff = Duration.ofSeconds(5);
            private double jitter = 0.35;
        }

        @Data
        public static class Fallback {
            private boolean allowOnUnavailable = true;
            private boolean useCachedPolicies = true;
            private boolean autoApproveLowRisk = true;
            private boolean logFallbacks = true;
        }

        @Data
        public static class Cache {
            private boolean enabled = true;
            private Duration policyTtl = Duration.ofMinutes(5);
            private Duration approvalTtl = Duration.ofMinutes(1);
            private int maxPolicies = 1000;
        }
    }

    /**
     * SYNAPSE execution client configuration.
     */
    @Data
    public static class Synapse {
        @NotBlank
        private String url = "http://localhost:8084";
        
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxConnections = 50;
        
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();

        @Data
        public static class CircuitBreaker {
            private boolean enabled = true;
            private int failureRateThreshold = 50;
            private Duration waitDurationInOpenState = Duration.ofSeconds(30);
            private int slidingWindowSize = 10;
        }
    }

    /**
     * CAPSULE evidence storage client configuration.
     */
    @Data
    public static class Capsule {
        @NotBlank
        private String url = "http://localhost:8083";
        
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxConnections = 30;
        
        /** Buffer size for local evidence when CAPSULE unavailable */
        private int localBufferSize = 1000;
    }

    /**
     * Chaos immunity and learning configuration.
     */
    @Data
    public static class Immunity {
        /** Enable chaos immunization learning */
        private boolean enabled = true;
        
        /** Minimum confidence for immunity rules */
        private double minRuleConfidence = 0.7;
        
        /** Time-to-live for immunity rules */
        private Duration ruleTtl = Duration.ofDays(30);
        
        /** Maximum rules per component */
        private int maxRulesPerComponent = 100;
        
        /** Pattern learning window */
        private Duration patternLearningWindow = Duration.ofDays(7);
    }

    /**
     * Execution mode for remediation actions.
     */
    public enum ExecutionMode {
        /** Real execution with side effects */
        PRODUCTION,
        /** Validation only, no side effects */
        DRY_RUN,
        /** Mocked execution for testing */
        SANDBOX
    }
}
