package com.z254.butterfly.cortex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for CORTEX service.
 * Configures authentication and authorization for API endpoints.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private final CortexProperties cortexProperties;

    public SecurityConfig(CortexProperties cortexProperties) {
        this.cortexProperties = cortexProperties;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers("/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/info").permitAll()
                        .pathMatchers("/actuator/prometheus").permitAll()
                        .pathMatchers("/v3/api-docs/**").permitAll()
                        .pathMatchers("/swagger-ui/**").permitAll()
                        .pathMatchers("/swagger-ui.html").permitAll()
                        .pathMatchers("/webjars/**").permitAll()
                        // WebSocket endpoints (auth handled at message level)
                        .pathMatchers("/ws/**").permitAll()
                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                )
                .build();
    }
}
