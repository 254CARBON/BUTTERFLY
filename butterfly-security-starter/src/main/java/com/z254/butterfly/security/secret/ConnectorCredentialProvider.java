package com.z254.butterfly.security.secret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider for typed connector credentials.
 * <p>
 * Wraps a {@link SecretProvider} to provide convenient typed access to
 * credentials for various external connectors (AWS, databases, APIs, etc.).
 * <p>
 * Credential paths follow the convention:
 * <pre>
 * {basePath}/connectors/{connectorType}/{credentialKey}
 * </pre>
 * <p>
 * Example paths:
 * <ul>
 *   <li>{@code butterfly/connectors/s3/access-key}</li>
 *   <li>{@code butterfly/connectors/jira/api-token}</li>
 *   <li>{@code butterfly/connectors/slack/bot-token}</li>
 * </ul>
 */
public class ConnectorCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(ConnectorCredentialProvider.class);

    private static final String CONNECTORS_PATH = "connectors";
    private static final Duration SENSITIVE_SECRET_TTL = Duration.ofMinutes(1);

    private final SecretProvider secretProvider;
    private final String basePath;
    private final Map<String, ConnectorCredentials> credentialsCache = new ConcurrentHashMap<>();

    public ConnectorCredentialProvider(SecretProvider secretProvider) {
        this(secretProvider, "butterfly");
    }

    public ConnectorCredentialProvider(SecretProvider secretProvider, String basePath) {
        this.secretProvider = secretProvider;
        this.basePath = basePath;
    }

    /**
     * Get AWS credentials for S3, SQS, and other AWS services.
     *
     * @param profile the AWS profile/environment (e.g., "default", "production")
     * @return AWS credentials if configured
     */
    public Optional<ConnectorCredentials.AwsCredentials> getAwsCredentials(String profile) {
        String connectorType = "aws/" + profile;
        String cacheKey = "aws:" + profile;

        return getCachedOrLoad(cacheKey, () -> {
            String pathPrefix = buildPath(connectorType);

            Optional<String> accessKey = secretProvider.getSensitiveSecret(
                    pathPrefix + "/access-key-id", SENSITIVE_SECRET_TTL);
            Optional<String> secretKey = secretProvider.getSensitiveSecret(
                    pathPrefix + "/secret-access-key", SENSITIVE_SECRET_TTL);

            if (accessKey.isEmpty() || secretKey.isEmpty()) {
                log.debug("AWS credentials not found for profile: {}", profile);
                return Optional.empty();
            }

            String region = secretProvider.getSecretOrDefault(pathPrefix + "/region", "us-east-1");
            Optional<String> sessionToken = secretProvider.getSecret(pathPrefix + "/session-token");
            Optional<String> roleArn = secretProvider.getSecret(pathPrefix + "/role-arn");

            ConnectorCredentials.AwsCredentials.Builder builder = ConnectorCredentials.AwsCredentials.builder()
                    .accessKeyId(accessKey.get())
                    .secretAccessKey(secretKey.get())
                    .region(region);

            sessionToken.ifPresent(builder::sessionToken);
            roleArn.ifPresent(builder::roleArn);

            log.debug("Loaded AWS credentials for profile: {}", profile);
            return Optional.of(builder.build());
        }).map(c -> (ConnectorCredentials.AwsCredentials) c);
    }

    /**
     * Get AWS credentials using the default profile.
     */
    public Optional<ConnectorCredentials.AwsCredentials> getAwsCredentials() {
        return getAwsCredentials("default");
    }

    /**
     * Get database credentials.
     *
     * @param datasource the datasource name (e.g., "primary", "analytics")
     * @return database credentials if configured
     */
    public Optional<ConnectorCredentials.DatabaseCredentials> getDatabaseCredentials(String datasource) {
        String connectorType = "database/" + datasource;
        String cacheKey = "database:" + datasource;

        return getCachedOrLoad(cacheKey, () -> {
            String pathPrefix = buildPath(connectorType);

            Optional<String> username = secretProvider.getSecret(pathPrefix + "/username");
            Optional<String> password = secretProvider.getSensitiveSecret(
                    pathPrefix + "/password", SENSITIVE_SECRET_TTL);

            if (username.isEmpty() || password.isEmpty()) {
                log.debug("Database credentials not found for datasource: {}", datasource);
                return Optional.empty();
            }

            ConnectorCredentials.DatabaseCredentials.Builder builder =
                    ConnectorCredentials.DatabaseCredentials.builder()
                            .username(username.get())
                            .password(password.get());

            secretProvider.getSecret(pathPrefix + "/host").ifPresent(builder::host);
            secretProvider.getSecret(pathPrefix + "/port")
                    .map(Integer::parseInt)
                    .ifPresent(builder::port);
            secretProvider.getSecret(pathPrefix + "/database").ifPresent(builder::database);
            secretProvider.getSecret(pathPrefix + "/jdbc-url").ifPresent(builder::jdbcUrl);

            log.debug("Loaded database credentials for datasource: {}", datasource);
            return Optional.of(builder.build());
        }).map(c -> (ConnectorCredentials.DatabaseCredentials) c);
    }

    /**
     * Get API key credentials.
     *
     * @param service the service name (e.g., "jira", "pagerduty", "github")
     * @return API key credentials if configured
     */
    public Optional<ConnectorCredentials.ApiKeyCredentials> getApiKeyCredentials(String service) {
        String cacheKey = "apikey:" + service;

        return getCachedOrLoad(cacheKey, () -> {
            String pathPrefix = buildPath(service);

            Optional<String> apiKey = secretProvider.getSensitiveSecret(
                    pathPrefix + "/api-key", SENSITIVE_SECRET_TTL);

            // Fall back to alternative key names
            if (apiKey.isEmpty()) {
                apiKey = secretProvider.getSensitiveSecret(
                        pathPrefix + "/api-token", SENSITIVE_SECRET_TTL);
            }
            if (apiKey.isEmpty()) {
                apiKey = secretProvider.getSensitiveSecret(
                        pathPrefix + "/token", SENSITIVE_SECRET_TTL);
            }

            if (apiKey.isEmpty()) {
                log.debug("API key not found for service: {}", service);
                return Optional.empty();
            }

            ConnectorCredentials.ApiKeyCredentials.Builder builder =
                    ConnectorCredentials.ApiKeyCredentials.builder()
                            .connectorType(service)
                            .apiKey(apiKey.get());

            secretProvider.getSecret(pathPrefix + "/header-name").ifPresent(builder::headerName);
            secretProvider.getSecret(pathPrefix + "/prefix").ifPresent(builder::prefix);

            log.debug("Loaded API key credentials for service: {}", service);
            return Optional.of(builder.build());
        }).map(c -> (ConnectorCredentials.ApiKeyCredentials) c);
    }

    /**
     * Get OAuth credentials.
     *
     * @param provider the OAuth provider name (e.g., "slack", "github", "jira-oauth")
     * @return OAuth credentials if configured
     */
    public Optional<ConnectorCredentials.OAuthCredentials> getOAuthCredentials(String provider) {
        String cacheKey = "oauth:" + provider;

        return getCachedOrLoad(cacheKey, () -> {
            String pathPrefix = buildPath(provider);

            ConnectorCredentials.OAuthCredentials.Builder builder =
                    ConnectorCredentials.OAuthCredentials.builder()
                            .connectorType(provider);

            Optional<String> clientId = secretProvider.getSecret(pathPrefix + "/client-id");
            Optional<String> clientSecret = secretProvider.getSensitiveSecret(
                    pathPrefix + "/client-secret", SENSITIVE_SECRET_TTL);
            Optional<String> accessToken = secretProvider.getSensitiveSecret(
                    pathPrefix + "/access-token", SENSITIVE_SECRET_TTL);

            if (clientId.isEmpty() && accessToken.isEmpty()) {
                log.debug("OAuth credentials not found for provider: {}", provider);
                return Optional.empty();
            }

            clientId.ifPresent(builder::clientId);
            clientSecret.ifPresent(builder::clientSecret);
            accessToken.ifPresent(builder::accessToken);
            secretProvider.getSecret(pathPrefix + "/refresh-token").ifPresent(builder::refreshToken);
            secretProvider.getSecret(pathPrefix + "/token-url").ifPresent(builder::tokenUrl);
            secretProvider.getSecret(pathPrefix + "/scope").ifPresent(builder::scope);

            log.debug("Loaded OAuth credentials for provider: {}", provider);
            return Optional.of(builder.build());
        }).map(c -> (ConnectorCredentials.OAuthCredentials) c);
    }

    /**
     * Get basic authentication credentials.
     *
     * @param service the service name
     * @return basic auth credentials if configured
     */
    public Optional<ConnectorCredentials.BasicAuthCredentials> getBasicAuthCredentials(String service) {
        String cacheKey = "basic:" + service;

        return getCachedOrLoad(cacheKey, () -> {
            String pathPrefix = buildPath(service);

            Optional<String> username = secretProvider.getSecret(pathPrefix + "/username");
            Optional<String> password = secretProvider.getSensitiveSecret(
                    pathPrefix + "/password", SENSITIVE_SECRET_TTL);

            if (username.isEmpty() || password.isEmpty()) {
                log.debug("Basic auth credentials not found for service: {}", service);
                return Optional.empty();
            }

            log.debug("Loaded basic auth credentials for service: {}", service);
            return Optional.of(ConnectorCredentials.BasicAuthCredentials.builder()
                    .connectorType(service)
                    .username(username.get())
                    .password(password.get())
                    .build());
        }).map(c -> (ConnectorCredentials.BasicAuthCredentials) c);
    }

    /**
     * Get custom credentials as a map.
     *
     * @param connectorType the connector type
     * @param keys the credential keys to retrieve
     * @return custom credentials if configured
     */
    public Optional<ConnectorCredentials.CustomCredentials> getCustomCredentials(
            String connectorType, String... keys) {
        String cacheKey = "custom:" + connectorType;

        return getCachedOrLoad(cacheKey, () -> {
            String pathPrefix = buildPath(connectorType);
            Map<String, String> credentials = new java.util.HashMap<>();

            for (String key : keys) {
                secretProvider.getSecret(pathPrefix + "/" + key)
                        .ifPresent(value -> credentials.put(key, value));
            }

            if (credentials.isEmpty()) {
                log.debug("Custom credentials not found for connector: {}", connectorType);
                return Optional.empty();
            }

            log.debug("Loaded custom credentials for connector: {} with {} keys",
                    connectorType, credentials.size());
            return Optional.of(ConnectorCredentials.CustomCredentials.builder()
                    .connectorType(connectorType)
                    .credentials(credentials)
                    .build());
        }).map(c -> (ConnectorCredentials.CustomCredentials) c);
    }

    /**
     * Invalidate cached credentials.
     *
     * @param connectorType the connector type, or null to invalidate all
     */
    public void invalidateCredentials(String connectorType) {
        if (connectorType == null) {
            credentialsCache.clear();
            log.debug("Invalidated all cached credentials");
        } else {
            credentialsCache.entrySet().removeIf(e -> e.getKey().contains(connectorType));
            log.debug("Invalidated cached credentials for connector: {}", connectorType);
        }
    }

    /**
     * Check if the underlying secret provider is healthy.
     */
    public boolean isHealthy() {
        return secretProvider.isHealthy();
    }

    private String buildPath(String connectorType) {
        return basePath + "/" + CONNECTORS_PATH + "/" + connectorType;
    }

    private Optional<ConnectorCredentials> getCachedOrLoad(
            String cacheKey,
            java.util.function.Supplier<Optional<ConnectorCredentials>> loader) {

        ConnectorCredentials cached = credentialsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return Optional.of(cached);
        }

        Optional<ConnectorCredentials> loaded = loader.get();
        loaded.ifPresent(creds -> credentialsCache.put(cacheKey, creds));
        return loaded;
    }
}
