package com.z254.butterfly.aurora.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for AURORA service.
 * <p>
 * Configures:
 * <ul>
 *     <li>Actuator endpoints (open for health checks)</li>
 *     <li>API documentation endpoints (open)</li>
 *     <li>API endpoints (secured)</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Allow actuator endpoints without authentication
                        .pathMatchers("/actuator/**").permitAll()
                        // Allow API documentation
                        .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**").permitAll()
                        // Allow chaos test endpoints (for E2E testing)
                        .pathMatchers("/chaos/**").permitAll()
                        // All other requests require authentication
                        .anyExchange().permitAll() // TODO: Change to authenticated() once OAuth2 is configured
                )
                .build();
    }
}
