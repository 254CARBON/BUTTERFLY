package com.z254.butterfly.e2e.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Test harness for seeding CAPSULE, PERCEPTION, and ODYSSEY with representative
 * data for Temporal Intelligence Fabric verification.
 * 
 * <p>This harness creates a coherent dataset for a representative domain (FX/EURUSD)
 * spanning 30 days of history, current state, and 7-day projections.
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * TemporalDataHarness harness = new TemporalDataHarness()
 *     .withCapsuleUrl("http://localhost:8081")
 *     .withPerceptionUrl("http://localhost:8082")
 *     .withOdysseyUrl("http://localhost:8083");
 * 
 * harness.seedAll();
 * 
 * // Run temporal queries against NEXUS
 * // ...
 * 
 * harness.cleanup();
 * }</pre>
 */
public class TemporalDataHarness {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // Service URLs
    private String capsuleUrl = "http://localhost:8081";
    private String perceptionUrl = "http://localhost:8082";
    private String odysseyUrl = "http://localhost:8083";
    private String wiremockUrl = "http://localhost:8080";
    private boolean useWireMock = false;

    // Seed data configuration
    private static final String TEST_NODE_ID = "rim:entity:finance:EURUSD";
    private static final String TEST_SCOPE_ID = "rim-scope:finance:fx-major";
    private static final int HISTORY_DAYS = 30;
    private static final int PROJECTION_DAYS = 7;

    // Generated IDs for cleanup
    private final List<String> createdCapsuleIds = new ArrayList<>();
    private final List<String> createdPathIds = new ArrayList<>();

    // ========================================================================
    // Configuration
    // ========================================================================

    public TemporalDataHarness withCapsuleUrl(String url) {
        this.capsuleUrl = url;
        return this;
    }

    public TemporalDataHarness withPerceptionUrl(String url) {
        this.perceptionUrl = url;
        return this;
    }

    public TemporalDataHarness withOdysseyUrl(String url) {
        this.odysseyUrl = url;
        return this;
    }

    public TemporalDataHarness withWireMock(String wiremockUrl) {
        this.wiremockUrl = wiremockUrl;
        this.useWireMock = true;
        return this;
    }

    // ========================================================================
    // Main Seeding Methods
    // ========================================================================

    /**
     * Seeds all services with representative data.
     */
    public void seedAll() throws Exception {
        seedCapsuleHistory();
        seedPerceptionCurrentState();
        seedOdysseyProjections();
    }

    /**
     * Seeds CAPSULE with 30 days of historical data for EURUSD.
     */
    public void seedCapsuleHistory() throws Exception {
        if (useWireMock) {
            setupCapsuleWireMockStubs();
            return;
        }

        Instant now = Instant.now();
        double basePrice = 1.08; // Base EURUSD price
        Random random = new Random(42); // Seeded for reproducibility

        for (int day = 0; day < HISTORY_DAYS; day++) {
            Instant capsuleTime = now.minus(HISTORY_DAYS - day, ChronoUnit.DAYS);
            
            // Simulate realistic price movements
            double dailyChange = (random.nextDouble() - 0.5) * 0.02; // ±1% daily
            basePrice = basePrice * (1 + dailyChange);
            basePrice = Math.max(1.0, Math.min(1.2, basePrice)); // Bound to realistic range

            double stress = 0.2 + random.nextDouble() * 0.3; // 0.2-0.5 stress
            double volume = 1000000 + random.nextDouble() * 500000;

            ObjectNode capsule = createCapsule(
                TEST_NODE_ID,
                capsuleTime,
                basePrice,
                volume,
                stress,
                day < 5 ? "STABLE" : (random.nextBoolean() ? "UP" : "DOWN")
            );

            String capsuleId = postCapsule(capsule);
            if (capsuleId != null) {
                createdCapsuleIds.add(capsuleId);
            }
        }
    }

    /**
     * Seeds PERCEPTION with current RIM state for EURUSD.
     */
    public void seedPerceptionCurrentState() throws Exception {
        if (useWireMock) {
            setupPerceptionWireMockStubs();
            return;
        }

        ObjectNode rimState = createRimNodeState(
            TEST_NODE_ID,
            1.085, // Current price
            1500000, // Volume
            0.35,  // Stress
            0.85,  // Trust
            0.92   // Coverage
        );

        postRimState(rimState);
    }

