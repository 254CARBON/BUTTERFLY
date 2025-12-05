package com.z254.butterfly.cortex;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Test configuration for CORTEX integration tests.
 * Provides testcontainers for Redis and Kafka.
 * Disables security for integration tests.
 */
@TestConfiguration
@EnableWebFluxSecurity
@EnableAutoConfiguration(excludeName = {
    "com.z254.butterfly.common.telemetry.TelemetryAutoConfiguration"
})
public class CortexTestConfiguration {

    private static final int REDIS_PORT = 6379;
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka:7.4.0";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(
            DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT)
            .withReuse(true);

    @SuppressWarnings("resource")
    private static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(
            DockerImageName.parse(KAFKA_IMAGE))
            .withReuse(true);

    static {
        REDIS_CONTAINER.start();
        KAFKA_CONTAINER.start();
        
        // Set system properties for Spring to pick up - Redis
        System.setProperty("spring.data.redis.host", REDIS_CONTAINER.getHost());
        System.setProperty("spring.data.redis.port", 
                String.valueOf(REDIS_CONTAINER.getMappedPort(REDIS_PORT)));
        
        // Set system properties for Spring to pick up - Kafka
        System.setProperty("spring.kafka.bootstrap-servers", 
                KAFKA_CONTAINER.getBootstrapServers());
    }

    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS_CONTAINER.getHost(),
                REDIS_CONTAINER.getMappedPort(REDIS_PORT));
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        RedisSerializationContext<String, String> context = 
                RedisSerializationContext.<String, String>newSerializationContext(
                        new StringRedisSerializer())
                        .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }

    public static String getKafkaBootstrapServers() {
        return KAFKA_CONTAINER.getBootstrapServers();
    }

    public static String getRedisHost() {
        return REDIS_CONTAINER.getHost();
    }

    public static int getRedisPort() {
        return REDIS_CONTAINER.getMappedPort(REDIS_PORT);
    }

    /**
     * Test security configuration that permits all requests.
     * Overrides the main SecurityConfig for integration tests.
     */
    @Bean
    @Primary
    @Order(-1)
    public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .anyExchange().permitAll()
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }
}
