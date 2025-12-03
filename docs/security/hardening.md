# BUTTERFLY Hardening Guide

> Production security configuration and best practices

**Last Updated**: 2025-12-03  
**Target Audience**: DevOps engineers, security engineers

---

## Overview

This guide provides hardening recommendations for deploying BUTTERFLY in production environments.

---

## Network Security

### TLS Configuration

```yaml
# application.yml - TLS settings
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2
    ciphers:
      - TLS_AES_256_GCM_SHA384
      - TLS_CHACHA20_POLY1305_SHA256
      - TLS_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
```

### Security Headers

```java
@Configuration
public class SecurityHeadersConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.headers()
            .contentSecurityPolicy("default-src 'self'; frame-ancestors 'none'")
            .and()
            .referrerPolicy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            .and()
            .permissionsPolicy(policy -> policy
                .geolocation().none()
                .camera().none()
                .microphone().none())
            .and()
            .httpStrictTransportSecurity()
                .includeSubDomains(true)
                .maxAgeInSeconds(31536000)
            .and()
            .frameOptions().deny()
            .xssProtection().block(true)
            .and()
            .contentTypeOptions();
    }
}
```

### Network Policies (Kubernetes)

```yaml
# network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: butterfly
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-nexus-ingress
  namespace: butterfly
spec:
  podSelector:
    matchLabels:
      app: nexus
  policyTypes:
    - Ingress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: ingress-nginx
      ports:
        - protocol: TCP
          port: 8083
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-internal-communication
  namespace: butterfly
spec:
  podSelector:
    matchLabels:
      component: service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: nexus
      ports:
        - protocol: TCP
          port: 8080
  egress:
    - to:
        - podSelector:
            matchLabels:
              component: database
```

---

## Container Security

### Pod Security Standards

```yaml
# pod-security.yaml
apiVersion: v1
kind: Pod
metadata:
  name: secure-pod
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    runAsGroup: 1000
    fsGroup: 1000
    seccompProfile:
      type: RuntimeDefault
  containers:
    - name: app
      image: butterfly/service:latest
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities:
          drop:
            - ALL
      resources:
        limits:
          cpu: "2"
          memory: "4Gi"
        requests:
          cpu: "500m"
          memory: "1Gi"
      volumeMounts:
        - name: tmp
          mountPath: /tmp
  volumes:
    - name: tmp
      emptyDir: {}
```

### Image Security

```dockerfile
# Dockerfile - secure base image
FROM eclipse-temurin:21-jre-alpine

# Create non-root user
RUN addgroup -g 1000 butterfly && \
    adduser -u 1000 -G butterfly -s /bin/sh -D butterfly

# Set ownership
COPY --chown=butterfly:butterfly target/*.jar /app/app.jar

# Run as non-root
USER butterfly

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Image Scanning

```yaml
# GitHub Actions - Trivy scan
- name: Run Trivy vulnerability scanner
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: 'butterfly/service:${{ github.sha }}'
    format: 'sarif'
    output: 'trivy-results.sarif'
    severity: 'CRITICAL,HIGH'
    exit-code: '1'
```

---

## Application Security

### Spring Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/**"))  // APIs use JWT
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder())))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        
        return http.build();
    }
    
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://app.butterfly.example.com"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### Input Validation

```java
@RestController
@Validated
public class CapsuleController {
    
    @PostMapping("/api/v1/capsules")
    public ResponseEntity<CapsuleResponse> create(
            @Valid @RequestBody CapsuleRequest request) {
        // Input validated
    }
}

@Data
public class CapsuleRequest {
    
    @NotNull(message = "scopeId is required")
    @Pattern(
        regexp = "^rim:[a-z]+:[a-z]+:[a-zA-Z0-9_-]+$",
        message = "scopeId must match RimNodeId format"
    )
    @Size(max = 256, message = "scopeId must not exceed 256 characters")
    private String scopeId;
    
    @NotNull(message = "timestamp is required")
    @PastOrPresent(message = "timestamp must not be in the future")
    private Instant timestamp;
    
    @Min(value = 1, message = "resolution must be at least 1")
    @Max(value = 86400, message = "resolution must not exceed 86400")
    private int resolution = 60;
}
```

### SQL Injection Prevention

```java
// Always use parameterized queries
@Repository
public interface CapsuleRepository extends JpaRepository<Capsule, String> {
    
    // Good: Named parameters
    @Query("SELECT c FROM Capsule c WHERE c.scopeId = :scopeId AND c.tenantId = :tenantId")
    List<Capsule> findByScopeAndTenant(
        @Param("scopeId") String scopeId, 
        @Param("tenantId") String tenantId);
    
    // Good: JPA method naming
    List<Capsule> findByScopeIdAndTimestampBetween(
        String scopeId, Instant start, Instant end);
}
```

---

## Database Security

### Cassandra

```yaml
# cassandra.yaml hardening
authenticator: PasswordAuthenticator
authorizer: CassandraAuthorizer
role_manager: CassandraRoleManager

