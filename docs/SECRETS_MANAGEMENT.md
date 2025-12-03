# Secrets Management Guide

This document outlines the secrets management approach for the BUTTERFLY ecosystem, covering local development, CI/CD, and production environments.

## Overview

BUTTERFLY services require various secrets for operation:

| Secret Type | Examples | Used By |
|-------------|----------|---------|
| Database credentials | PostgreSQL, Cassandra passwords | CAPSULE, ODYSSEY, PERCEPTION |
| Message broker | Kafka SASL credentials, Schema Registry auth | All services |
| API keys | External service integrations | PERCEPTION |
| JWT signing keys | Authentication tokens | CAPSULE, ODYSSEY |
| Encryption keys | Data-at-rest encryption | All services |
| Cloud credentials | AWS/GCP service accounts | Infrastructure |

## Principles

1. **Never commit secrets** to version control
2. **Rotate regularly** - automate credential rotation where possible
3. **Least privilege** - services should only access secrets they need
4. **Audit access** - log all secret access for compliance
5. **Encrypt in transit and at rest** - TLS for all communications

---

## Local Development

### Environment Variables

For local development, use `.env` files that are gitignored:

```bash
# .env.local (gitignored)
DATABASE_URL=jdbc:postgresql://localhost:5432/butterfly
DATABASE_USERNAME=butterfly_dev
DATABASE_PASSWORD=dev_password_123

KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_SASL_USERNAME=
KAFKA_SASL_PASSWORD=

JWT_SECRET_KEY=dev-jwt-secret-not-for-production

REDIS_HOST=localhost
REDIS_PASSWORD=
```

### Loading Environment Variables

**Spring Boot** automatically loads from `.env` files with Spring Dotenv:

```yaml
# application-local.yml
spring:
  config:
    import: optional:file:.env[.properties]
```

**Docker Compose** uses `env_file`:

```yaml
services:
  odyssey:
    env_file:
      - .env.local
```

### IDE Configuration

Configure your IDE to load environment variables from `.env.local`:

- **IntelliJ IDEA**: Run Configuration → Environment variables → Load from file
- **VS Code**: Use `.vscode/launch.json` with `envFile` property

---

## CI/CD Secrets

### GitHub Actions

Secrets are stored in GitHub repository settings and accessed via `${{ secrets.NAME }}`:

```yaml
# .github/workflows/deploy.yml
steps:
  - name: Deploy
    env:
      DATABASE_PASSWORD: ${{ secrets.DATABASE_PASSWORD_PROD }}
      JWT_SECRET: ${{ secrets.JWT_SECRET_PROD }}
```

#### Required GitHub Secrets

| Secret Name | Description | Environments |
|-------------|-------------|--------------|
| `KUBE_CONFIG_DEV` | Kubernetes config for dev cluster | dev |
| `KUBE_CONFIG_STAGE` | Kubernetes config for staging cluster | stage |
| `KUBE_CONFIG_PROD` | Kubernetes config for production cluster | prod |
| `DATABASE_PASSWORD_DEV` | PostgreSQL password | dev |
| `DATABASE_PASSWORD_STAGE` | PostgreSQL password | stage |
| `DATABASE_PASSWORD_PROD` | PostgreSQL password | prod |
| `JWT_SECRET_DEV` | JWT signing key | dev |
| `JWT_SECRET_STAGE` | JWT signing key | stage |
| `JWT_SECRET_PROD` | JWT signing key | prod |
| `SCHEMA_REGISTRY_API_KEY` | Confluent Schema Registry | all |
| `SCHEMA_REGISTRY_API_SECRET` | Confluent Schema Registry | all |
| `SONAR_TOKEN` | SonarQube analysis token | CI |
| `GITLEAKS_LICENSE` | Gitleaks scanning license | CI |

### Environment Separation

Use GitHub Environments for deployment approval and environment-specific secrets:

```yaml
jobs:
  deploy-prod:
    environment:
      name: production
      url: https://api.butterfly.254carbon.com
    steps:
      - name: Deploy
        env:
          DATABASE_URL: ${{ secrets.DATABASE_URL }}  # Environment-specific
```

---

## Production Secrets Management

### Recommended: HashiCorp Vault

For production, use a dedicated secrets manager like HashiCorp Vault:

