package com.z254.butterfly.security.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for HashiCorp Vault integration.
 * <p>
 * Supports multiple authentication methods:
 * <ul>
 *   <li>Token - Direct token authentication (dev/testing)</li>
 *   <li>AppRole - Machine-to-machine authentication (production)</li>
 *   <li>Kubernetes - Service account authentication (K8s environments)</li>
 * </ul>
 * <p>
 * Configuration example:
 * <pre>
 * butterfly:
 *   security:
 *     secrets:
 *       provider: vault
 *       vault:
 *         uri: https://vault.example.com:8200
 *         authentication: approle
 *         role-id: ${VAULT_ROLE_ID}
 *         secret-id: ${VAULT_SECRET_ID}
 *         base-path: secret/shared
 * </pre>
 */
@ConfigurationProperties(prefix = "butterfly.security.secrets.vault")
public class VaultProperties {

    /**
     * Vault server URI.
     */
    private String uri = "http://localhost:8200";

    /**
     * Authentication method: token, approle, or kubernetes.
     */
    private AuthMethod authentication = AuthMethod.TOKEN;

    /**
     * Vault token for token authentication.
     */
    private String token;

    /**
     * AppRole role ID for approle authentication.
     */
    private String roleId;

    /**
     * AppRole secret ID for approle authentication.
     */
    private String secretId;

    /**
     * Kubernetes auth mount path.
     */
    private String kubernetesPath = "kubernetes";

    /**
     * Kubernetes service account token path.
     */
    private String kubernetesTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    /**
     * Kubernetes role for authentication.
     */
    private String kubernetesRole;

    /**
     * Base path in Vault for stored secrets.
     */
    private String basePath = "secret/butterfly";

    /**
     * API key secrets path within base path.
     */
    private String apiKeyPath = "apikeys";

    /**
     * Connection timeout.
     */
    private Duration connectionTimeout = Duration.ofSeconds(5);

    /**
     * Read timeout.
     */
    private Duration readTimeout = Duration.ofSeconds(15);

    /**
     * Enable SSL/TLS verification.
     */
    private boolean sslVerify = true;

    /**
     * CA certificate path for SSL verification.
     */
    private String caCertPath;

    /**
     * Namespace for Vault Enterprise.
     */
    private String namespace;

    /**
     * Enable token renewal.
     */
    private boolean tokenRenewal = true;

    /**
     * Token renewal interval.
     */
    private Duration tokenRenewalInterval = Duration.ofMinutes(5);

    /**
     * Enable secret caching.
     */
    private boolean cacheEnabled = true;

    /**
     * Secret cache TTL.
     */
    private Duration cacheTtl = Duration.ofMinutes(5);

    /**
     * Maximum cache size.
     */
    private int cacheMaxSize = 1000;

    /**
     * Retry configuration.
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * Health check configuration.
     */
    private HealthConfig health = new HealthConfig();

    public enum AuthMethod {
        TOKEN,
        APPROLE,
        KUBERNETES
    }

    public static class RetryConfig {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(100);
        private Duration maxBackoff = Duration.ofSeconds(5);
        private double backoffMultiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

    public static class HealthConfig {
        private boolean enabled = true;
        private Duration checkInterval = Duration.ofSeconds(30);
        private Duration timeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getCheckInterval() {
            return checkInterval;
        }

        public void setCheckInterval(Duration checkInterval) {
            this.checkInterval = checkInterval;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    // Getters and Setters

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public AuthMethod getAuthentication() {
        return authentication;
    }

    public void setAuthentication(AuthMethod authentication) {
        this.authentication = authentication;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getKubernetesPath() {
        return kubernetesPath;
    }

    public void setKubernetesPath(String kubernetesPath) {
        this.kubernetesPath = kubernetesPath;
    }

    public String getKubernetesTokenPath() {
        return kubernetesTokenPath;
    }

    public void setKubernetesTokenPath(String kubernetesTokenPath) {
        this.kubernetesTokenPath = kubernetesTokenPath;
    }

    public String getKubernetesRole() {
        return kubernetesRole;
    }

    public void setKubernetesRole(String kubernetesRole) {
        this.kubernetesRole = kubernetesRole;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getApiKeyPath() {
        return apiKeyPath;
    }

    public void setApiKeyPath(String apiKeyPath) {
        this.apiKeyPath = apiKeyPath;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean isSslVerify() {
        return sslVerify;
    }

    public void setSslVerify(boolean sslVerify) {
        this.sslVerify = sslVerify;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public void setCaCertPath(String caCertPath) {
        this.caCertPath = caCertPath;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isTokenRenewal() {
        return tokenRenewal;
    }

    public void setTokenRenewal(boolean tokenRenewal) {
        this.tokenRenewal = tokenRenewal;
    }

    public Duration getTokenRenewalInterval() {
        return tokenRenewalInterval;
    }

    public void setTokenRenewalInterval(Duration tokenRenewalInterval) {
        this.tokenRenewalInterval = tokenRenewalInterval;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public HealthConfig getHealth() {
        return health;
    }

    public void setHealth(HealthConfig health) {
        this.health = health;
    }

    /**
     * Build the full secret path.
     */
    public String buildSecretPath(String... segments) {
        StringBuilder path = new StringBuilder(basePath);
        for (String segment : segments) {
            if (!path.toString().endsWith("/")) {
                path.append("/");
            }
            path.append(segment);
        }
        return path.toString();
    }

    /**
     * Build the API key secret path.
     */
    public String buildApiKeySecretPath(String namespace, String serviceName) {
        return buildSecretPath(apiKeyPath, namespace, serviceName);
    }
}
