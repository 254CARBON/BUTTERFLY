package com.z254.butterfly.security.secret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of {@link SecretProvider} for development and testing.
 * <p>
 * This provider:
 * <ul>
 *   <li>Stores secrets in memory (non-persistent)</li>
 *   <li>Falls back to environment variables for secret lookups</li>
 *   <li>Provides a simple API key validation mechanism</li>
 * </ul>
 * <p>
 * <b>Warning:</b> This provider is NOT suitable for production use.
 * Use a Vault-backed provider for production deployments.
 * <p>
 */
public class InMemorySecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemorySecretProvider.class);
    private static final String PROVIDER_TYPE = "inmemory";

    private final ConcurrentMap<String, String> secrets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> apiKeySecrets = new ConcurrentHashMap<>();
    private final Environment environment;
    private final SecretsConfigurationProperties properties;
    private final String envPrefix;

    public InMemorySecretProvider(Environment environment,
                                  SecretsConfigurationProperties properties) {
        this.environment = environment;
        this.properties = properties;
        this.envPrefix = properties.getEnvPrefix() != null
                ? properties.getEnvPrefix().toUpperCase(Locale.ROOT)
                : "BUTTERFLY";
        log.info("Initialized in-memory secret provider (development mode)");
        
        // Pre-populate with some default development API keys
        initializeDefaultApiKeys();
    }

    private void initializeDefaultApiKeys() {
        if (!properties.isPreloadDevApiKeys()) {
            log.debug("Skipping default API key seeding");
            return;
        }

        // Default development API keys for BUTTERFLY services
        // Format: namespace:service:secret
        properties.getDevApiKeys().forEach(seed -> {
            if (seed.getService() == null || seed.getSecret() == null) {
                return;
            }
            String namespace = seed.getNamespace() != null ? seed.getNamespace() : "default";
            storeApiKeySecret(namespace, seed.getService(), seed.getSecret());
        });
        
        log.debug("Initialized default development API keys");
    }

    @Override
    public Optional<String> getSecret(String path) {
        // Check in-memory store first
        String value = secrets.get(path);
        if (value != null) {
            return Optional.of(value);
        }

        // Fall back to environment variable
        String envKey = pathToEnvVar(path);
        String envValue = environment.getProperty(envKey);
        if (envValue != null) {
            return Optional.of(envValue);
        }

        // Try without path conversion (direct property name)
        String directValue = environment.getProperty(path);
        if (directValue != null) {
            return Optional.of(directValue);
        }

        log.debug("Secret not found for path: {}", path);
        return Optional.empty();
    }

    @Override
    public void storeSecret(String path, String value) {
        secrets.put(path, value);
        log.debug("Stored secret at path: {}", path);
    }

    @Override
    public void deleteSecret(String path) {
        secrets.remove(path);
        log.debug("Deleted secret at path: {}", path);
    }

    @Override
    public boolean validateApiKeySecret(String namespace, String serviceName, String secret) {
        String key = buildApiKeyPath(namespace, serviceName);
        String storedSecret = apiKeySecrets.get(key);
        
        if (storedSecret == null) {
            // Fall back to environment variable
            String envKey = envPrefix + "_APIKEY_" + 
                    namespace.toUpperCase(Locale.ROOT) + "_" +
                    serviceName.toUpperCase(Locale.ROOT).replace("-", "_");
            storedSecret = environment.getProperty(envKey);
        }

        if (storedSecret == null) {
            log.debug("No API key found for service: {} in namespace: {}", serviceName, namespace);
            return false;
        }

        boolean valid = storedSecret.equals(secret);
        if (!valid) {
            log.debug("API key validation failed for service: {} in namespace: {}", serviceName, namespace);
        }
        return valid;
    }

    @Override
    public void storeApiKeySecret(String namespace, String serviceName, String secret) {
        String key = buildApiKeyPath(namespace, serviceName);
        apiKeySecrets.put(key, secret);
        log.debug("Stored API key for service: {} in namespace: {}", serviceName, namespace);
    }

    @Override
    public void revokeApiKeySecret(String namespace, String serviceName) {
        String key = buildApiKeyPath(namespace, serviceName);
        apiKeySecrets.remove(key);
        log.debug("Revoked API key for service: {} in namespace: {}", serviceName, namespace);
    }

    @Override
    public boolean isHealthy() {
        return true; // In-memory provider is always healthy
    }

    @Override
    public String getProviderType() {
        return PROVIDER_TYPE;
    }

    /**
     * Convert a secret path to an environment variable name.
     * <p>
     * Example: {@code service/jwt/signing-key} → {@code SERVICE_JWT_SIGNING_KEY}
     */
    private String pathToEnvVar(String path) {
        return path.toUpperCase()
                .replace('/', '_')
                .replace('-', '_')
                .replace('.', '_');
    }

    /**
     * Build the internal key for API key storage.
     */
    private String buildApiKeyPath(String namespace, String serviceName) {
        return "apikey:" + namespace + ":" + serviceName;
    }

    /**
     * Get the count of stored secrets (for testing/debugging).
     */
    public int getSecretCount() {
        return secrets.size();
    }

    /**
     * Get the count of stored API keys (for testing/debugging).
     */
    public int getApiKeyCount() {
        return apiKeySecrets.size();
    }

    /**
     * Clear all stored secrets and API keys (for testing).
     */
    public void clear() {
        secrets.clear();
        apiKeySecrets.clear();
        initializeDefaultApiKeys();
        log.debug("Cleared all secrets and reset to defaults");
    }
}
