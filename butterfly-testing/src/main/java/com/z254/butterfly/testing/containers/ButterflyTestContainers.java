package com.z254.butterfly.testing.containers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton Testcontainers configuration for BUTTERFLY integration tests.
 * 
 * <p>This class provides thread-safe, lazy initialization of test containers
 * that are shared across all integration tests. Using singleton containers provides:</p>
 * <ul>
 *   <li>Better performance - containers start once per test suite</li>
 *   <li>Improved stability - no container restart issues between tests</li>
 *   <li>Reduced resource usage - single container instance per type</li>
 *   <li>Container reuse support for faster local development</li>
 * </ul>
 * 
 * <h2>Available Containers</h2>
 * <ul>
 *   <li><b>PostgreSQL</b> - Primary relational database for ODYSSEY, PERCEPTION, NEXUS</li>
 *   <li><b>Kafka</b> - Event backbone for async messaging across services</li>
 *   <li><b>Redis</b> - Cache layer for performance optimization</li>
 *   <li><b>Cassandra</b> - Wide-column store for CAPSULE, PLATO time-series data</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * @SpringBootTest
 * class MyIntegrationTest {
 *     static {
 *         ButterflyTestContainers.startAllContainers();
 *     }
 *     
 *     @DynamicPropertySource
 *     static void configure(DynamicPropertyRegistry registry) {
 *         ButterflyTestContainers.configurePostgres(registry);
 *         ButterflyTestContainers.configureKafka(registry);
 *     }
 * }
 * }</pre>
 * 
 * @see ContainerRegistry
 * @since 0.1.0
 */
public final class ButterflyTestContainers {

    private static final Logger log = LoggerFactory.getLogger(ButterflyTestContainers.class);

    // Container image versions - aligned with production
    private static final String POSTGRES_IMAGE = "postgres:15-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.5.2";
    private static final String REDIS_IMAGE = "redis:7.2-alpine";
    private static final String CASSANDRA_IMAGE = "cassandra:4.1";

    // Default ports
    private static final int REDIS_PORT = 6379;
    private static final int CASSANDRA_CQL_PORT = 9042;

    // Container instances - lazy initialized
    private static PostgreSQLContainer<?> postgresContainer;
    private static KafkaContainer kafkaContainer;
    private static GenericContainer<?> redisContainer;
    private static CassandraContainer<?> cassandraContainer;

    // Initialization tracking
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private ButterflyTestContainers() {
        // Prevent instantiation
    }

    // ==========================================================================
    // PostgreSQL Container
    // ==========================================================================

    /**
     * Get the singleton PostgreSQL container instance.
     * Container is started lazily on first access with reuse enabled.
     *
     * @return running PostgreSQL container
     */
    public static synchronized PostgreSQLContainer<?> getPostgresContainer() {
        if (postgresContainer == null) {
            log.info("Starting PostgreSQL container: {}", POSTGRES_IMAGE);
            postgresContainer = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                    .withDatabaseName("butterfly_test")
                    .withUsername("butterfly")
                    .withPassword("butterfly_test")
                    .withReuse(true)
                    .withLabel("com.butterfly.testcontainer", "postgres");
            postgresContainer.start();
            ContainerRegistry.register("postgres", postgresContainer);
            log.info("PostgreSQL container started: jdbc={}", postgresContainer.getJdbcUrl());
        }
        return postgresContainer;
    }

    /**
     * Get the JDBC URL for the PostgreSQL container.
     */
    public static String getPostgresJdbcUrl() {
        return getPostgresContainer().getJdbcUrl();
    }

    /**
     * Get the PostgreSQL username.
     */
    public static String getPostgresUsername() {
        return getPostgresContainer().getUsername();
    }

    /**
     * Get the PostgreSQL password.
     */
    public static String getPostgresPassword() {
        return getPostgresContainer().getPassword();
    }

