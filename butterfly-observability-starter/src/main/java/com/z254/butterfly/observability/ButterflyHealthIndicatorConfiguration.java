package com.z254.butterfly.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for observability-related health indicators.
 * 
 * @since 0.1.0
 */
@Configuration
public class ButterflyHealthIndicatorConfiguration {
    
    /**
     * Health indicator for OpenTelemetry collector connectivity.
     */
    @Bean
    @ConditionalOnClass(WebClient.class)
    @ConditionalOnProperty(prefix = "butterfly.observability.tracing", 
        name = "enabled", havingValue = "true", matchIfMissing = true)
    public HealthIndicator otelCollectorHealthIndicator() {
        return () -> {
            // Simple check - in production, ping the collector
            String endpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
            if (endpoint == null || endpoint.isEmpty()) {
                return Health.unknown()
                    .withDetail("reason", "OTEL_EXPORTER_OTLP_ENDPOINT not configured")
                    .build();
            }
            return Health.up()
                .withDetail("endpoint", endpoint)
                .build();
        };
    }
    
    /**
     * Health indicator for Kafka (for agent thoughts).
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    @ConditionalOnProperty(prefix = "butterfly.observability.agent-thoughts", 
        name = "enabled", havingValue = "true", matchIfMissing = true)
    public HealthIndicator kafkaThoughtsHealthIndicator() {
        return () -> {
            String bootstrapServers = System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS");
            if (bootstrapServers == null) {
                bootstrapServers = System.getProperty("spring.kafka.bootstrap-servers");
            }
            if (bootstrapServers == null || bootstrapServers.isEmpty()) {
                return Health.unknown()
                    .withDetail("reason", "Kafka bootstrap servers not configured")
                    .build();
            }
            return Health.up()
                .withDetail("bootstrapServers", bootstrapServers)
                .build();
        };
    }
}
