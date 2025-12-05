package com.z254.butterfly.security.secret;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for connector credentials providing typed access to authentication data.
 * <p>
 * Supports various credential types commonly used by external connectors:
 * <ul>
 *   <li>{@link AwsCredentials} - AWS access key/secret key pairs</li>
 *   <li>{@link DatabaseCredentials} - Database connection credentials</li>
 *   <li>{@link ApiKeyCredentials} - Simple API key authentication</li>
 *   <li>{@link OAuthCredentials} - OAuth2 client credentials</li>
 *   <li>{@link BasicAuthCredentials} - HTTP Basic authentication</li>
 * </ul>
 */
public sealed class ConnectorCredentials permits
        ConnectorCredentials.AwsCredentials,
        ConnectorCredentials.DatabaseCredentials,
        ConnectorCredentials.ApiKeyCredentials,
        ConnectorCredentials.OAuthCredentials,
        ConnectorCredentials.BasicAuthCredentials,
        ConnectorCredentials.CustomCredentials {

    private final String connectorType;
    private final Instant retrievedAt;
    private final Instant expiresAt;
    private final Map<String, String> metadata;

    protected ConnectorCredentials(String connectorType, Instant expiresAt, Map<String, String> metadata) {
        this.connectorType = Objects.requireNonNull(connectorType, "connectorType must not be null");
        this.retrievedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public String getConnectorType() {
        return connectorType;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public Optional<Instant> getExpiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * AWS credentials for S3, SQS, and other AWS services.
     */
    public static final class AwsCredentials extends ConnectorCredentials {
        private final String accessKeyId;
        private final String secretAccessKey;
        private final String sessionToken;
        private final String region;
        private final String roleArn;

        private AwsCredentials(Builder builder) {
            super("aws", builder.expiresAt, builder.metadata);
            this.accessKeyId = Objects.requireNonNull(builder.accessKeyId, "accessKeyId must not be null");
            this.secretAccessKey = Objects.requireNonNull(builder.secretAccessKey, "secretAccessKey must not be null");
            this.sessionToken = builder.sessionToken;
            this.region = builder.region != null ? builder.region : "us-east-1";
            this.roleArn = builder.roleArn;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public String getSecretAccessKey() {
            return secretAccessKey;
        }

        public Optional<String> getSessionToken() {
            return Optional.ofNullable(sessionToken);
        }

        public String getRegion() {
            return region;
        }

        public Optional<String> getRoleArn() {
            return Optional.ofNullable(roleArn);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String accessKeyId;
            private String secretAccessKey;
            private String sessionToken;
            private String region;
            private String roleArn;
            private Instant expiresAt;
            private Map<String, String> metadata;

            public Builder accessKeyId(String accessKeyId) {
                this.accessKeyId = accessKeyId;
                return this;
            }

            public Builder secretAccessKey(String secretAccessKey) {
                this.secretAccessKey = secretAccessKey;
                return this;
            }

            public Builder sessionToken(String sessionToken) {
                this.sessionToken = sessionToken;
                return this;
            }

            public Builder region(String region) {
                this.region = region;
                return this;
            }

            public Builder roleArn(String roleArn) {
                this.roleArn = roleArn;
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public AwsCredentials build() {
                return new AwsCredentials(this);
            }
        }
    }

    /**
     * Database connection credentials.
     */
    public static final class DatabaseCredentials extends ConnectorCredentials {
        private final String username;
        private final String password;
        private final String host;
        private final int port;
        private final String database;
        private final String jdbcUrl;
        private final Map<String, String> connectionProperties;

        private DatabaseCredentials(Builder builder) {
            super("database", builder.expiresAt, builder.metadata);
            this.username = Objects.requireNonNull(builder.username, "username must not be null");
            this.password = Objects.requireNonNull(builder.password, "password must not be null");
            this.host = builder.host;
            this.port = builder.port;
            this.database = builder.database;
            this.jdbcUrl = builder.jdbcUrl;
            this.connectionProperties = builder.connectionProperties != null
                    ? Map.copyOf(builder.connectionProperties) : Map.of();
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public Optional<String> getHost() {
            return Optional.ofNullable(host);
        }

        public int getPort() {
            return port;
        }

        public Optional<String> getDatabase() {
            return Optional.ofNullable(database);
        }

        public Optional<String> getJdbcUrl() {
            return Optional.ofNullable(jdbcUrl);
        }

        public Map<String, String> getConnectionProperties() {
            return connectionProperties;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String username;
            private String password;
            private String host;
            private int port;
            private String database;
            private String jdbcUrl;
            private Map<String, String> connectionProperties;
            private Instant expiresAt;
            private Map<String, String> metadata;

            public Builder username(String username) {
                this.username = username;
                return this;
            }

            public Builder password(String password) {
                this.password = password;
                return this;
            }

            public Builder host(String host) {
                this.host = host;
                return this;
            }

            public Builder port(int port) {
                this.port = port;
                return this;
            }

            public Builder database(String database) {
                this.database = database;
                return this;
            }

            public Builder jdbcUrl(String jdbcUrl) {
                this.jdbcUrl = jdbcUrl;
                return this;
            }

            public Builder connectionProperties(Map<String, String> connectionProperties) {
                this.connectionProperties = connectionProperties;
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public DatabaseCredentials build() {
                return new DatabaseCredentials(this);
            }
        }
    }

    /**
     * API key credentials for services using simple API key authentication.
     */
    public static final class ApiKeyCredentials extends ConnectorCredentials {
        private final String apiKey;
        private final String headerName;
        private final String prefix;

        private ApiKeyCredentials(Builder builder) {
            super(builder.connectorType != null ? builder.connectorType : "api-key",
                    builder.expiresAt, builder.metadata);
            this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey must not be null");
            this.headerName = builder.headerName != null ? builder.headerName : "X-API-Key";
            this.prefix = builder.prefix;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getHeaderName() {
            return headerName;
        }

        public Optional<String> getPrefix() {
            return Optional.ofNullable(prefix);
        }

        /**
         * Get the full header value including prefix if configured.
         */
        public String getHeaderValue() {
            return prefix != null ? prefix + " " + apiKey : apiKey;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String connectorType;
            private String apiKey;
            private String headerName;
            private String prefix;
            private Instant expiresAt;
            private Map<String, String> metadata;

            public Builder connectorType(String connectorType) {
                this.connectorType = connectorType;
                return this;
            }

            public Builder apiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public Builder headerName(String headerName) {
                this.headerName = headerName;
                return this;
            }

            public Builder prefix(String prefix) {
                this.prefix = prefix;
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public ApiKeyCredentials build() {
                return new ApiKeyCredentials(this);
            }
        }
    }

    /**
     * OAuth2 credentials for services using OAuth2 authentication.
     */
    public static final class OAuthCredentials extends ConnectorCredentials {
        private final String clientId;
        private final String clientSecret;
        private final String accessToken;
        private final String refreshToken;
        private final String tokenUrl;
        private final String scope;

        private OAuthCredentials(Builder builder) {
            super(builder.connectorType != null ? builder.connectorType : "oauth",
                    builder.expiresAt, builder.metadata);
            this.clientId = builder.clientId;
            this.clientSecret = builder.clientSecret;
            this.accessToken = builder.accessToken;
            this.refreshToken = builder.refreshToken;
            this.tokenUrl = builder.tokenUrl;
            this.scope = builder.scope;
        }

        public Optional<String> getClientId() {
            return Optional.ofNullable(clientId);
        }

        public Optional<String> getClientSecret() {
            return Optional.ofNullable(clientSecret);
        }

        public Optional<String> getAccessToken() {
            return Optional.ofNullable(accessToken);
        }

        public Optional<String> getRefreshToken() {
            return Optional.ofNullable(refreshToken);
        }

        public Optional<String> getTokenUrl() {
            return Optional.ofNullable(tokenUrl);
        }

        public Optional<String> getScope() {
            return Optional.ofNullable(scope);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String connectorType;
            private String clientId;
            private String clientSecret;
            private String accessToken;
            private String refreshToken;
            private String tokenUrl;
            private String scope;
            private Instant expiresAt;
            private Map<String, String> metadata;

            public Builder connectorType(String connectorType) {
                this.connectorType = connectorType;
                return this;
            }

            public Builder clientId(String clientId) {
                this.clientId = clientId;
                return this;
            }

            public Builder clientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
                return this;
            }

            public Builder accessToken(String accessToken) {
                this.accessToken = accessToken;
                return this;
            }

            public Builder refreshToken(String refreshToken) {
                this.refreshToken = refreshToken;
                return this;
            }

            public Builder tokenUrl(String tokenUrl) {
                this.tokenUrl = tokenUrl;
                return this;
            }

            public Builder scope(String scope) {
                this.scope = scope;
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public OAuthCredentials build() {
                return new OAuthCredentials(this);
            }
        }
    }

    /**
     * Basic HTTP authentication credentials.
     */
    public static final class BasicAuthCredentials extends ConnectorCredentials {
        private final String username;
        private final String password;

        private BasicAuthCredentials(Builder builder) {
            super(builder.connectorType != null ? builder.connectorType : "basic-auth",
                    builder.expiresAt, builder.metadata);
            this.username = Objects.requireNonNull(builder.username, "username must not be null");
            this.password = Objects.requireNonNull(builder.password, "password must not be null");
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        /**
         * Get the Base64-encoded credentials for HTTP Basic Auth header.
         */
        public String getEncodedCredentials() {
            String credentials = username + ":" + password;
            return java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        }

        /**
         * Get the full Authorization header value.
         */
        public String getAuthorizationHeader() {
            return "Basic " + getEncodedCredentials();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String connectorType;
            private String username;
            private String password;
            private Instant expiresAt;
            private Map<String, String> metadata;

            public Builder connectorType(String connectorType) {
                this.connectorType = connectorType;
                return this;
            }

            public Builder username(String username) {
                this.username = username;
                return this;
            }

            public Builder password(String password) {
                this.password = password;
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public BasicAuthCredentials build() {
                return new BasicAuthCredentials(this);
            }
        }
    }

    /**
     * Custom credentials for connectors with non-standard authentication.
     */
    public static final class CustomCredentials extends ConnectorCredentials {
        private final Map<String, String> credentials;

        private CustomCredentials(Builder builder) {
            super(Objects.requireNonNull(builder.connectorType, "connectorType must not be null"),
                    builder.expiresAt, builder.metadata);
            this.credentials = builder.credentials != null
                    ? Map.copyOf(builder.credentials) : Map.of();
        }

        public Map<String, String> getCredentials() {
            return credentials;
        }

        public Optional<String> get(String key) {
            return Optional.ofNullable(credentials.get(key));
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String connectorType;
            private Map<String, String> credentials;
            private Instant expiresAt;
            private Map<String, String> metadata;

            public Builder connectorType(String connectorType) {
                this.connectorType = connectorType;
                return this;
            }

            public Builder credentials(Map<String, String> credentials) {
                this.credentials = credentials;
                return this;
            }

            public Builder credential(String key, String value) {
                if (this.credentials == null) {
                    this.credentials = new java.util.HashMap<>();
                }
                this.credentials.put(key, value);
                return this;
            }

            public Builder expiresAt(Instant expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public CustomCredentials build() {
                return new CustomCredentials(this);
            }
        }
    }
}