```yaml
# Vault configuration example
vault:
  address: https://vault.254carbon.com
  auth:
    method: kubernetes
    role: butterfly-capsule
  secrets:
    - path: secret/data/butterfly/capsule/database
      key: password
      env: DATABASE_PASSWORD
    - path: secret/data/butterfly/capsule/jwt
      key: signing_key
      env: JWT_SECRET_KEY
```

### Kubernetes Secrets

For Kubernetes deployments, use sealed secrets or external secrets:

```yaml
# External Secrets Operator example
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: capsule-secrets
  namespace: butterfly
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: capsule-secrets
  data:
    - secretKey: database-password
      remoteRef:
        key: secret/data/butterfly/capsule/database
        property: password
    - secretKey: jwt-secret
      remoteRef:
        key: secret/data/butterfly/capsule/jwt
        property: signing_key
```

### AWS Secrets Manager (Alternative)

```yaml
# AWS Secrets Manager integration
aws:
  secretsmanager:
    region: us-east-1
    secrets:
      - name: butterfly/capsule/prod
        keys:
          - DATABASE_PASSWORD
          - JWT_SECRET_KEY
```

---

## Schema Registry Authentication

For Confluent Schema Registry with authentication:

```yaml
# application.yml
spring:
  kafka:
    properties:
      schema.registry.url: ${SCHEMA_REGISTRY_URL}
      basic.auth.credentials.source: USER_INFO
      basic.auth.user.info: ${SCHEMA_REGISTRY_API_KEY}:${SCHEMA_REGISTRY_API_SECRET}
```

---

## JWT Configuration

### Key Generation

Generate strong JWT signing keys:

```bash
# Generate 256-bit key for HS256
openssl rand -base64 32

# Generate RSA key pair for RS256 (recommended for production)
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

### Key Rotation

Implement key rotation with dual-key support:

```yaml
jwt:
  signing:
    current-key-id: key-2024-01
    keys:
      key-2024-01: ${JWT_SIGNING_KEY_CURRENT}
      key-2023-12: ${JWT_SIGNING_KEY_PREVIOUS}  # Still valid for verification
  verification:
    accept-key-ids:
      - key-2024-01
      - key-2023-12
```

---

## Database Credential Rotation

### Automated Rotation with Vault

```hcl
# Vault database secrets engine
resource "vault_database_secret_backend_role" "capsule" {
  backend             = vault_mount.db.path
  name                = "capsule"
  db_name             = vault_database_secret_backend_connection.postgres.name
  creation_statements = [
    "CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';",
    "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\";",
  ]
  default_ttl         = 3600  # 1 hour
  max_ttl             = 86400 # 24 hours
}
```

---

## Audit and Monitoring

### Access Logging

Enable secret access logging:

```yaml
# Vault audit log
vault audit enable file file_path=/var/log/vault/audit.log

# Application-level logging
logging:
  level:
    org.springframework.vault: DEBUG
    com.z254.butterfly.security: INFO
```

### Alerting

Set up alerts for:
- Failed secret access attempts
- Unusual access patterns
- Credential age exceeding rotation policy
- Secrets accessed from unexpected locations

---

## Security Checklist

### Before Committing

- [ ] No secrets in code or configuration files
- [ ] `.env` files are in `.gitignore`
- [ ] No hardcoded credentials in Docker Compose files
- [ ] Test data uses non-production credentials

### For Production Deployments

- [ ] All secrets stored in secrets manager
- [ ] Kubernetes secrets are sealed or external
- [ ] Service accounts have minimal permissions
- [ ] TLS enabled for all secret transmission
- [ ] Audit logging enabled
- [ ] Rotation policies configured
- [ ] Break-glass procedures documented

### Incident Response

- [ ] Process for emergency credential rotation
- [ ] Contact list for security incidents
- [ ] Runbook for compromised credentials
- [ ] Post-incident review process

---

## Quick Reference

### Adding a New Secret

1. Add to Vault/Secrets Manager with appropriate path
2. Create Kubernetes ExternalSecret or SealedSecret
3. Add to application configuration with env var reference
4. Document in this guide
5. Add to CI/CD if needed

### Rotating a Secret

1. Generate new secret value
2. Update in secrets manager
3. Deploy with dual-key support (if applicable)
4. Verify services are using new secret
5. Remove old secret after grace period

---

## Related Documentation

- [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - Deployment procedures
- [SECURITY.md](../CAPSULE/SECURITY.md) - CAPSULE security documentation
- [docs/operations/](../CAPSULE/docs/operations/) - Operational runbooks

