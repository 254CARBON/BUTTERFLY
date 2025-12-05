package com.z254.butterfly.observability;

import com.z254.butterfly.common.observability.ObservabilityAutoConfiguration;
import com.z254.butterfly.common.observability.thoughts.AgentThoughtAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

/**
 * Main auto-configuration class for BUTTERFLY Observability Starter.
 * 
 * <p>This starter provides:</p>
 * <ul>
 *   <li>Correlation ID propagation (HTTP, Kafka, WebClient)</li>
 *   <li>OpenTelemetry tracing configuration</li>
 *   <li>Structured JSON logging with logback</li>
 *   <li>Prometheus metrics export</li>
 *   <li>Agent thought visualization endpoints</li>
 *   <li>Health indicators for observability backends</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <p>Simply add the dependency to your project:</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.z254.butterfly</groupId>
 *     <artifactId>butterfly-observability-starter</artifactId>
 *     <version>0.1.0</version>
 * </dependency>
 * }</pre>
 * 
 * <h2>Configuration</h2>
 * <pre>{@code
 * butterfly:
 *   observability:
 *     enabled: true
 *     correlation-id:
 *       header-name: X-Correlation-ID
 *     tracing:
 *       enabled: true
 *     agent-thoughts:
 *       enabled: true
 * }</pre>
 * 
 * @since 0.1.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "butterfly.observability", name = "enabled", 
    havingValue = "true", matchIfMissing = true)
@Import({
    ObservabilityAutoConfiguration.class,
    AgentThoughtAutoConfiguration.class,
    ButterflyHealthIndicatorConfiguration.class
})
public class ButterflyObservabilityAutoConfiguration {
    // Configuration is imported from butterfly-common
}
