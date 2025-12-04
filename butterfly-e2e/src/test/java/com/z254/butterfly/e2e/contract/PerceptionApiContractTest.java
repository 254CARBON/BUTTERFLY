package com.z254.butterfly.e2e.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pact Consumer-Driven Contract tests for PERCEPTION API.
 * 
 * <p>These tests define the contracts between PERCEPTION and its consumers:
 * <ul>
 *   <li>NEXUS Gateway (primary consumer)</li>
 *   <li>CAPSULE (state store)</li>
 *   <li>Portal UI (dashboard)</li>
 * </ul>
 *
 * <p>Domain coverage:
 * <ul>
 *   <li>Acquisition: Source management, content ingestion, DLQ operations</li>
 *   <li>Integrity: Trust scoring, embedding operations, quarantine</li>
 *   <li>Intelligence: Event detection, scenario lifecycle, feedback</li>
 *   <li>Monitoring: SLO status, pipeline health</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 *   mvn test -Dtest=PerceptionApiContractTest -Pcontract-tests
 * </pre>
 *
 * <p>Publish pacts to broker:
 * <pre>
 *   mvn pact:publish
 * </pre>
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "perception-api")
@DisplayName("PERCEPTION API Contract Tests")
public class PerceptionApiContractTest {

    private static final String TENANT_ID = "contract-test";
    private static final String CORRELATION_ID = "contract-test-correlation";

    private final RestTemplate restTemplate = new RestTemplate();

    // =====================================================================
    // ACQUISITION DOMAIN CONTRACTS
    // =====================================================================

    @Nested
    @DisplayName("Acquisition Domain Contracts")
    class AcquisitionContracts {

