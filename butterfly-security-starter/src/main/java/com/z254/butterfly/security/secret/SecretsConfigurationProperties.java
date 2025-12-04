package com.z254.butterfly.security.secret;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared configuration for the centralized SecretProvider beans.
 */
@ConfigurationProperties(prefix = "butterfly.security.secrets")
public class SecretsConfigurationProperties {

    /**
     * Enable secret provider auto-configuration.
     */
    private boolean enabled = true;

    /**
     * Provider to use (inmemory | vault).
     */
    private String provider = "inmemory";

    /**
     * Environment variable prefix used when resolving API key secrets.
     */
    private String envPrefix = "BUTTERFLY";

    /**
     * Whether to seed development API keys for local testing.
     */
    private boolean preloadDevApiKeys = true;

    /**
     * Default API key seeds for local development.
     */
    private List<ApiKeySeed> devApiKeys = new ArrayList<>(List.of(
            new ApiKeySeed("default", "perception-service", "dev-secret-perception"),
            new ApiKeySeed("default", "capsule-service", "dev-secret-capsule"),
            new ApiKeySeed("default", "odyssey-service", "dev-secret-odyssey"),
            new ApiKeySeed("default", "synapse-service", "dev-secret-synapse"),
            new ApiKeySeed("default", "test-service", "test-secret")
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public void setEnvPrefix(String envPrefix) {
        this.envPrefix = envPrefix;
    }

    public boolean isPreloadDevApiKeys() {
        return preloadDevApiKeys;
    }

    public void setPreloadDevApiKeys(boolean preloadDevApiKeys) {
        this.preloadDevApiKeys = preloadDevApiKeys;
    }

    public List<ApiKeySeed> getDevApiKeys() {
        return devApiKeys;
    }

    public void setDevApiKeys(List<ApiKeySeed> devApiKeys) {
        this.devApiKeys = devApiKeys;
    }

    /**
     * Seed value for a development API key.
     */
    public static class ApiKeySeed {
        private String namespace = "default";
        private String service;
        private String secret;

        public ApiKeySeed() {
        }

        public ApiKeySeed(String namespace, String service, String secret) {
            this.namespace = namespace;
            this.service = service;
            this.secret = secret;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