    /**
     * Seeds ODYSSEY with projected paths for EURUSD.
     */
    public void seedOdysseyProjections() throws Exception {
        if (useWireMock) {
            setupOdysseyWireMockStubs();
            return;
        }

        // Create multiple narrative paths
        List<ObjectNode> paths = Arrays.asList(
            createNarrativePath(
                "path-eurusd-bull",
                TEST_NODE_ID,
                "EUR strength continues on ECB hawkish stance",
                0.45,
                0.72,
                "SHORT_TERM",
                List.of("ecb-policy", "inflation-data"),
                createTippingPoint("tip-ecb-decision", "ECB Rate Decision", 3, "APPROACHING")
            ),
            createNarrativePath(
                "path-eurusd-bear",
                TEST_NODE_ID,
                "USD rally on Fed maintaining rates",
                0.35,
                0.68,
                "SHORT_TERM",
                List.of("fed-policy", "us-employment"),
                createTippingPoint("tip-fed-fomc", "FOMC Meeting", 5, "STABLE")
            ),
            createNarrativePath(
                "path-eurusd-range",
                TEST_NODE_ID,
                "Range-bound consolidation before major catalyst",
                0.20,
                0.55,
                "MEDIUM_TERM",
                List.of("technical-resistance", "market-positioning"),
                null
            )
        );

        for (ObjectNode path : paths) {
            String pathId = postNarrativePath(path);
            if (pathId != null) {
                createdPathIds.add(pathId);
            }
        }
    }

    // ========================================================================
    // WireMock Stub Creation
    // ========================================================================

    private void setupCapsuleWireMockStubs() throws Exception {
        // Create realistic 30-day history response
        ArrayNode capsules = MAPPER.createArrayNode();
        Instant now = Instant.now();
        double basePrice = 1.08;
        Random random = new Random(42);

        for (int day = 0; day < HISTORY_DAYS; day++) {
            Instant capsuleTime = now.minus(HISTORY_DAYS - day, ChronoUnit.DAYS);
            double dailyChange = (random.nextDouble() - 0.5) * 0.02;
            basePrice = basePrice * (1 + dailyChange);
            basePrice = Math.max(1.0, Math.min(1.2, basePrice));

            ObjectNode capsule = MAPPER.createObjectNode();
            capsule.put("capsuleId", "cap-eurusd-" + day);
            capsule.put("scopeId", TEST_NODE_ID);
            capsule.put("timestamp", capsuleTime.toString());
            
            ObjectNode config = capsule.putObject("configuration");
            config.put("price", Math.round(basePrice * 10000) / 10000.0);
            config.put("volume", 1000000 + random.nextInt(500000));
            
            ObjectNode dynamics = capsule.putObject("dynamics");
            dynamics.put("trend", random.nextBoolean() ? "UP" : "DOWN");
            dynamics.put("volatility", 0.1 + random.nextDouble() * 0.2);
            dynamics.put("stress", 0.2 + random.nextDouble() * 0.3);
            
            capsules.add(capsule);
        }

        createWireMockStub("GET", "/api/v1/history.*", 200, capsules.toString());
    }

    private void setupPerceptionWireMockStubs() throws Exception {
        ObjectNode rimState = MAPPER.createObjectNode();
        rimState.put("rimNodeId", TEST_NODE_ID);
        rimState.put("nodeType", "entity");
        rimState.put("namespace", "finance");
        rimState.put("localId", "EURUSD");
        rimState.put("status", "ACTIVE");

        ObjectNode config = rimState.putObject("configuration");
        config.put("price", 1.085);
        config.put("volume", 1500000);
        config.put("spread", 0.0001);

        ObjectNode dynamics = rimState.putObject("dynamics");
        dynamics.put("trend", "UP");
        dynamics.put("volatility", 0.15);
        dynamics.put("stress", 0.35);

        ObjectNode trust = rimState.putObject("trustMetrics");
        trust.put("overallTrust", 0.85);
        trust.put("sourceReliability", 0.88);
        trust.put("dataCurrency", 0.95);

        ObjectNode visibility = rimState.putObject("visibilityMetrics");
        visibility.put("coverage", 0.92);
        visibility.put("consensus", 0.87);
        visibility.put("divergence", 0.05);
        visibility.put("visibilityLevel", "HIGH");

        rimState.put("snapshotTime", Instant.now().toString());

        createWireMockStub("GET", "/api/v1/rim/nodes/" + TEST_NODE_ID.replace(":", "%3A") + ".*", 200, rimState.toString());
        createWireMockStub("GET", "/api/v1/rim/nodes/.*/state", 200, rimState.toString());
    }

    private void setupOdysseyWireMockStubs() throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        ArrayNode paths = response.putArray("paths");