        @Pact(consumer = "nexus-gateway")
        public V4Pact registerSourcePact(PactDslWithProvider builder) {
            return builder
                    .given("no existing source with this ID")
                    .uponReceiving("a request to register a new acquisition source")
                    .path("/api/v1/acquisition/sources")
                    .method("POST")
                    .headers(Map.of(
                            "Content-Type", "application/json",
                            "X-Tenant-ID", TENANT_ID
                    ))
                    .body(new PactDslJsonBody()
                            .stringType("name", "Test API Source")
                            .stringValue("type", "API")
                            .stringType("endpoint", "https://api.example.com/feed")
                            .stringValue("tenantId", TENANT_ID)
                            .booleanValue("enabled", true)
                            .numberType("rateLimit", 100)
                    )
                    .willRespondWith()
                    .status(201)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("sourceId")
                            .stringType("name")
                            .stringValue("type", "API")
                            .stringValue("status", "ACTIVE")
                            .booleanValue("enabled", true)
                            .datetime("createdAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "registerSourcePact")
        void testRegisterSource(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", TENANT_ID);

            String body = """
                    {
                        "name": "Test API Source",
                        "type": "API",
                        "endpoint": "https://api.example.com/feed",
                        "tenantId": "%s",
                        "enabled": true,
                        "rateLimit": 100
                    }
                    """.formatted(TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/acquisition/sources",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).contains("sourceId");
        }

        @Pact(consumer = "nexus-gateway")
        public V4Pact getSourceStatsPact(PactDslWithProvider builder) {
            return builder
                    .given("source exists with ID src-123")
                    .uponReceiving("a request for acquisition statistics")
                    .path("/api/v1/acquisition/sources/src-123/stats")
                    .method("GET")
                    .headers(Map.of("X-Tenant-ID", TENANT_ID))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringValue("sourceId", "src-123")
                            .integerType("totalAcquisitions")
                            .integerType("successfulAcquisitions")
                            .integerType("failedAcquisitions")
                            .decimalType("successRate")
                            .datetime("lastAcquisition", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "getSourceStatsPact")
        void testGetSourceStats(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/acquisition/sources/src-123/stats",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("successRate");
        }

        @Pact(consumer = "nexus-gateway")
        public V4Pact ingestContentPact(PactDslWithProvider builder) {
            return builder
                    .given("source exists and is enabled")
                    .uponReceiving("a request to ingest content")
                    .path("/api/v1/acquisition/ingest")
                    .method("POST")
                    .headers(Map.of(
                            "Content-Type", "application/json",
                            "X-Tenant-ID", TENANT_ID,
                            "X-Correlation-ID", CORRELATION_ID
                    ))
                    .body(new PactDslJsonBody()
                            .stringType("sourceId", "src-123")
                            .stringValue("contentType", "application/json")
                            .stringType("idempotencyKey")
                            .object("content")
                                .stringType("title")
                                .stringType("body")
                            .closeObject()
                    )
                    .willRespondWith()
                    .status(202)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("contentId")
                            .stringMatcher("status", "ACCEPTED|PROCESSING|INGESTED", "ACCEPTED")
                            .stringType("correlationId")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "ingestContentPact")
        void testIngestContent(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", TENANT_ID);
            headers.set("X-Correlation-ID", CORRELATION_ID);

            String body = """
                    {
                        "sourceId": "src-123",
                        "contentType": "application/json",
                        "idempotencyKey": "test-key-123",
                        "content": {
                            "title": "Test Content",
                            "body": "Test body content"
                        }
                    }
                    """;

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/acquisition/ingest",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        @Pact(consumer = "perception-portal")
        public V4Pact listDlqMessagesPact(PactDslWithProvider builder) {
            return builder
                    .given("DLQ contains messages")
                    .uponReceiving("a request to list DLQ messages")
                    .path("/api/v1/acquisition/dlq")
                    .method("GET")
                    .matchQuery("limit", "\\d+", "20")
                    .headers(Map.of("X-Tenant-ID", TENANT_ID))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .integerType("totalElements")
                            .integerType("totalPages")
                            .integerType("page")
                            .eachLike("content")
                                .stringType("messageId")
                                .stringType("sourceId")
                                .stringType("errorReason")
                                .integerType("retryCount")
                                .datetime("failedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .closeObject()
                            .closeArray()
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "listDlqMessagesPact")
        void testListDlqMessages(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/acquisition/dlq?limit=20",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("totalElements");
        }
    }

    // =====================================================================
    // INTEGRITY DOMAIN CONTRACTS
    // =====================================================================

    @Nested
    @DisplayName("Integrity Domain Contracts")
    class IntegrityContracts {

        @Pact(consumer = "nexus-gateway")
        public V4Pact getTrustScorePact(PactDslWithProvider builder) {
            return builder
                    .given("content exists with ID content-456")
                    .uponReceiving("a request for trust score")
                    .path("/api/v1/integrity/trust/content/content-456")
                    .method("GET")
                    .headers(Map.of(
                            "X-Tenant-ID", TENANT_ID,
                            "X-Correlation-ID", CORRELATION_ID
                    ))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringValue("contentId", "content-456")
                            .decimalType("score", 0.85)
                            .stringMatcher("level", "HIGH|MEDIUM|LOW|UNKNOWN", "HIGH")
                            .datetime("assessedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .stringType("modelVersion")
                            .object("factors")
                                .decimalType("sourceReputation")
                                .decimalType("contentQuality")
                                .decimalType("temporalConsistency")
                            .closeObject()
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "getTrustScorePact")
        void testGetTrustScore(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);
            headers.set("X-Correlation-ID", CORRELATION_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/integrity/trust/content/content-456",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("score");
        }

        @Pact(consumer = "nexus-gateway")
        public V4Pact assessTrustPact(PactDslWithProvider builder) {
            return builder
                    .given("trust scoring service is available")
                    .uponReceiving("a request to assess trust for content")
                    .path("/api/v1/integrity/trust/assess")
                    .method("POST")
                    .headers(Map.of(
                            "Content-Type", "application/json",
                            "X-Tenant-ID", TENANT_ID
                    ))
                    .body(new PactDslJsonBody()
                            .stringType("contentId")
                            .object("content")
                                .stringType("text")
                                .stringType("source")
                            .closeObject()
                            .object("options")
                                .booleanType("includeEmbedding")
                                .booleanType("checkDuplicates")
                            .closeObject()
                    )
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("contentId")
                            .decimalType("score")
                            .stringMatcher("level", "HIGH|MEDIUM|LOW|UNKNOWN", "MEDIUM")
                            .booleanType("isDuplicate")
                            .stringType("explanation")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "assessTrustPact")
        void testAssessTrust(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", TENANT_ID);

            String body = """
                    {
                        "contentId": "content-new-123",
                        "content": {
                            "text": "Sample content for trust assessment",
                            "source": "test-harness"
                        },
                        "options": {
                            "includeEmbedding": true,
                            "checkDuplicates": true
                        }
                    }
                    """;

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/integrity/trust/assess",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Pact(consumer = "nexus-gateway")
        public V4Pact embeddingSearchPact(PactDslWithProvider builder) {
            return builder
                    .given("embedding index contains vectors")
                    .uponReceiving("a request to search similar embeddings")
                    .path("/api/v1/integrity/embeddings/search")
                    .method("POST")
                    .headers(Map.of(
                            "Content-Type", "application/json",
                            "X-Tenant-ID", TENANT_ID
                    ))
                    .body(new PactDslJsonBody()
                            .minArrayLike("embedding", 3, PactDslJsonBody.numberType())
                            .integerType("topK", 10)
                            .decimalType("threshold", 0.5)
                    )
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .eachLike("results")
                                .stringType("contentId")
                                .decimalType("similarity")
                            .closeObject()
                            .closeArray()
                            .integerType("totalResults")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "embeddingSearchPact")
        void testEmbeddingSearch(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", TENANT_ID);

            String body = """
                    {
                        "embedding": [0.1, 0.2, 0.3, 0.4, 0.5],
                        "topK": 10,
                        "threshold": 0.5
                    }
                    """;

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/integrity/embeddings/search",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // =====================================================================
    // INTELLIGENCE DOMAIN CONTRACTS
    // =====================================================================

    @Nested
    @DisplayName("Intelligence Domain Contracts")
    class IntelligenceContracts {

        @Pact(consumer = "nexus-gateway")
        public V4Pact queryEventsPact(PactDslWithProvider builder) {
            return builder
                    .given("events exist in the specified time range")
                    .uponReceiving("a request to query detected events")
                    .path("/api/v1/intelligence/events")
                    .method("GET")
                    .matchQuery("fromTimestamp", "\\d+", "1701460800000")
                    .matchQuery("toTimestamp", "\\d+", "1701547200000")
                    .matchQuery("limit", "\\d+", "20")
                    .headers(Map.of("X-Tenant-ID", TENANT_ID))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .integerType("totalElements")
                            .eachLike("content")
                                .stringType("eventId")
                                .stringMatcher("eventType", "MARKET_SHOCK|VOLATILITY|ANOMALY|PATTERN", "VOLATILITY")
                                .datetime("detectedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                                .decimalType("confidence")
                                .stringType("description")
                            .closeObject()
                            .closeArray()
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "queryEventsPact")
        void testQueryEvents(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/intelligence/events?fromTimestamp=1701460800000&toTimestamp=1701547200000&limit=20",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Pact(consumer = "nexus-gateway")
        public V4Pact getScenarioPact(PactDslWithProvider builder) {
            return builder
                    .given("scenario exists with ID scenario-789")
                    .uponReceiving("a request to get scenario details")
                    .path("/api/v1/intelligence/scenarios/scenario-789")
                    .method("GET")
                    .headers(Map.of(
                            "X-Tenant-ID", TENANT_ID,
                            "X-Correlation-ID", CORRELATION_ID
                    ))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringValue("scenarioId", "scenario-789")
                            .stringMatcher("status", "PENDING|ACTIVE|COMPLETED|CANCELLED", "ACTIVE")
                            .stringType("eventId")
                            .stringType("scenarioType")
                            .stringType("description")
                            .datetime("createdAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .decimalType("confidence")
                            .object("parameters")
                            .closeObject()
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "getScenarioPact")
        void testGetScenario(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);
            headers.set("X-Correlation-ID", CORRELATION_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/intelligence/scenarios/scenario-789",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Pact(consumer = "nexus-gateway")
        public V4Pact submitFeedbackPact(PactDslWithProvider builder) {
            return builder
                    .given("scenario exists for feedback")
                    .uponReceiving("a request to submit scenario feedback")
                    .path("/api/v1/intelligence/scenarios/scenario-789/feedback")
                    .method("POST")
                    .headers(Map.of(
                            "Content-Type", "application/json",
                            "X-Tenant-ID", TENANT_ID
                    ))
                    .body(new PactDslJsonBody()
                            .stringMatcher("feedbackType", "ACCURACY|RELEVANCE|ACTIONABILITY", "ACCURACY")
                            .stringMatcher("outcome", "ACCURATE|INACCURATE|PARTIALLY_ACCURATE", "ACCURATE")
                            .decimalType("confidence", 0.9)
                            .stringType("notes")
                    )
                    .willRespondWith()
                    .status(202)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .stringType("feedbackId")
                            .stringValue("status", "ACCEPTED")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "submitFeedbackPact")
        void testSubmitFeedback(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Tenant-ID", TENANT_ID);

            String body = """
                    {
                        "feedbackType": "ACCURACY",
                        "outcome": "ACCURATE",
                        "confidence": 0.9,
                        "notes": "Scenario was correctly identified"
                    }
                    """;

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/intelligence/scenarios/scenario-789/feedback",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    // =====================================================================
    // MONITORING DOMAIN CONTRACTS
    // =====================================================================

    @Nested
    @DisplayName("Monitoring Domain Contracts")
    class MonitoringContracts {

        @Pact(consumer = "perception-portal")
        public V4Pact getPipelineHealthPact(PactDslWithProvider builder) {
            return builder
                    .given("monitoring service is available")
                    .uponReceiving("a request for pipeline health")
                    .path("/api/v1/monitoring/health")
                    .method("GET")
                    .headers(Map.of("X-Tenant-ID", TENANT_ID))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .decimalType("overallScore")
                            .decimalType("fidelityScore")
                            .decimalType("freshnessScore")
                            .decimalType("trustQuality")
                            .stringMatcher("status", "HEALTHY|DEGRADED|CRITICAL", "HEALTHY")
                            .datetime("assessedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .decimalType("ingestionSuccessRate")
                            .decimalType("dlqRate")
                            .object("componentScores")
                            .closeObject()
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "getPipelineHealthPact")
        void testGetPipelineHealth(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/monitoring/health",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("overallScore");
        }

        @Pact(consumer = "perception-portal")
        public V4Pact getSloSnapshotPact(PactDslWithProvider builder) {
            return builder
                    .given("SLO metrics are available")
                    .uponReceiving("a request for SLO snapshot")
                    .path("/api/v1/monitoring/slo/snapshot")
                    .method("GET")
                    .headers(Map.of("X-Tenant-ID", TENANT_ID))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .datetime("capturedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                            .stringMatcher("overallStatus", "MET|WARNING|CRITICAL|UNKNOWN", "MET")
                            .integerType("slosMet")
                            .integerType("slosWarning")
                            .integerType("slosCritical")
                            .integerType("slosUnknown")
                            .decimalType("errorBudgetRemaining")
                            .decimalType("errorBudgetBurnRate")
                            .object("domainSummaries")
                                .object("ACQUISITION")
                                    .stringMatcher("status", "MET|WARNING|CRITICAL|UNKNOWN", "MET")
                                    .integerType("totalSlos")
                                    .decimalType("healthScore")
                                .closeObject()
                                .object("INTEGRITY")
                                    .stringMatcher("status", "MET|WARNING|CRITICAL|UNKNOWN", "MET")
                                    .integerType("totalSlos")
                                    .decimalType("healthScore")
                                .closeObject()
                                .object("INTELLIGENCE")
                                    .stringMatcher("status", "MET|WARNING|CRITICAL|UNKNOWN", "MET")
                                    .integerType("totalSlos")
                                    .decimalType("healthScore")
                                .closeObject()
                            .closeObject()
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "getSloSnapshotPact")
        void testGetSloSnapshot(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/monitoring/slo/snapshot",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("domainSummaries");
        }

        @Pact(consumer = "perception-portal")
        public V4Pact getErrorBudgetPact(PactDslWithProvider builder) {
            return builder
                    .given("error budget tracking is active")
                    .uponReceiving("a request for error budget status")
                    .path("/api/v1/monitoring/slo/error-budget")
                    .method("GET")
                    .headers(Map.of("X-Tenant-ID", TENANT_ID))
                    .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(new PactDslJsonBody()
                            .decimalType("remaining")
                            .decimalType("burnRate")
                            .stringMatcher("status", "MET|WARNING|CRITICAL|UNKNOWN", "MET")
                            .datetime("capturedAt", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    )
                    .toPact(V4Pact.class);
        }

        @Test
        @PactTestFor(pactMethod = "getErrorBudgetPact")
        void testGetErrorBudget(MockServer mockServer) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Tenant-ID", TENANT_ID);

            ResponseEntity<String> response = restTemplate.exchange(
                    mockServer.getUrl() + "/api/v1/monitoring/slo/error-budget",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("remaining");
        }
    }
}
