package com.z254.butterfly.security.secret;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for secret management operations.
 * <p>
 * Provides an abstraction layer for secret storage and retrieval that can be
 * implemented by different backends:
 * <ul>
 *   <li>{@link InMemorySecretProvider} - Development/testing (default)</li>
 *   <li>Database-backed provider - Production with persistent storage</li>
 *   <li>Vault-backed provider - Enterprise with HashiCorp Vault</li>
 * </ul>
 * <p>
 * Supports hybrid secret fetching patterns:
 * <ul>
 *   <li>Startup batch loading for common secrets (lower latency)</li>
 *   <li>On-demand retrieval with short TTL for sensitive/rotating secrets</li>
 *   <li>Connector-specific credential abstraction</li>
 * </ul>
 */
public interface SecretProvider {

    /**
     * Retrieve a secret by its key path.
     *
     * @param path the path/key for the secret (e.g., "service/jwt/signing-key")
     * @return the secret value if found, empty otherwise
     */
    Optional<String> getSecret(String path);

    /**
     * Retrieve a secret with a default fallback.
     *
     * @param path the path/key for the secret
     * @param defaultValue the default value if secret is not found
     * @return the secret value or default
     */
    default String getSecretOrDefault(String path, String defaultValue) {
        return getSecret(path).orElse(defaultValue);
    }

    /**
     * Retrieve a sensitive secret with a custom TTL cache.
     * Use for highly sensitive or frequently rotating secrets.
     *
     * @param path the path/key for the secret
     * @param ttl the cache time-to-live for this specific secret
     * @return the secret value if found, empty otherwise
     */
    default Optional<String> getSensitiveSecret(String path, Duration ttl) {
        // Default implementation falls back to regular getSecret
        // Implementations can override for custom TTL behavior
        return getSecret(path);
    }

    /**
     * Bulk load secrets at startup for improved latency.
     * Secrets are loaded into cache with the default TTL.
     *
     * @param paths list of secret paths to preload
     * @return map of path to secret value for successfully loaded secrets
     */
    default Map<String, String> loadSecretsBatch(List<String> paths) {
        // Default implementation loads secrets one by one
        java.util.HashMap<String, String> result = new java.util.HashMap<>();
        for (String path : paths) {
            getSecret(path).ifPresent(value -> result.put(path, value));
        }
        return result;
    }

    /**
     * Get connector credentials for a specific connector type.
     * This provides a typed abstraction for connector authentication.
     *
     * @param connectorType the type of connector (e.g., "s3", "jira", "slack")
     * @return the connector credentials if configured
     */
    default Optional<ConnectorCredentials> getConnectorCredentials(String connectorType) {
        return Optional.empty();
    }

    /**
     * Store a secret (for providers that support write operations).
     *
     * @param path the path/key for the secret
     * @param value the secret value to store
     * @throws UnsupportedOperationException if the provider is read-only
     */
    void storeSecret(String path, String value);

    /**
     * Delete a secret (for providers that support write operations).
     *
     * @param path the path/key for the secret
     * @throws UnsupportedOperationException if the provider is read-only
     */
    void deleteSecret(String path);

    /**
     * Validate an API key secret.
     *
     * @param namespace the namespace of the service
     * @param serviceName the name of the service
     * @param secret the secret to validate
     * @return true if the secret is valid
     */
    boolean validateApiKeySecret(String namespace, String serviceName, String secret);

    /**
     * Store an API key secret.
     *
     * @param namespace the namespace of the service
     * @param serviceName the name of the service
     * @param secret the secret to store
     */
    void storeApiKeySecret(String namespace, String serviceName, String secret);

    /**
     * Revoke an API key secret.
     *
     * @param namespace the namespace of the service
     * @param serviceName the name of the service
     */
    void revokeApiKeySecret(String namespace, String serviceName);

    /**
     * Check if the provider is available and healthy.
     *
     * @return true if the provider is operational
     */
    boolean isHealthy();

    /**
     * Get the provider type identifier.
     *
     * @return the provider type (e.g., "inmemory", "vault", "database")
     */
    String getProviderType();

    /**
     * Invalidate cached secret(s) to force re-fetch.
     *
     * @param path the path to invalidate, or null to invalidate all
     */
    default void invalidateCache(String path) {
        // Default no-op for providers without caching
    }
}