        // Bull path
        ObjectNode bullPath = paths.addObject();
        bullPath.put("pathId", "path-eurusd-bull");
        bullPath.put("scopeId", TEST_NODE_ID);
        bullPath.put("description", "EUR strength continues on ECB hawkish stance");
        bullPath.put("weight", 0.45);
        bullPath.put("confidence", 0.72);
        bullPath.put("horizon", "SHORT_TERM");
        bullPath.putArray("keyDrivers").add("ecb-policy").add("inflation-data");
        
        ArrayNode bullTips = bullPath.putArray("tippingPoints");
        ObjectNode tip1 = bullTips.addObject();
        tip1.put("tippingId", "tip-ecb-decision");
        tip1.put("label", "ECB Rate Decision");
        tip1.put("distanceTimeSeconds", 3 * 24 * 3600);
        tip1.put("directionOfTravel", "APPROACHING");

        // Bear path
        ObjectNode bearPath = paths.addObject();
        bearPath.put("pathId", "path-eurusd-bear");
        bearPath.put("scopeId", TEST_NODE_ID);
        bearPath.put("description", "USD rally on Fed maintaining rates");
        bearPath.put("weight", 0.35);
        bearPath.put("confidence", 0.68);
        bearPath.put("horizon", "SHORT_TERM");
        bearPath.putArray("keyDrivers").add("fed-policy").add("us-employment");

        // Range path
        ObjectNode rangePath = paths.addObject();
        rangePath.put("pathId", "path-eurusd-range");
        rangePath.put("scopeId", TEST_NODE_ID);
        rangePath.put("description", "Range-bound consolidation");
        rangePath.put("weight", 0.20);
        rangePath.put("confidence", 0.55);
        rangePath.put("horizon", "MEDIUM_TERM");

