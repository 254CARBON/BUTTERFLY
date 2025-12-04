package com.z254.butterfly.security.starter;

import com.z254.butterfly.common.audit.AuditAspect;
import com.z254.butterfly.common.audit.AuditService;
import com.z254.butterfly.common.governance.PolicyEnforcementAgent;
import com.z254.butterfly.common.security.ButterflyPrincipal;
import com.z254.butterfly.common.security.ButterflyRole;
import com.z254.butterfly.common.security.JwtClaimsExtractor;
import com.z254.butterfly.common.security.SecurityContextPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Auto-configuration for BUTTERFLY security.
 * <p>
 * Provides:
 * <ul>
 *   <li>JWT Resource Server with ButterflyPrincipal integration</li>
 *   <li>Rate limiting filter (Redis-backed)</li>
 *   <li>Security headers filter (HSTS, CSP, X-Frame-Options)</li>
 *   <li>API key authentication filter</li>
 *   <li>Audit logging aspect</li>
 *   <li>Policy enforcement agent</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(ButterflySecurityProperties.class)
@ConditionalOnProperty(prefix = "butterfly.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ButterflySecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ButterflySecurityAutoConfiguration.class);

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    public static class ServletSecurityConfiguration {

        private final ButterflySecurityProperties properties;

        public ServletSecurityConfiguration(ButterflySecurityProperties properties) {
            this.properties = properties;
        }

        @Bean
        @Order(1)
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            var headersProps = properties.getHeaders();

            http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    if (headersProps.isHstsEnabled()) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                            .maxAgeInSeconds(headersProps.getHstsMaxAge())
                            .includeSubDomains(headersProps.isHstsIncludeSubdomains()));
                    }
                    if (headersProps.getCspPolicy() != null && !headersProps.getCspPolicy().isEmpty()) {
                        headers.contentSecurityPolicy(csp -> csp
                            .policyDirectives(headersProps.getCspPolicy()));
                    }
                    if (headersProps.isxFrameOptionsDeny()) {
                        headers.frameOptions(frame -> frame.deny());
                    }
                    if (headersProps.isxContentTypeOptionsNosniff()) {
                        headers.contentTypeOptions(ct -> {});
                    }
                    headers.referrerPolicy(ref -> 
                        ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                })
                .authorizeHttpRequests(auth -> {
                    // Public endpoints
                    for (String pattern : properties.getPublicEndpoints()) {
                        auth.requestMatchers(pattern).permitAll();
                    }
                    // All other requests require authentication
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .jwtAuthenticationConverter(butterflyJwtConverter())));

            log.info("BUTTERFLY Security configured for service: {}", properties.getServiceName());
            return http.build();
        }

        @Bean
        public Converter<Jwt, AbstractAuthenticationToken> butterflyJwtConverter() {
            return new ButterflyJwtAuthenticationConverter();
        }
    }

    /**
     * JWT to ButterflyPrincipal converter.
     */
    public static class ButterflyJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            // Extract ButterflyPrincipal from JWT claims
            Map<String, Object> claims = jwt.getClaims();
            ButterflyPrincipal principal = JwtClaimsExtractor.extractPrincipal(claims);

            // Set principal in thread-local context
            SecurityContextPropagator.setCurrentPrincipal(principal);

            // Convert roles to authorities
            Collection<GrantedAuthority> authorities = principal.getRoles().stream()
                    .map(ButterflyRole::toAuthority)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());

            // Add scope-based authorities
            principal.getScopes().forEach(scope ->
                    authorities.add(new SimpleGrantedAuthority(scope.toAuthority())));

            return new JwtAuthenticationToken(jwt, authorities, principal.getSubject());
        }
    }

    // === Service Beans ===

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(ButterflySecurityProperties properties) {
        String serviceName = properties.getServiceName() != null ? 
                properties.getServiceName() : "unknown";
        
        // Use logging publisher by default
        return new AuditService(serviceName, new AuditService.LoggingPublisher(), 
                properties.getAudit().isEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public AuditAspect auditAspect(AuditService auditService) {
        return new AuditAspect(auditService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyEnforcementAgent policyEnforcementAgent(ButterflySecurityProperties properties) {
        String serviceName = properties.getServiceName() != null ? 
                properties.getServiceName() : "unknown";
        return new PolicyEnforcementAgent(serviceName, null, Duration.ofMinutes(5), false);
    }

    @Bean
    @ConditionalOnMissingBean
    public ButterflySecurityContextFilter securityContextFilter(ButterflySecurityProperties properties) {
        return new ButterflySecurityContextFilter(properties.getServiceName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "butterfly.security.rate-limiting", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public RateLimitingFilter rateLimitingFilter(ButterflySecurityProperties properties) {
        var rateLimitProps = properties.getRateLimiting();
        return new RateLimitingFilter(
                rateLimitProps.getDefaultRequestsPerMinute(),
                rateLimitProps.getWindowSize(),
                rateLimitProps.isPerUser()
        );
    }
}

