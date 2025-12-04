package com.z254.butterfly.security.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for BUTTERFLY security.
 * <p>
 * Prefix: {@code butterfly.security}
 * <p>
 * Example configuration:
 * <pre>
 * butterfly:
 *   security:
 *     enabled: true
 *     service-name: odyssey
 *     jwt:
 *       issuer-uri: https://auth.butterfly.dev
 *       audience: butterfly-api
 *     rate-limiting:
 *       enabled: true
 *       redis-uri: redis://localhost:6379
 *       default-requests-per-minute: 1000
 *     headers:
 *       hsts-enabled: true
 *       csp-policy: "default-src 'self'"
 *     api-keys:
 *       enabled: true
 *       header-name: X-API-Key
 *     audit:
 *       enabled: true
 *       kafka-topic: governance.audit
 * </pre>
 */
@ConfigurationProperties(prefix = "butterfly.security")
public class ButterflySecurityProperties {

    /**
     * Whether security is enabled (default: true).
     */
    private boolean enabled = true;

    /**
     * Service name for security context (e.g., "odyssey", "capsule").
     */
    private String serviceName;

    /**
     * JWT configuration.
     */
    private JwtProperties jwt = new JwtProperties();

    /**
     * Rate limiting configuration.
     */
    private RateLimitingProperties rateLimiting = new RateLimitingProperties();

    /**
     * Security headers configuration.
     */
    private HeadersProperties headers = new HeadersProperties();

    /**
     * API key authentication configuration.
     */
    private ApiKeyProperties apiKeys = new ApiKeyProperties();

    /**
     * Audit logging configuration.
     */
    private AuditProperties audit = new AuditProperties();

    /**
     * Public endpoints that don't require authentication.
     */
    private List<String> publicEndpoints = List.of(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public JwtProperties getJwt() {
        return jwt;
    }

    public void setJwt(JwtProperties jwt) {
        this.jwt = jwt;
    }

    public RateLimitingProperties getRateLimiting() {
        return rateLimiting;
    }

    public void setRateLimiting(RateLimitingProperties rateLimiting) {
        this.rateLimiting = rateLimiting;
    }

    public HeadersProperties getHeaders() {
        return headers;
    }

    public void setHeaders(HeadersProperties headers) {
        this.headers = headers;
    }

    public ApiKeyProperties getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(ApiKeyProperties apiKeys) {
        this.apiKeys = apiKeys;
    }

    public AuditProperties getAudit() {
        return audit;
    }

    public void setAudit(AuditProperties audit) {
        this.audit = audit;
    }

    public List<String> getPublicEndpoints() {
        return publicEndpoints;
    }

    public void setPublicEndpoints(List<String> publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    // Nested Properties Classes

    public static class JwtProperties {
        private String issuerUri;
        private String audience = "butterfly-api";
        private String jwkSetUri;
        private List<String> trustedIssuers = List.of();

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public List<String> getTrustedIssuers() {
            return trustedIssuers;
        }

        public void setTrustedIssuers(List<String> trustedIssuers) {
            this.trustedIssuers = trustedIssuers;
        }
    }

    public static class RateLimitingProperties {
        private boolean enabled = true;
        private String redisUri = "redis://localhost:6379";
        private int defaultRequestsPerMinute = 1000;
        private int defaultRequestsPerSecond = 100;
        private Duration windowSize = Duration.ofMinutes(1);
        private boolean perUser = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRedisUri() {
            return redisUri;
        }

        public void setRedisUri(String redisUri) {
            this.redisUri = redisUri;
        }

        public int getDefaultRequestsPerMinute() {
            return defaultRequestsPerMinute;
        }

        public void setDefaultRequestsPerMinute(int defaultRequestsPerMinute) {
            this.defaultRequestsPerMinute = defaultRequestsPerMinute;
        }

        public int getDefaultRequestsPerSecond() {
            return defaultRequestsPerSecond;
        }

        public void setDefaultRequestsPerSecond(int defaultRequestsPerSecond) {
            this.defaultRequestsPerSecond = defaultRequestsPerSecond;
        }

        public Duration getWindowSize() {
            return windowSize;
        }

        public void setWindowSize(Duration windowSize) {
            this.windowSize = windowSize;
        }

        public boolean isPerUser() {
            return perUser;
        }

        public void setPerUser(boolean perUser) {
            this.perUser = perUser;
        }
    }

    public static class HeadersProperties {
        private boolean hstsEnabled = true;
        private long hstsMaxAge = 31536000; // 1 year
        private boolean hstsIncludeSubdomains = true;
        private String cspPolicy = "default-src 'self'";
        private boolean xFrameOptionsDeny = true;
        private boolean xContentTypeOptionsNosniff = true;
        private boolean xXssProtection = true;
        private String referrerPolicy = "strict-origin-when-cross-origin";

        public boolean isHstsEnabled() {
            return hstsEnabled;
        }

        public void setHstsEnabled(boolean hstsEnabled) {
            this.hstsEnabled = hstsEnabled;
        }

        public long getHstsMaxAge() {
            return hstsMaxAge;
        }

        public void setHstsMaxAge(long hstsMaxAge) {
            this.hstsMaxAge = hstsMaxAge;
        }

        public boolean isHstsIncludeSubdomains() {
            return hstsIncludeSubdomains;
        }

        public void setHstsIncludeSubdomains(boolean hstsIncludeSubdomains) {
            this.hstsIncludeSubdomains = hstsIncludeSubdomains;
        }

        public String getCspPolicy() {
            return cspPolicy;
        }

        public void setCspPolicy(String cspPolicy) {
            this.cspPolicy = cspPolicy;
        }

        public boolean isxFrameOptionsDeny() {
            return xFrameOptionsDeny;
        }

        public void setxFrameOptionsDeny(boolean xFrameOptionsDeny) {
            this.xFrameOptionsDeny = xFrameOptionsDeny;
        }

        public boolean isxContentTypeOptionsNosniff() {
            return xContentTypeOptionsNosniff;
        }

        public void setxContentTypeOptionsNosniff(boolean xContentTypeOptionsNosniff) {
            this.xContentTypeOptionsNosniff = xContentTypeOptionsNosniff;
        }

        public boolean isxXssProtection() {
            return xXssProtection;
        }

        public void setxXssProtection(boolean xXssProtection) {
            this.xXssProtection = xXssProtection;
        }

        public String getReferrerPolicy() {
            return referrerPolicy;
        }

        public void setReferrerPolicy(String referrerPolicy) {
            this.referrerPolicy = referrerPolicy;
        }
    }

    public static class ApiKeyProperties {
        private boolean enabled = false;
        private String headerName = "X-API-Key";
        private List<String> validKeys = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public List<String> getValidKeys() {
            return validKeys;
        }

        public void setValidKeys(List<String> validKeys) {
            this.validKeys = validKeys;
        }
    }

    public static class AuditProperties {
        private boolean enabled = true;
        private String kafkaTopic = "governance.audit";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKafkaTopic() {
            return kafkaTopic;
        }

        public void setKafkaTopic(String kafkaTopic) {
            this.kafkaTopic = kafkaTopic;
        }
    }
}