server_encryption_options:
  internode_encryption: all
  keystore: /etc/cassandra/conf/.keystore
  keystore_password: ${CASSANDRA_KEYSTORE_PASSWORD}
  truststore: /etc/cassandra/conf/.truststore
  truststore_password: ${CASSANDRA_TRUSTSTORE_PASSWORD}
  protocol: TLS
  cipher_suites:
    - TLS_RSA_WITH_AES_256_CBC_SHA

client_encryption_options:
  enabled: true
  keystore: /etc/cassandra/conf/.keystore
  keystore_password: ${CASSANDRA_KEYSTORE_PASSWORD}
  require_client_auth: true
  truststore: /etc/cassandra/conf/.truststore
  truststore_password: ${CASSANDRA_TRUSTSTORE_PASSWORD}
```

### PostgreSQL

```sql
-- Create least-privilege user
CREATE USER butterfly WITH PASSWORD 'strong-password';
GRANT CONNECT ON DATABASE perception TO butterfly;
GRANT USAGE ON SCHEMA public TO butterfly;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO butterfly;
REVOKE CREATE ON SCHEMA public FROM butterfly;

-- Enable row-level security
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON events
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

### Redis

```conf
# redis.conf hardening
requirepass ${REDIS_PASSWORD}
rename-command FLUSHALL ""
rename-command FLUSHDB ""
rename-command DEBUG ""
rename-command CONFIG ""
protected-mode yes
bind 127.0.0.1 -::1
```

---

## Secrets Management

### External Secrets Operator

```yaml
# external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: butterfly-secrets
  namespace: butterfly
spec:
  refreshInterval: 1h
  secretStoreRef:
    kind: ClusterSecretStore
    name: vault-backend
  target:
    name: butterfly-secrets
    creationPolicy: Owner
  data:
    - secretKey: jwt-private-key
      remoteRef:
        key: secret/data/butterfly/production
        property: jwt-private-key
    - secretKey: db-password
      remoteRef:
        key: secret/data/butterfly/production
        property: db-password
```

### Secret Rotation

```bash
# Rotate JWT signing key
# 1. Generate new key pair
openssl genrsa -out jwt-key-new.pem 2048
openssl rsa -in jwt-key-new.pem -pubout -out jwt-key-new.pub

# 2. Update Vault
vault kv put secret/butterfly/production \
  jwt-private-key=@jwt-key-new.pem \
  jwt-public-key=@jwt-key-new.pub

# 3. Rolling restart services
kubectl rollout restart deployment -n butterfly

# 4. Old tokens will expire naturally (1 hour max)
```

---

## Logging Security

### Sensitive Data Filtering

```java
@Configuration
public class LoggingConfig {
    
    @Bean
    public MaskingPatternLayout maskingLayout() {
        MaskingPatternLayout layout = new MaskingPatternLayout();
        layout.addMaskPattern("\"password\"\\s*:\\s*\"[^\"]*\"");
        layout.addMaskPattern("\"apiKey\"\\s*:\\s*\"[^\"]*\"");
        layout.addMaskPattern("\"token\"\\s*:\\s*\"[^\"]*\"");
        layout.addMaskPattern("Authorization:\\s*Bearer\\s+[^\\s]+");
        return layout;
    }
}
```

### Audit Logging

```java
@Aspect
@Component
public class AuditAspect {
    
    @Around("@annotation(Audited)")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        AuditEvent event = AuditEvent.builder()
            .timestamp(Instant.now())
            .user(SecurityContextHolder.getContext().getAuthentication().getName())
            .action(pjp.getSignature().getName())
            .resource(extractResource(pjp))
            .build();
        
        try {
            Object result = pjp.proceed();
            event.setResult("SUCCESS");
            return result;
        } catch (Exception e) {
            event.setResult("FAILURE");
            event.setError(e.getMessage());
            throw e;
        } finally {
            auditLogger.log(event);
        }
    }
}
```

---

## Security Checklist

### Pre-Production

- [ ] TLS 1.3 enabled
- [ ] Security headers configured
- [ ] Network policies deployed
- [ ] Pod security standards enforced
- [ ] Image scanning enabled
- [ ] Secrets in Vault/KMS
- [ ] Database encryption enabled
- [ ] Audit logging enabled
- [ ] Input validation complete
- [ ] Dependency scan clean

### Ongoing

- [ ] Weekly vulnerability scans
- [ ] Monthly dependency updates
- [ ] Quarterly penetration tests
- [ ] Annual security review
- [ ] Continuous monitoring

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Security Overview](README.md) | Security index |
| [Security Model](security-model.md) | Auth and authz |
| [Compliance](compliance.md) | Compliance mappings |