    /**
     * Configure Spring properties for PostgreSQL.
     *
     * @param registry Spring dynamic property registry
     */
    public static void configurePostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ButterflyTestContainers::getPostgresJdbcUrl);
        registry.add("spring.datasource.username", ButterflyTestContainers::getPostgresUsername);
        registry.add("spring.datasource.password", ButterflyTestContainers::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    // ==========================================================================
    // Kafka Container
    // ==========================================================================

    /**
     * Get the singleton Kafka container instance.
     * Container is started lazily on first access with reuse enabled.
     *
     * @return running Kafka container
     */
    public static synchronized KafkaContainer getKafkaContainer() {
        if (kafkaContainer == null) {
            log.info("Starting Kafka container: {}", KAFKA_IMAGE);
            kafkaContainer = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                    .withReuse(true)
                    .withLabel("com.butterfly.testcontainer", "kafka");
            kafkaContainer.start();
            ContainerRegistry.register("kafka", kafkaContainer);
            log.info("Kafka container started: bootstrap-servers={}", kafkaContainer.getBootstrapServers());
        }
        return kafkaContainer;
    }

    /**
     * Get the Kafka bootstrap servers connection string.
     */
    public static String getKafkaBootstrapServers() {
        return getKafkaContainer().getBootstrapServers();
    }

    /**
     * Configure Spring properties for Kafka.
     *
     * @param registry Spring dynamic property registry
     */
    public static void configureKafka(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", ButterflyTestContainers::getKafkaBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "butterfly-test-group");
        registry.add("spring.kafka.producer.acks", () -> "all");
    }

    // ==========================================================================
    // Redis Container
    // ==========================================================================

    /**
     * Get the singleton Redis container instance.
     * Container is started lazily on first access with reuse enabled.
     *
     * <p>Redis is used for L2 cache layer in BUTTERFLY's multi-tier caching strategy:</p>
     * <ul>
     *   <li>L1: Caffeine (in-memory, per-instance)</li>
     *   <li>L2: Redis (distributed, cross-instance)</li>
     * </ul>
     *
     * @return running Redis container
     */
    public static synchronized GenericContainer<?> getRedisContainer() {
        if (redisContainer == null) {
            log.info("Starting Redis container: {}", REDIS_IMAGE);
            redisContainer = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .withCommand("redis-server", "--appendonly", "yes")
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                            .withStartupTimeout(Duration.ofSeconds(30)))
                    .withReuse(true)
                    .withLabel("com.butterfly.testcontainer", "redis");
            redisContainer.start();
            ContainerRegistry.register("redis", redisContainer);
            log.info("Redis container started: host={}, port={}", 
                    redisContainer.getHost(), redisContainer.getMappedPort(REDIS_PORT));
        }
        return redisContainer;
    }

    /**
     * Get the Redis host address.
     */
    public static String getRedisHost() {
        return getRedisContainer().getHost();
    }

    /**
     * Get the Redis mapped port.
     */
    public static Integer getRedisPort() {
        return getRedisContainer().getMappedPort(REDIS_PORT);
    }

    /**
     * Get the full Redis connection URL (host:port format).
     */
    public static String getRedisUrl() {
        return String.format("%s:%d", getRedisHost(), getRedisPort());
    }

    /**
     * Configure Spring properties for Redis.
     *
     * @param registry Spring dynamic property registry
     */
    public static void configureRedis(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", ButterflyTestContainers::getRedisHost);
        registry.add("spring.data.redis.port", ButterflyTestContainers::getRedisPort);
        registry.add("spring.data.redis.timeout", () -> "2000ms");
    }

    // ==========================================================================
    // Cassandra Container
    // ==========================================================================

    /**
     * Get the singleton Cassandra container instance.
     * Container is started lazily on first access with reuse enabled.
     *
     * <p>Cassandra is used by CAPSULE and PLATO for time-series data storage.</p>
     *
     * @return running Cassandra container
     */
    public static synchronized CassandraContainer<?> getCassandraContainer() {
        if (cassandraContainer == null) {
            log.info("Starting Cassandra container: {}", CASSANDRA_IMAGE);
            cassandraContainer = new CassandraContainer<>(DockerImageName.parse(CASSANDRA_IMAGE))
                    .withReuse(true)
                    .withLabel("com.butterfly.testcontainer", "cassandra")
                    .withStartupTimeout(Duration.ofMinutes(3));
            cassandraContainer.start();
            ContainerRegistry.register("cassandra", cassandraContainer);
            log.info("Cassandra container started: contact-point={}:{}", 
                    cassandraContainer.getHost(), cassandraContainer.getMappedPort(CASSANDRA_CQL_PORT));
        }
        return cassandraContainer;
    }

    /**
     * Get the Cassandra contact point (host).
     */
    public static String getCassandraContactPoint() {
        return getCassandraContainer().getHost();
    }

    /**
     * Get the Cassandra CQL port.
     */
    public static Integer getCassandraPort() {
        return getCassandraContainer().getMappedPort(CASSANDRA_CQL_PORT);
    }

    /**
     * Get the Cassandra local datacenter name.
     */
    public static String getCassandraLocalDatacenter() {
        return getCassandraContainer().getLocalDatacenter();
    }

    /**
     * Configure Spring properties for Cassandra.
     *
     * @param registry Spring dynamic property registry
     */
    public static void configureCassandra(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points", ButterflyTestContainers::getCassandraContactPoint);
        registry.add("spring.cassandra.port", ButterflyTestContainers::getCassandraPort);
        registry.add("spring.cassandra.local-datacenter", ButterflyTestContainers::getCassandraLocalDatacenter);
        registry.add("spring.cassandra.schema-action", () -> "create_if_not_exists");
    }

    // ==========================================================================
    // Lifecycle Management
    // ==========================================================================

    /**
     * Start all containers. Call this in a static initializer to eager-load containers.
     * 
     * <p>Example usage:</p>
     * <pre>{@code
     * static {
     *     ButterflyTestContainers.startAllContainers();
     * }
     * }</pre>
     */
    public static void startAllContainers() {
        if (initialized.compareAndSet(false, true)) {
            log.info("Initializing all BUTTERFLY test containers...");
            long startTime = System.currentTimeMillis();
            
            // Start containers in parallel would be nice, but Testcontainers 
            // handles this internally via daemon threads
            getPostgresContainer();
            getKafkaContainer();
            getRedisContainer();
            // Don't start Cassandra by default - it's slow and not all services need it
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("BUTTERFLY test containers initialized in {} ms", elapsed);
        }
    }

    /**
     * Start containers for core services (PostgreSQL, Kafka, Redis).
     * Use this for services that don't need Cassandra.
     */
    public static void startCoreContainers() {
        log.info("Starting core containers (PostgreSQL, Kafka, Redis)...");
        getPostgresContainer();
        getKafkaContainer();
        getRedisContainer();
    }

    /**
     * Start Cassandra container in addition to core containers.
     * Use this for CAPSULE and PLATO tests.
     */
    public static void startWithCassandra() {
        startCoreContainers();
        getCassandraContainer();
    }

    /**
     * Check if all core containers are running and healthy.
     *
     * @return true if PostgreSQL, Kafka, and Redis containers are running
     */
    public static boolean areCoreContainersRunning() {
        return postgresContainer != null && postgresContainer.isRunning()
                && kafkaContainer != null && kafkaContainer.isRunning()
                && redisContainer != null && redisContainer.isRunning();
    }

    /**
     * Check if all containers including Cassandra are running.
     *
     * @return true if all containers are running
     */
    public static boolean areAllContainersRunning() {
        return areCoreContainersRunning()
                && cassandraContainer != null && cassandraContainer.isRunning();
    }

    /**
     * Configure all core container properties at once.
     *
     * @param registry Spring dynamic property registry
     */
    public static void configureCoreProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        configurePostgres(registry);
        configureKafka(registry);
        configureRedis(registry);
    }

    /**
     * Configure all container properties including Cassandra.
     *
     * @param registry Spring dynamic property registry
     */
    public static void configureAllProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        configureCoreProperties(registry);
        configureCassandra(registry);
    }
}

