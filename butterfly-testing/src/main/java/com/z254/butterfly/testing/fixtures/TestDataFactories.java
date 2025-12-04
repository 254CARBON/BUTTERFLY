package com.z254.butterfly.testing.fixtures;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Factory utilities for generating test data across BUTTERFLY services.
 * 
 * <p>Provides consistent test data generation patterns for:</p>
 * <ul>
 *   <li>Unique identifiers (UUIDs, sequence IDs)</li>
 *   <li>RIM Node identifiers</li>
 *   <li>Timestamps and time ranges</li>
 *   <li>Random data generation</li>
 *   <li>Builder patterns for complex objects</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Generate unique IDs
 * String id = TestDataFactories.uniqueId("capsule");  // "capsule-a1b2c3d4"
 * 
 * // Generate RIM node IDs
 * String rimId = TestDataFactories.rimNodeId("entity", "finance", "EURUSD");
 * // -> "rim:entity:finance:EURUSD"
 * 
 * // Generate timestamps
 * Instant now = TestDataFactories.now();
 * Instant pastHour = TestDataFactories.hoursAgo(1);
 * 
 * // Random data
 * double stress = TestDataFactories.randomStress();  // 0.0 - 1.0
 * String ticker = TestDataFactories.randomTicker();  // "AAPL", "MSFT", etc.
 * }</pre>
 * 
 * @since 0.1.0
 */
public final class TestDataFactories {

    private static final AtomicLong sequenceCounter = new AtomicLong(0);
    private static final Random random = ThreadLocalRandom.current();

