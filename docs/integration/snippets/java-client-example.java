/**
 * PERCEPTION API Integration - Java Examples
 * 
 * Prerequisites:
 *   - Java 17+
 *   - perception-client dependency (see clients/java/README.md)
 *   
 * Maven dependency:
 *   <dependency>
 *     <groupId>com.z254.butterfly</groupId>
 *     <artifactId>perception-client</artifactId>
 *     <version>${perception.client.version}</version>
 *   </dependency>
 */

package com.z254.butterfly.integration.example;

import com.z254.butterfly.perception.client.PerceptionClient;
import com.z254.butterfly.perception.client.PerceptionClientConfiguration;
import com.z254.butterfly.perception.client.api.*;
import com.z254.butterfly.perception.client.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Complete example of using the PERCEPTION Java client SDK.
 */
public class PerceptionIntegrationExample {

    private final PerceptionClient client;

    public PerceptionIntegrationExample() {
        // Initialize client with configuration
        PerceptionClientConfiguration config = PerceptionClientConfiguration.builder()
            .baseUrl("http://localhost:8080")
            .apiKey(System.getenv("PERCEPTION_API_KEY"))
            .timeout(Duration.ofSeconds(30))
            .retryOnFailure(true)
            .maxRetries(3)
            .build();
        
        this.client = new PerceptionClient(config);
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    /**
     * Exchange API key for JWT token (if using API key auth).
     */
    public String authenticate() {
        TokenResponse response = client.auth().exchangeApiKey(
            System.getenv("PERCEPTION_API_KEY")
        );
        return response.getToken();
    }

    // =========================================================================
    // Acquisition API - Managing Data Sources
    // =========================================================================

    /**
     * Register a new RSS feed data source.
     */
    public Source registerRssSource() {
        CreateSourceRequest request = CreateSourceRequest.builder()
            .name("Tech News Feed")
            .type(SourceType.RSS_FEED)
            .endpoint("https://example.com/rss/tech.xml")
            .schedule("0 */30 * * * *")  // Every 30 minutes
            .tenantId("global")
            .rateLimit(60)
            .build();

        return client.acquisition().createSource(request);
    }

    /**
     * List all registered sources.
     */
    public List<Source> listSources() {
        return client.acquisition().listSources(
            ListSourcesRequest.builder()
                .page(0)
                .size(20)
                .build()
        );
    }

    /**
     * Trigger manual acquisition for a source.
     */
    public void triggerAcquisition(String sourceId) {
        client.acquisition().triggerSource(sourceId);
    }

    // =========================================================================
    // Intelligence API - Events and Scenarios
    // =========================================================================

    /**
     * Get recent events with scenarios.
     */
    public List<Event> getRecentEvents() {
        return client.intelligence().listEvents(
            ListEventsRequest.builder()
                .limit(20)
                .includeScenarios(true)
                .build()
        );
    }

    /**
     * Get event details with full scenario bundle.
     */
    public Event getEventDetails(String eventId) {
        return client.intelligence().getEvent(eventId);
    }

    /**
     * Submit feedback on scenario execution.
     */
    public void submitScenarioFeedback(String scenarioId, boolean success, String notes) {
        ScenarioFeedbackRequest request = ScenarioFeedbackRequest.builder()
            .feedback(success ? "Scenario executed successfully" : "Scenario execution failed")
            .outcome(success ? Outcome.SUCCESS : Outcome.FAILURE)
            .notes(notes)
            .build();

        client.intelligence().submitFeedback(scenarioId, request);
    }

    // =========================================================================
    // Signals API - Weak Signal Detection
    // =========================================================================

    /**
     * Get detected weak signals from the last 24 hours.
     */
    public List<WeakSignal> getDetectedSignals() {
        return client.signals().getDetectedSignals(
            GetSignalsRequest.builder()
                .hoursBack(24)
                .build()
        );
    }

    /**
     * Track an event horizon for a topic.
     */
    public EventHorizon trackEventHorizon(String topic) {
        return client.signals().trackHorizon(topic);
    }

    /**
     * Get signal clusters.
     */
    public List<SignalCluster> getSignalClusters() {
        return client.signals().getClusters(
            GetClustersRequest.builder()
                .timeWindowHours(24)
                .minClusterSize(2)
                .build()
        );
    }

    // =========================================================================
    // RIM API - Reality Integration Mesh
    // =========================================================================

    /**
     * Register a new RIM node.
     */
    public RimNode registerRimNode() {
        CreateRimNodeRequest request = CreateRimNodeRequest.builder()
            .nodeId("rim:entity:finance:EURUSD")
            .displayName("EUR/USD Currency Pair")
            .nodeType(NodeType.ENTITY)
            .namespace("finance")
            .region("global")
            .domain("fx")
            .status(NodeStatus.ACTIVE)
            .build();

        return client.rim().createNode(request);
    }

    /**
     * Get mesh snapshot.
     */
    public MeshSnapshot getMeshSnapshot() {
        return client.rim().getMesh(
            GetMeshRequest.builder()
                .scope("global")
                .window(Duration.ofMinutes(5))
                .build()
        );
    }

    /**
     * Get active anomalies.
     */
    public List<Anomaly> getAnomalies(double minSeverity) {
        return client.rim().getAnomalies(
            GetAnomaliesRequest.builder()
                .minSeverity(minSeverity)
                .build()
        );
    }

    // =========================================================================
    // Integrity API - Trust Scoring
    // =========================================================================

    /**
     * Get trust score for content.
     */
    public TrustScore getContentTrustScore(String contentId) {
        return client.integrity().getContentTrust(contentId);
    }

    /**
     * Get trust score for a source.
     */
    public TrustScore getSourceTrustScore(String sourceId) {
        return client.integrity().getSourceTrust(sourceId);
    }

    // =========================================================================
    // Stream A API - Reasoning & Predictions
    // =========================================================================

    /**
     * Apply reasoning rules to an interpretation.
     */
    public ReasoningResult applyReasoning(String interpretation, double initialPlausibility) {
        return client.reasoning().apply(
            ReasoningRequest.builder()
                .interpretation(interpretation)
                .initialPlausibility(initialPlausibility)
                .build()
        );
    }

    /**
     * Generate prediction for a target metric.
     */
    public Prediction generatePrediction(String target, double currentValue, int horizonHours) {
        return client.predictive().predict(
            PredictionRequest.builder()
                .target(target)
                .currentValue(currentValue)
                .horizonHours(horizonHours)
                .build()
        );
    }

    /**
     * Get early warnings.
     */
    public List<EarlyWarning> getEarlyWarnings() {
        return client.predictive().getEarlyWarnings();
    }

    // =========================================================================
    // Async Operations
    // =========================================================================

    /**
     * Async event fetch with CompletableFuture.
     */
    public CompletableFuture<List<Event>> getRecentEventsAsync() {
        return client.intelligence().listEventsAsync(
            ListEventsRequest.builder()
                .limit(20)
                .build()
        );
    }

    // =========================================================================
    // Error Handling
    // =========================================================================

    /**
     * Example with proper error handling.
     */
    public void exampleWithErrorHandling(String eventId) {
        try {
            Event event = client.intelligence().getEvent(eventId);
            System.out.println("Event: " + event.getTitle());
        } catch (PerceptionNotFoundException e) {
            System.err.println("Event not found: " + eventId);
        } catch (PerceptionUnauthorizedException e) {
            System.err.println("Authentication failed - check API key");
        } catch (PerceptionRateLimitedException e) {
            System.err.println("Rate limited - retry after: " + e.getRetryAfter());
        } catch (PerceptionApiException e) {
            System.err.println("API error: " + e.getErrorCode() + " - " + e.getMessage());
        }
    }

    // =========================================================================
    // Main - Demo
    // =========================================================================

    public static void main(String[] args) {
        PerceptionIntegrationExample example = new PerceptionIntegrationExample();

        // List events
        List<Event> events = example.getRecentEvents();
        System.out.println("Found " + events.size() + " events");

        // Get weak signals
        List<WeakSignal> signals = example.getDetectedSignals();
        System.out.println("Detected " + signals.size() + " weak signals");

        // Register source
        Source source = example.registerRssSource();
        System.out.println("Registered source: " + source.getId());
    }
}
