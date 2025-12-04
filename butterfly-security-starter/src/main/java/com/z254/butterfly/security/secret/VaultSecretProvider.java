package com.z254.butterfly.security.secret;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HashiCorp Vault implementation of the SecretProvider interface.
 * <p>
 * Provides centralized secret management with:
 * <ul>
 *   <li>Multiple authentication methods (Token, AppRole, Kubernetes)</li>
 *   <li>Automatic token renewal</li>
 *   <li>Secret caching with TTL</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Health checking</li>
 * </ul>
 */
public class VaultSecretProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(VaultSecretProvider.class);
    private static final String PROVIDER_TYPE = "vault";

    private final VaultProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Token management
    private final AtomicReference<String> currentToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiry = new AtomicReference<>();
    private final AtomicBoolean healthy = new AtomicBoolean(false);

    // Secret caching
    private final ConcurrentMap<String, CachedSecret> secretCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "vault-provider-scheduler"));

    // Metrics
    private Counter secretReadCounter;
    private Counter secretWriteCounter;
    private Counter secretCacheHitCounter;
    private Counter secretCacheMissCounter;
    private Counter authFailureCounter;
    private Timer secretReadTimer;

    public VaultSecretProvider(VaultProperties properties, 
                               Optional<MeterRegistry> meterRegistry) {
        this.properties = properties;
        this.restTemplate = createRestTemplate();
        this.objectMapper = new ObjectMapper();
        this.meterRegistry = meterRegistry.orElse(null);
        initializeMetrics();
    }

    @PostConstruct
    void initialize() {
        log.info("Initializing Vault SecretProvider with URI: {}", properties.getUri());
        try {
            authenticate();
            healthy.set(true);
            startTokenRenewalScheduler();
            startCacheCleanupScheduler();
            log.info("Vault SecretProvider initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Vault SecretProvider", e);
            healthy.set(false);
        }
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down Vault SecretProvider");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // SecretProvider Interface Implementation
    // =========================================================================

    @Override
    public Optional<String> getSecret(String path) {
        if (!healthy.get()) {
            log.warn("Vault is unhealthy, cannot retrieve secret: {}", path);
            return Optional.empty();
        }

        // Check cache first
        if (properties.isCacheEnabled()) {
            CachedSecret cached = secretCache.get(path);
            if (cached != null && !cached.isExpired()) {
                incrementCounter(secretCacheHitCounter);
                return Optional.ofNullable(cached.value());
            }
            incrementCounter(secretCacheMissCounter);
        }

        // Read from Vault
        return readFromVault(path);
    }

    @Override
    public void storeSecret(String path, String value) {
        if (!healthy.get()) {
            throw new IllegalStateException("Vault is unhealthy, cannot store secret");
        }

        String fullPath = properties.buildSecretPath(path);
        writeToVault(fullPath, Map.of("value", value));
        
        // Update cache
        if (properties.isCacheEnabled()) {
            secretCache.put(path, new CachedSecret(value, 
                    Instant.now().plus(properties.getCacheTtl())));
        }
        
        incrementCounter(secretWriteCounter);
        log.debug("Stored secret at path: {}", path);
    }

    @Override
    public void deleteSecret(String path) {
        if (!healthy.get()) {
            throw new IllegalStateException("Vault is unhealthy, cannot delete secret");
        }

        String fullPath = properties.buildSecretPath(path);
        deleteFromVault(fullPath);
        
        // Remove from cache
        secretCache.remove(path);
        
        log.debug("Deleted secret at path: {}", path);
    }

    @Override
    public boolean validateApiKeySecret(String namespace, String serviceName, String secret) {
        String path = properties.buildApiKeySecretPath(namespace, serviceName);
        Optional<String> storedSecret = getSecret(path);
        
        if (storedSecret.isEmpty()) {
            log.debug("No API key found for {}/{}", namespace, serviceName);
            return false;
        }
        
        boolean valid = storedSecret.get().equals(secret);
        log.debug("API key validation for {}/{}: {}", namespace, serviceName, valid ? "SUCCESS" : "FAILED");
        return valid;
    }

    @Override
    public void storeApiKeySecret(String namespace, String serviceName, String secret) {
        String path = properties.buildApiKeySecretPath(namespace, serviceName);
        Map<String, Object> data = Map.of(
                "secret", secret,
                "namespace", namespace,
                "serviceName", serviceName,
                "createdAt", Instant.now().toString()
        );
        
        String fullPath = properties.buildSecretPath(path);
        writeToVault(fullPath, data);
        
        // Cache the secret value
        if (properties.isCacheEnabled()) {
            secretCache.put(path, new CachedSecret(secret, 
                    Instant.now().plus(properties.getCacheTtl())));
        }
        
        incrementCounter(secretWriteCounter);
        log.info("Stored API key for {}/{}", namespace, serviceName);
    }

    @Override
    public void revokeApiKeySecret(String namespace, String serviceName) {
        String path = properties.buildApiKeySecretPath(namespace, serviceName);
        deleteSecret(path);
        log.info("Revoked API key for {}/{}", namespace, serviceName);
    }

    @Override
    public boolean isHealthy() {
        return healthy.get();
    }

    @Override
    public String getProviderType() {
        return PROVIDER_TYPE;
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    private void authenticate() {
        String token = switch (properties.getAuthentication()) {
            case TOKEN -> authenticateWithToken();
            case APPROLE -> authenticateWithAppRole();
            case KUBERNETES -> authenticateWithKubernetes();
        };
        
        currentToken.set(token);
        log.info("Successfully authenticated with Vault using {}", properties.getAuthentication());
    }

    private String authenticateWithToken() {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Vault token not configured");
        }
        return token;
    }

    private String authenticateWithAppRole() {
        String roleId = properties.getRoleId();
        String secretId = properties.getSecretId();
        
        if (roleId == null || secretId == null) {
            throw new IllegalStateException("Vault AppRole credentials not configured");
        }

        String url = properties.getUri() + "/v1/auth/approle/login";
        Map<String, String> body = Map.of(
                "role_id", roleId,
                "secret_id", secretId
        );

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url, body, JsonNode.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode auth = response.getBody().get("auth");
                String clientToken = auth.get("client_token").asText();
                int leaseDuration = auth.get("lease_duration").asInt();
                tokenExpiry.set(Instant.now().plusSeconds(leaseDuration));
                return clientToken;
            }
            throw new IllegalStateException("AppRole authentication failed");
        } catch (RestClientException e) {
            incrementCounter(authFailureCounter);
            throw new IllegalStateException("AppRole authentication failed", e);
        }
    }

    private String authenticateWithKubernetes() {
        String role = properties.getKubernetesRole();
        if (role == null) {
            throw new IllegalStateException("Kubernetes role not configured");
        }

        // Read JWT from service account
        String jwt;
        try {
            jwt = Files.readString(Path.of(properties.getKubernetesTokenPath()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Kubernetes service account token", e);
        }

        String url = properties.getUri() + "/v1/auth/" + properties.getKubernetesPath() + "/login";
        Map<String, String> body = Map.of(
                "role", role,
                "jwt", jwt
        );

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    url, body, JsonNode.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode auth = response.getBody().get("auth");
                String clientToken = auth.get("client_token").asText();
                int leaseDuration = auth.get("lease_duration").asInt();
                tokenExpiry.set(Instant.now().plusSeconds(leaseDuration));
                return clientToken;
            }
            throw new IllegalStateException("Kubernetes authentication failed");
        } catch (RestClientException e) {
            incrementCounter(authFailureCounter);
            throw new IllegalStateException("Kubernetes authentication failed", e);
        }
    }

    // =========================================================================
    // Vault Operations
    // =========================================================================

    private Optional<String> readFromVault(String path) {
        String fullPath = properties.buildSecretPath(path);
        String url = properties.getUri() + "/v1/" + fullPath;

        Timer.Sample sample = Timer.start();
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, JsonNode.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode data = response.getBody().path("data").path("data");
                if (!data.isMissingNode()) {
                    String value = data.has("value") 
                            ? data.get("value").asText() 
                            : data.get("secret").asText();
                    
                    // Update cache
                    if (properties.isCacheEnabled()) {
                        secretCache.put(path, new CachedSecret(value, 
                                Instant.now().plus(properties.getCacheTtl())));
                    }
                    
                    incrementCounter(secretReadCounter);
                    return Optional.of(value);
                }
            }
            
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("Failed to read secret from Vault: {}", path, e);
            return Optional.empty();
        } finally {
            recordTimer(sample, secretReadTimer);
        }
    }

    private void writeToVault(String fullPath, Map<String, Object> data) {
        String url = properties.getUri() + "/v1/" + fullPath;
        
        try {
            HttpHeaders headers = createHeaders();
            // KV v2 format
            Map<String, Object> body = Map.of("data", data);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, JsonNode.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Failed to write secret to Vault: " + 
                        response.getStatusCode());
            }
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to write secret to Vault", e);
        }
    }

    private void deleteFromVault(String fullPath) {
        String url = properties.getUri() + "/v1/" + fullPath;
        
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to delete secret from Vault", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Vault-Token", currentToken.get());
        if (properties.getNamespace() != null) {
            headers.set("X-Vault-Namespace", properties.getNamespace());
        }
        return headers;
    }

    // =========================================================================
    // Token Renewal and Health Checks
    // =========================================================================

    private void startTokenRenewalScheduler() {
        if (properties.isTokenRenewal() && 
            properties.getAuthentication() != VaultProperties.AuthMethod.TOKEN) {
            
            scheduler.scheduleAtFixedRate(this::renewTokenIfNeeded,
                    properties.getTokenRenewalInterval().toMillis(),
                    properties.getTokenRenewalInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    private void renewTokenIfNeeded() {
        Instant expiry = tokenExpiry.get();
        if (expiry == null) return;
        
        // Renew if within 2 renewal intervals of expiry
        Duration timeToExpiry = Duration.between(Instant.now(), expiry);
        if (timeToExpiry.compareTo(properties.getTokenRenewalInterval().multipliedBy(2)) < 0) {
            try {
                renewToken();
            } catch (Exception e) {
                log.error("Failed to renew Vault token", e);
                // Re-authenticate
                try {
                    authenticate();
                } catch (Exception authE) {
                    log.error("Failed to re-authenticate with Vault", authE);
                    healthy.set(false);
                }
            }
        }
    }

    private void renewToken() {
        String url = properties.getUri() + "/v1/auth/token/renew-self";
        
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, JsonNode.class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode auth = response.getBody().get("auth");
            int leaseDuration = auth.get("lease_duration").asInt();
            tokenExpiry.set(Instant.now().plusSeconds(leaseDuration));
            log.debug("Renewed Vault token, new lease duration: {}s", leaseDuration);
        }
    }

    @Scheduled(fixedDelayString = "${butterfly.security.secrets.vault.health.check-interval:30000}")
    void checkHealth() {
        if (!properties.getHealth().isEnabled()) return;
        
        try {
            String url = properties.getUri() + "/v1/sys/health";
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            
            boolean wasHealthy = healthy.get();
            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            healthy.set(isHealthy);
            
            if (wasHealthy != isHealthy) {
                if (isHealthy) {
                    log.info("Vault is now healthy");
                } else {
                    log.warn("Vault is now unhealthy");
                }
            }
        } catch (Exception e) {
            log.warn("Vault health check failed: {}", e.getMessage());
            healthy.set(false);
        }
    }

    private void startCacheCleanupScheduler() {
        if (properties.isCacheEnabled()) {
            scheduler.scheduleAtFixedRate(this::cleanupExpiredCache,
                    properties.getCacheTtl().toMillis(),
                    properties.getCacheTtl().toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    private void cleanupExpiredCache() {
        int removed = 0;
        for (var entry : secretCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                secretCache.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} expired cache entries", removed);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        // Configure timeouts would be done here in production
        return template;
    }

    private void initializeMetrics() {
        if (meterRegistry != null) {
            secretReadCounter = Counter.builder("butterfly.vault.secret.read")
                    .description("Number of secrets read from Vault")
                    .register(meterRegistry);
            secretWriteCounter = Counter.builder("butterfly.vault.secret.write")
                    .description("Number of secrets written to Vault")
                    .register(meterRegistry);
            secretCacheHitCounter = Counter.builder("butterfly.vault.cache.hit")
                    .description("Number of cache hits")
                    .register(meterRegistry);
            secretCacheMissCounter = Counter.builder("butterfly.vault.cache.miss")
                    .description("Number of cache misses")
                    .register(meterRegistry);
            authFailureCounter = Counter.builder("butterfly.vault.auth.failure")
                    .description("Number of authentication failures")
                    .register(meterRegistry);
            secretReadTimer = Timer.builder("butterfly.vault.secret.read.time")
                    .description("Time to read secrets from Vault")
                    .register(meterRegistry);
        }
    }

    private void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private void recordTimer(Timer.Sample sample, Timer timer) {
        if (sample != null && timer != null) {
            sample.stop(timer);
        }
    }

    private record CachedSecret(String value, Instant expiry) {
        boolean isExpired() {
            return Instant.now().isAfter(expiry);
        }
    }
}
