package com.z254.butterfly.security.secret;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Optional;

/**
 * Auto-configuration that wires a shared SecretProvider backed by either Vault or
 * the in-memory development provider.
 * <p>
 * Also provides a {@link ConnectorCredentialProvider} for typed access to connector
 * credentials stored in the secret provider.
 */
@AutoConfiguration
@EnableConfigurationProperties({SecretsConfigurationProperties.class, VaultProperties.class})
@ConditionalOnProperty(prefix = "butterfly.security.secrets", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecretProviderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecretProviderAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SecretProvider secretProvider(SecretsConfigurationProperties secretsConfig,
                                         VaultProperties vaultProperties,
                                         Environment environment,
                                         ObjectProvider<MeterRegistry> meterRegistry) {
        if ("vault".equalsIgnoreCase(secretsConfig.getProvider())) {
            log.info("Configuring Vault-backed SecretProvider");
            return new VaultSecretProvider(vaultProperties,
                    Optional.ofNullable(meterRegistry.getIfAvailable()));
        }

        log.info("Configuring in-memory SecretProvider (development/testing)");
        return new InMemorySecretProvider(environment, secretsConfig);
    }

    /**
     * Provides typed access to connector credentials via the underlying SecretProvider.
     * <p>
     * Supports AWS, database, API key, OAuth, and custom credential types.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SecretProvider.class)
    public ConnectorCredentialProvider connectorCredentialProvider(
            SecretProvider secretProvider,
            SecretsConfigurationProperties secretsConfig) {
        String basePath = secretsConfig.getBasePath() != null 
                ? secretsConfig.getBasePath() 
                : "butterfly";
        log.info("Configuring ConnectorCredentialProvider with base path: {}", basePath);
        return new ConnectorCredentialProvider(secretProvider, basePath);
    }
}
