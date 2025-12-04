package com.z254.butterfly.security.secret;

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
 * This interface is designed to support future integration with HashiCorp Vault
 * for centralized secret management across the BUTTERFLY ecosystem.
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
}