    // Common test tickers
    private static final List<String> TICKERS = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "JPM", "V", "JNJ"
    );

    // Common currency pairs
    private static final List<String> CURRENCY_PAIRS = List.of(
            "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD", "NZDUSD", "USDCHF", "EURGBP"
    );

    // Common sectors
    private static final List<String> SECTORS = List.of(
            "technology", "finance", "healthcare", "energy", "consumer", "industrial"
    );

    private TestDataFactories() {
        // Prevent instantiation
    }

    // ==========================================================================
    // Unique Identifiers
    // ==========================================================================

    /**
     * Generate a unique ID with the given prefix.
     *
     * @param prefix the ID prefix (e.g., "capsule", "actor")
     * @return unique ID like "capsule-a1b2c3d4"
     */
    public static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a unique UUID string.
     *
     * @return UUID string
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a sequential ID (useful for ordered test data).
     *
     * @param prefix the ID prefix
     * @return sequential ID like "test-1", "test-2"
     */
    public static String sequentialId(String prefix) {
        return prefix + "-" + sequenceCounter.incrementAndGet();
    }

    /**
     * Reset the sequential counter (call between test classes if needed).
     */
    public static void resetSequence() {
        sequenceCounter.set(0);
    }

    // ==========================================================================
    // RIM Node Identifiers
    // ==========================================================================

    /**
     * Generate a RIM node identifier.
     *
     * @param type   the node type (e.g., "entity", "actor", "scenario")
     * @param domain the domain (e.g., "finance", "macro")
     * @param name   the specific name
     * @return RIM node ID like "rim:entity:finance:EURUSD"
     */
    public static String rimNodeId(String type, String domain, String name) {
        return String.format("rim:%s:%s:%s", type, domain, name);
    }

    /**
     * Generate a random RIM entity node for testing.
     *
     * @return RIM entity node ID
     */
    public static String randomRimEntity() {
        return rimNodeId("entity", randomSector(), randomTicker());
    }

    /**
     * Generate a RIM actor node.
     *
     * @param actorName the actor name
     * @return RIM actor node ID
     */
    public static String rimActorId(String actorName) {
        return rimNodeId("actor", "global", actorName);
    }

    /**
     * Generate a RIM scenario node.
     *
     * @param scenarioName the scenario name
     * @return RIM scenario node ID
     */
    public static String rimScenarioId(String scenarioName) {
        return rimNodeId("scenario", "analysis", scenarioName);
    }

    // ==========================================================================
    // Timestamps
    // ==========================================================================

    /**
     * Get the current timestamp.
     *
     * @return current Instant
     */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * Get a timestamp from N hours ago.
     *
     * @param hours number of hours ago
     * @return Instant from N hours ago
     */
    public static Instant hoursAgo(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    /**
     * Get a timestamp from N minutes ago.
     *
     * @param minutes number of minutes ago
     * @return Instant from N minutes ago
     */
    public static Instant minutesAgo(int minutes) {
        return Instant.now().minus(minutes, ChronoUnit.MINUTES);
    }

    /**
     * Get a timestamp from N days ago.
     *
     * @param days number of days ago
     * @return Instant from N days ago
     */
    public static Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    /**
     * Get a timestamp N hours in the future.
     *
     * @param hours number of hours ahead
     * @return Instant N hours from now
     */
    public static Instant hoursFromNow(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS);
    }

    /**
     * Generate a list of timestamps at regular intervals.
     *
     * @param start    starting timestamp
     * @param count    number of timestamps
     * @param interval interval between timestamps
     * @param unit     time unit for interval
     * @return list of timestamps
     */
    public static List<Instant> timestampSeries(Instant start, int count, 
            long interval, ChronoUnit unit) {
        return IntStream.range(0, count)
                .mapToObj(i -> start.plus(i * interval, unit))
                .toList();
    }

    // ==========================================================================
    // Random Data
    // ==========================================================================

    /**
     * Generate a random stress level (0.0 - 1.0).
     *
     * @return random stress value
     */
    public static double randomStress() {
        return random.nextDouble();
    }

    /**
     * Generate a random high stress level (0.7 - 1.0).
     *
     * @return random high stress value
     */
    public static double randomHighStress() {
        return 0.7 + (random.nextDouble() * 0.3);
    }

    /**
     * Generate a random low stress level (0.0 - 0.3).
     *
     * @return random low stress value
     */
    public static double randomLowStress() {
        return random.nextDouble() * 0.3;
    }

    /**
     * Generate a random confidence score (0.0 - 1.0).
     *
     * @return random confidence value
     */
    public static double randomConfidence() {
        return random.nextDouble();
    }

    /**
     * Generate a random price value.
     *
     * @param min minimum price
     * @param max maximum price
     * @return random price
     */
    public static double randomPrice(double min, double max) {
        return min + (random.nextDouble() * (max - min));
    }

    /**
     * Generate a random integer in range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (exclusive)
     * @return random integer
     */
    public static int randomInt(int min, int max) {
        return random.nextInt(max - min) + min;
    }

    /**
     * Generate a random ticker symbol from common stocks.
     *
     * @return random ticker like "AAPL"
     */
    public static String randomTicker() {
        return TICKERS.get(random.nextInt(TICKERS.size()));
    }

    /**
     * Generate a random currency pair.
     *
     * @return random currency pair like "EURUSD"
     */
    public static String randomCurrencyPair() {
        return CURRENCY_PAIRS.get(random.nextInt(CURRENCY_PAIRS.size()));
    }

    /**
     * Generate a random sector.
     *
     * @return random sector like "technology"
     */
    public static String randomSector() {
        return SECTORS.get(random.nextInt(SECTORS.size()));
    }

    /**
     * Pick a random element from a list.
     *
     * @param items the list to pick from
     * @param <T>   element type
     * @return random element
     */
    public static <T> T randomFrom(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    /**
     * Pick a random element from an array.
     *
     * @param items the array to pick from
     * @param <T>   element type
     * @return random element
     */
    @SafeVarargs
    public static <T> T randomFrom(T... items) {
        return items[random.nextInt(items.length)];
    }

    // ==========================================================================
    // Collection Helpers
    // ==========================================================================

    /**
     * Generate a list of items using a factory function.
     *
     * @param count   number of items to generate
     * @param factory function to create each item (receives index)
     * @param <T>     item type
     * @return list of generated items
     */
    public static <T> List<T> listOf(int count, java.util.function.IntFunction<T> factory) {
        return IntStream.range(0, count)
                .mapToObj(factory)
                .toList();
    }

    /**
     * Generate a map of items using key and value factories.
     *
     * @param count        number of entries
     * @param keyFactory   function to create keys
     * @param valueFactory function to create values
     * @param <K>          key type
     * @param <V>          value type
     * @return generated map
     */
    public static <K, V> Map<K, V> mapOf(int count, 
            java.util.function.IntFunction<K> keyFactory,
            java.util.function.IntFunction<V> valueFactory) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            map.put(keyFactory.apply(i), valueFactory.apply(i));
        }
        return map;
    }

    // ==========================================================================
    // Builder Pattern Support
    // ==========================================================================

    /**
     * Create a builder wrapper for fluent object construction.
     *
     * @param initial the initial object
     * @param <T>     object type
     * @return builder wrapper
     */
    public static <T> BuilderWrapper<T> builder(T initial) {
        return new BuilderWrapper<>(initial);
    }

    /**
     * Wrapper class for builder-style object construction.
     *
     * @param <T> the type being built
     */
    public static class BuilderWrapper<T> {
        private final T object;

        BuilderWrapper(T object) {
            this.object = object;
        }

        /**
         * Apply a modification to the object.
         *
         * @param modifier function to modify the object
         * @return this wrapper for chaining
         */
        public BuilderWrapper<T> with(Consumer<T> modifier) {
            modifier.accept(object);
            return this;
        }

        /**
         * Get the built object.
         *
         * @return the object
         */
        public T build() {
            return object;
        }
    }

    // ==========================================================================
    // JSON Test Data
    // ==========================================================================

    /**
     * Create a simple test JSON object as a Map.
     *
     * @return map that can be serialized to JSON
     */
    public static Map<String, Object> simpleJsonObject() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", uuid());
        json.put("name", "test-" + sequentialId("obj"));
        json.put("timestamp", now().toString());
        json.put("active", true);
        return json;
    }

    /**
     * Create a test event JSON structure.
     *
     * @param eventType the event type
     * @return map representing a test event
     */
    public static Map<String, Object> testEvent(String eventType) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", uuid());
        event.put("eventType", eventType);
        event.put("timestamp", now().toString());
        event.put("source", "butterfly-testing");
        event.put("payload", simpleJsonObject());
        return event;
    }
}