        createWireMockStub("GET", "/api/v1/paths.*", 200, response.toString());
        createWireMockStub("GET", "/api/v1/world/state", 200, createWorldStateResponse().toString());
    }

    private ObjectNode createWorldStateResponse() {
        ObjectNode worldState = MAPPER.createObjectNode();
        worldState.put("snapshotId", "ws-" + System.currentTimeMillis());
        worldState.put("timestamp", Instant.now().toString());
        
        ArrayNode entities = worldState.putArray("entities");
        ObjectNode entity = entities.addObject();
        entity.put("entityId", TEST_NODE_ID);
        entity.put("entityType", "currency_pair");
        entity.put("name", "EUR/USD");
        
        ObjectNode stress = entity.putObject("stress");
        stress.put("stressScore", 0.35);
        stress.put("bufferRemaining", 0.65);
        stress.put("fragility", 0.2);
        stress.put("trend", "STABLE");

        return worldState;
    }

    private void createWireMockStub(String method, String urlPattern, int status, String responseBody) throws Exception {
        ObjectNode stub = MAPPER.createObjectNode();
        
        ObjectNode request = stub.putObject("request");
        request.put("method", method);
        request.put("urlPathPattern", urlPattern);
        
        ObjectNode response = stub.putObject("response");
        response.put("status", status);
        ObjectNode headers = response.putObject("headers");
        headers.put("Content-Type", "application/json");
        response.put("body", responseBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(wiremockUrl + "/__admin/mappings"))
            .POST(HttpRequest.BodyPublishers.ofString(stub.toString()))
            .header("Content-Type", "application/json")
            .build();

        HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    // ========================================================================
    // Data Object Creation
    // ========================================================================

    private ObjectNode createCapsule(String scopeId, Instant timestamp, double price, 
                                     double volume, double stress, String trend) {
        ObjectNode capsule = MAPPER.createObjectNode();
        
        ObjectNode header = capsule.putObject("header");
        ObjectNode time = header.putObject("time");
        time.put("centerTsUtc", timestamp.toString());
        time.put("deltaTSeconds", 86400); // 1 day resolution
        time.put("horizonSeconds", 86400);
        
        ObjectNode scope = header.putObject("scope");
        scope.put("scopeId", scopeId);
        scope.put("description", "EURUSD currency pair");
        
        ObjectNode vantage = header.putObject("vantage");
        vantage.put("mode", "MARKET_DATA");
        vantage.put("observerId", "temporal-harness");
        
        ObjectNode config = capsule.putObject("configuration");
        config.put("price", price);
        config.put("volume", volume);
        config.put("spread", 0.0001);
        
        ObjectNode dynamics = capsule.putObject("dynamics");
        dynamics.put("trend", trend);
        dynamics.put("volatility", 0.1 + Math.random() * 0.1);
        dynamics.put("stress", stress);
        
        return capsule;
    }

    private ObjectNode createRimNodeState(String rimNodeId, double price, double volume,
                                          double stress, double trust, double coverage) {
        ObjectNode state = MAPPER.createObjectNode();
        state.put("rimNodeId", rimNodeId);
        state.put("snapshotTime", Instant.now().toString());
        
        ObjectNode config = state.putObject("configuration");
        config.put("price", price);
        config.put("volume", volume);
        
        ObjectNode dynamics = state.putObject("dynamics");
        dynamics.put("stress", stress);
        dynamics.put("trend", "UP");
        
        ObjectNode trustMetrics = state.putObject("trustMetrics");
        trustMetrics.put("overallTrust", trust);
        
        ObjectNode visibilityMetrics = state.putObject("visibilityMetrics");
        visibilityMetrics.put("coverage", coverage);
        
        return state;
    }

    private ObjectNode createNarrativePath(String pathId, String scopeId, String description,
                                           double weight, double confidence, String horizon,
                                           List<String> keyDrivers, ObjectNode tippingPoint) {
        ObjectNode path = MAPPER.createObjectNode();
        path.put("pathId", pathId);
        path.put("scopeId", scopeId);
        path.put("description", description);
        path.put("weight", weight);
        path.put("confidence", confidence);
        path.put("horizon", horizon);
        
        ArrayNode drivers = path.putArray("keyDrivers");
        keyDrivers.forEach(drivers::add);
        
        if (tippingPoint != null) {
            path.putArray("tippingPoints").add(tippingPoint);
        }
        
        return path;
    }

    private ObjectNode createTippingPoint(String tippingId, String label, int daysAway, String direction) {
        ObjectNode tip = MAPPER.createObjectNode();
        tip.put("tippingId", tippingId);
        tip.put("label", label);
        tip.put("distanceTimeSeconds", daysAway * 24 * 3600);
        tip.put("directionOfTravel", direction);
        return tip;
    }

    // ========================================================================
    // HTTP Operations
    // ========================================================================

    private String postCapsule(ObjectNode capsule) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(capsuleUrl + "/api/v1/capsules"))
            .POST(HttpRequest.BodyPublishers.ofString(capsule.toString()))
            .header("Content-Type", "application/json")
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201 || response.statusCode() == 200) {
            JsonNode body = MAPPER.readTree(response.body());
            return body.has("capsuleId") ? body.get("capsuleId").asText() : null;
        }
        return null;
    }

    private void postRimState(ObjectNode state) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(perceptionUrl + "/api/v1/rim/nodes"))
            .POST(HttpRequest.BodyPublishers.ofString(state.toString()))
            .header("Content-Type", "application/json")
            .build();

        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String postNarrativePath(ObjectNode path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(odysseyUrl + "/api/v1/paths"))
            .POST(HttpRequest.BodyPublishers.ofString(path.toString()))
            .header("Content-Type", "application/json")
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201 || response.statusCode() == 200) {
            JsonNode body = MAPPER.readTree(response.body());
            return body.has("pathId") ? body.get("pathId").asText() : null;
        }
        return null;
    }

    // ========================================================================
    // Cleanup
    // ========================================================================

    /**
     * Cleans up all seeded data.
     */
    public void cleanup() throws Exception {
        if (useWireMock) {
            // Reset WireMock stubs
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(wiremockUrl + "/__admin/mappings/reset"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return;
        }

        // Delete created capsules
        for (String capsuleId : createdCapsuleIds) {
            deleteCapsule(capsuleId);
        }

        // Delete created paths
        for (String pathId : createdPathIds) {
            deletePath(pathId);
        }

        createdCapsuleIds.clear();
        createdPathIds.clear();
    }

    private void deleteCapsule(String capsuleId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(capsuleUrl + "/api/v1/capsules/" + capsuleId))
            .DELETE()
            .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void deletePath(String pathId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(odysseyUrl + "/api/v1/paths/" + pathId))
            .DELETE()
            .build();
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ========================================================================
    // Verification Helpers
    // ========================================================================

    /**
     * Returns the test node ID for verification.
     */
    public String getTestNodeId() {
        return TEST_NODE_ID;
    }

    /**
     * Returns the expected capsule count.
     */
    public int getExpectedCapsuleCount() {
        return HISTORY_DAYS;
    }

    /**
     * Returns the expected path count.
     */
    public int getExpectedPathCount() {
        return 3;
    }
}

