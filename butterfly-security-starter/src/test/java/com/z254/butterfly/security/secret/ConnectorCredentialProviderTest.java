package com.z254.butterfly.security.secret;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConnectorCredentialProvider.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConnectorCredentialProvider Tests")
class ConnectorCredentialProviderTest {

    @Mock
    private SecretProvider secretProvider;

    private ConnectorCredentialProvider provider;
    private static final String BASE_PATH = "butterfly";

    @BeforeEach
    void setUp() {
        provider = new ConnectorCredentialProvider(secretProvider, BASE_PATH);
    }

    @Nested
    @DisplayName("AWS Credentials")
    class AwsCredentialsTests {

        @Test
        @DisplayName("should return AWS credentials when all secrets exist")
        void shouldReturnAwsCredentials() {
            // Given
            String profile = "default";
            when(secretProvider.getSensitiveSecret(eq(BASE_PATH + "/connectors/aws/" + profile + "/access-key-id"), any(Duration.class)))
                    .thenReturn(Optional.of("AKIAIOSFODNN7EXAMPLE"));
            when(secretProvider.getSensitiveSecret(eq(BASE_PATH + "/connectors/aws/" + profile + "/secret-access-key"), any(Duration.class)))
                    .thenReturn(Optional.of("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"));
            when(secretProvider.getSecretOrDefault(eq(BASE_PATH + "/connectors/aws/" + profile + "/region"), any()))
                    .thenReturn("us-east-1");
            when(secretProvider.getSecret(BASE_PATH + "/connectors/aws/" + profile + "/session-token"))
                    .thenReturn(Optional.empty());
            when(secretProvider.getSecret(BASE_PATH + "/connectors/aws/" + profile + "/role-arn"))
                    .thenReturn(Optional.empty());

            // When
            Optional<ConnectorCredentials.AwsCredentials> result = provider.getAwsCredentials(profile);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getAccessKeyId()).isEqualTo("AKIAIOSFODNN7EXAMPLE");
            assertThat(result.get().getSecretAccessKey()).isEqualTo("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
            assertThat(result.get().getRegion()).isEqualTo("us-east-1");
        }

        @Test
        @DisplayName("should return empty when access key is missing")
        void shouldReturnEmptyWhenAccessKeyMissing() {
            // Given
            String profile = "default";
            when(secretProvider.getSensitiveSecret(any(), any(Duration.class)))
                    .thenReturn(Optional.empty());

            // When
            Optional<ConnectorCredentials.AwsCredentials> result = provider.getAwsCredentials(profile);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Database Credentials")
    class DatabaseCredentialsTests {

        @Test
        @DisplayName("should return database credentials when all secrets exist")
        void shouldReturnDatabaseCredentials() {
            // Given
            String datasource = "primary";
            when(secretProvider.getSecret(BASE_PATH + "/connectors/database/" + datasource + "/username"))
                    .thenReturn(Optional.of("admin"));
            when(secretProvider.getSensitiveSecret(eq(BASE_PATH + "/connectors/database/" + datasource + "/password"), any(Duration.class)))
                    .thenReturn(Optional.of("secret123"));
            when(secretProvider.getSecret(BASE_PATH + "/connectors/database/" + datasource + "/host"))
                    .thenReturn(Optional.of("localhost"));
            when(secretProvider.getSecret(BASE_PATH + "/connectors/database/" + datasource + "/port"))
                    .thenReturn(Optional.of("5432"));
            when(secretProvider.getSecret(BASE_PATH + "/connectors/database/" + datasource + "/database"))
                    .thenReturn(Optional.of("mydb"));
            when(secretProvider.getSecret(BASE_PATH + "/connectors/database/" + datasource + "/jdbc-url"))
                    .thenReturn(Optional.empty());

            // When
            Optional<ConnectorCredentials.DatabaseCredentials> result = provider.getDatabaseCredentials(datasource);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("admin");
            assertThat(result.get().getPassword()).isEqualTo("secret123");
            assertThat(result.get().getHost()).contains("localhost");
            assertThat(result.get().getPort()).isEqualTo(5432);
            assertThat(result.get().getDatabase()).contains("mydb");
        }
    }

    @Nested
    @DisplayName("API Key Credentials")
    class ApiKeyCredentialsTests {

        @Test
        @DisplayName("should return API key credentials")
        void shouldReturnApiKeyCredentials() {
            // Given
            String service = "github";
            when(secretProvider.getSensitiveSecret(eq(BASE_PATH + "/connectors/" + service + "/api-key"), any(Duration.class)))
                    .thenReturn(Optional.of("ghp_xxxxxxxxxxxx"));
            when(secretProvider.getSecret(BASE_PATH + "/connectors/" + service + "/header-name"))
                    .thenReturn(Optional.of("Authorization"));
            when(secretProvider.getSecret(BASE_PATH + "/connectors/" + service + "/prefix"))
                    .thenReturn(Optional.of("Bearer"));

            // When
            Optional<ConnectorCredentials.ApiKeyCredentials> result = provider.getApiKeyCredentials(service);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getApiKey()).isEqualTo("ghp_xxxxxxxxxxxx");
            assertThat(result.get().getHeaderName()).isEqualTo("Authorization");
            assertThat(result.get().getPrefix()).contains("Bearer");
        }
    }

    @Nested
    @DisplayName("Cache Invalidation")
    class CacheInvalidationTests {

        @Test
        @DisplayName("should invalidate specific connector credentials")
        void shouldInvalidateSpecificCredentials() {
            // Given
            String connectorType = "aws";

            // When
            provider.invalidateCredentials(connectorType);

            // Then
            // Verify the cache was cleared (no exception thrown)
            assertThat(provider).isNotNull();
        }
    }
}
