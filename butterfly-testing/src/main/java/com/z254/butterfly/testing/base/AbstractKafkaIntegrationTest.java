package com.z254.butterfly.testing.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.testing.containers.ButterflyTestContainers;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base class for Kafka integration tests in the BUTTERFLY ecosystem.
 * 
 * <p>Provides:</p>
 * <ul>
 *   <li>Pre-configured Kafka Testcontainer</li>
 *   <li>Topic creation and cleanup utilities</li>
 *   <li>Producer and consumer helpers for test message handling</li>
 *   <li>Message assertion utilities</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * @SpringBootTest
 * class MyKafkaIntegrationTest extends AbstractKafkaIntegrationTest {
 *     
 *     @Override
 *     protected List<String> getRequiredTopics() {
 *         return List.of("my.input.topic", "my.output.topic");
 *     }
 *     
 *     @Test
 *     void shouldProcessMessage() throws Exception {
 *         // Send message
 *         sendMessage("my.input.topic", "key", "value");
 *         
 *         // Wait for processing and assert output
 *         ConsumerRecord<String, String> output = 
 *             waitForMessage("my.output.topic", Duration.ofSeconds(10));
 *         
 *         assertThat(output.value()).contains("processed");
 *     }
 * }
 * }</pre>
 * 
 * @since 0.1.0
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractKafkaIntegrationTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // Static initializer ensures containers are started before Spring context
    static {
        ButterflyTestContainers.startCoreContainers();
    }

    @Autowired(required = false)
    protected KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    private AdminClient adminClient;
    private final Map<String, KafkaConsumer<String, String>> testConsumers = new HashMap<>();

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        ButterflyTestContainers.configureKafka(registry);
        ButterflyTestContainers.configurePostgres(registry);
        ButterflyTestContainers.configureRedis(registry);
        
        // Additional Kafka test configuration
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.enable-auto-commit", () -> "false");
        registry.add("spring.kafka.producer.acks", () -> "all");
        
        // Disable security
        registry.add("butterfly.security.enabled", () -> "false");
    }

    /**
     * Get the list of topics required for this test class.
     * Override to specify topics that should be created before tests run.
     *
     * @return list of topic names
     */
    protected List<String> getRequiredTopics() {
        return Collections.emptyList();
    }

    /**
     * Get the number of partitions for test topics.
     * Override to customize partition count.
     *
     * @return number of partitions (default: 1)
     */
    protected int getTopicPartitions() {
        return 1;
    }

    /**
     * Get the replication factor for test topics.
     * Override to customize replication (1 for single-node test Kafka).
     *
     * @return replication factor (default: 1)
     */
    protected short getReplicationFactor() {
        return 1;
    }

    @BeforeEach
    void setUpKafka() throws Exception {
        initializeAdminClient();
        createRequiredTopics();
    }

    @AfterEach
    void tearDownKafka() {
        closeTestConsumers();
    }

    private void initializeAdminClient() {
        if (adminClient == null) {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, 
                    ButterflyTestContainers.getKafkaBootstrapServers());
            adminClient = AdminClient.create(props);
        }
    }

    private void createRequiredTopics() throws Exception {
        List<String> topics = getRequiredTopics();
        if (topics.isEmpty()) {
            return;
        }

        List<NewTopic> newTopics = topics.stream()
                .map(name -> new NewTopic(name, getTopicPartitions(), getReplicationFactor()))
                .toList();

        try {
            adminClient.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
            log.info("Created test topics: {}", topics);
        } catch (ExecutionException e) {
            // Topic might already exist - that's OK
            if (!e.getCause().getMessage().contains("TopicExistsException")) {
                throw e;
            }
            log.debug("Topics already exist: {}", topics);
        }
    }

    private void closeTestConsumers() {
        testConsumers.values().forEach(KafkaConsumer::close);
        testConsumers.clear();
    }

    // ==========================================================================
    // Producer Helpers
    // ==========================================================================

    /**
     * Send a message to a Kafka topic using the injected KafkaTemplate.
     *
     * @param topic the topic name
     * @param key   the message key
     * @param value the message value
     */
    protected void sendMessage(String topic, String key, String value) 
            throws InterruptedException, ExecutionException, TimeoutException {
        if (kafkaTemplate != null) {
            kafkaTemplate.send(topic, key, value).get(10, TimeUnit.SECONDS);
        } else {
            sendMessageDirect(topic, key, value);
        }
        log.debug("Sent message to {}: key={}", topic, key);
    }

    /**
     * Send a message directly without using KafkaTemplate.
     *
     * @param topic the topic name
     * @param key   the message key
     * @param value the message value
     */
    protected void sendMessageDirect(String topic, String key, String value) 
            throws InterruptedException, ExecutionException, TimeoutException {
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Send an object as JSON to a Kafka topic.
     *
     * @param topic the topic name
     * @param key   the message key
     * @param value the object to serialize as JSON
     */
    protected void sendJsonMessage(String topic, String key, Object value) throws Exception {
        String json = objectMapper.writeValueAsString(value);
        sendMessage(topic, key, json);
    }

    // ==========================================================================
    // Consumer Helpers
    // ==========================================================================

    /**
     * Wait for a single message on the specified topic.
     *
     * @param topic   the topic to consume from
     * @param timeout maximum time to wait
     * @return the consumed record, or null if timeout exceeded
     */
    protected ConsumerRecord<String, String> waitForMessage(String topic, Duration timeout) {
        KafkaConsumer<String, String> consumer = getOrCreateConsumer(topic);
        
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            if (!records.isEmpty()) {
                ConsumerRecord<String, String> record = records.iterator().next();
                log.debug("Received message from {}: key={}", topic, record.key());
                return record;
            }
        }
        
        log.warn("Timeout waiting for message on topic: {}", topic);
        return null;
    }

    /**
     * Wait for multiple messages on the specified topic.
     *
     * @param topic         the topic to consume from
     * @param expectedCount number of messages to wait for
     * @param timeout       maximum time to wait
     * @return list of consumed records
     */
    protected List<ConsumerRecord<String, String>> waitForMessages(
            String topic, int expectedCount, Duration timeout) {
        
        KafkaConsumer<String, String> consumer = getOrCreateConsumer(topic);
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        while (collected.size() < expectedCount && System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            records.forEach(collected::add);
        }
        
        log.debug("Collected {} messages from {} (expected: {})", 
                collected.size(), topic, expectedCount);
        return collected;
    }

    /**
     * Wait for a message matching a specific key.
     *
     * @param topic   the topic to consume from
     * @param key     the expected message key
     * @param timeout maximum time to wait
     * @return the matching record, or null if not found
     */
    protected ConsumerRecord<String, String> waitForMessageWithKey(
            String topic, String key, Duration timeout) {
        
        KafkaConsumer<String, String> consumer = getOrCreateConsumer(topic);
        
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
                if (Objects.equals(key, record.key())) {
                    return record;
                }
            }
        }
        
        return null;
    }

    /**
     * Parse a consumed message's value as the specified type.
     *
     * @param record      the consumed record
     * @param targetClass the class to deserialize to
     * @param <T>         the target type
     * @return the deserialized object
     */
    protected <T> T parseMessageValue(ConsumerRecord<String, String> record, Class<T> targetClass) 
            throws Exception {
        return objectMapper.readValue(record.value(), targetClass);
    }

    // ==========================================================================
    // Factory Methods
    // ==========================================================================

    /**
     * Create a new Kafka producer for direct message sending.
     *
     * @return configured Kafka producer
     */
    protected KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                ButterflyTestContainers.getKafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    /**
     * Create a new Kafka consumer for a specific topic.
     *
     * @param topic the topic to subscribe to
     * @return configured Kafka consumer
     */
    protected KafkaConsumer<String, String> createConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                ButterflyTestContainers.getKafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    private KafkaConsumer<String, String> getOrCreateConsumer(String topic) {
        return testConsumers.computeIfAbsent(topic, this::createConsumer);
    }

    // ==========================================================================
    // Topic Management
    // ==========================================================================

    /**
     * Delete a topic (useful for test cleanup).
     *
     * @param topic the topic to delete
     */
    protected void deleteTopic(String topic) throws Exception {
        adminClient.deleteTopics(Collections.singletonList(topic))
                .all().get(30, TimeUnit.SECONDS);
        log.info("Deleted topic: {}", topic);
    }

    /**
     * Check if a topic exists.
     *
     * @param topic the topic name
     * @return true if the topic exists
     */
    protected boolean topicExists(String topic) throws Exception {
        return adminClient.listTopics().names().get(10, TimeUnit.SECONDS).contains(topic);
    }
}

