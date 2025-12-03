# BUTTERFLY FAQ

> Frequently Asked Questions

**Last Updated**: 2025-12-03

---

## General

### What is BUTTERFLY?

BUTTERFLY is an enterprise platform for 4D temporal analysis, strategic cognition, sensory intelligence, and governance. It consists of several microservices that work together to capture, analyze, and act on complex data relationships over time.

### Why is it called BUTTERFLY?

The name references the "butterfly effect" - the concept that small changes can lead to significant outcomes. BUTTERFLY is designed to detect weak signals and understand how small events can cascade into major impacts.

### What are the main components?

| Component | Purpose |
|-----------|---------|
| CAPSULE | 4D temporal storage with time-travel |
| ODYSSEY | Graph-based relationship analysis |
| PERCEPTION | Signal detection and processing |
| PLATO | Governance and intelligence engines |
| NEXUS | API gateway and orchestration |

---

## Getting Started

### What are the system requirements?

**Minimum:**
- 16 GB RAM
- 4 CPU cores
- 50 GB storage
- Docker + Docker Compose

**Recommended:**
- 32 GB RAM
- 8 CPU cores
- 100 GB SSD storage
- Kubernetes cluster

### How do I install BUTTERFLY locally?

```bash
# Clone repository
git clone https://github.com/butterfly-org/butterfly.git
cd butterfly/apps

# Start with Docker Compose
docker compose up -d

# Verify health
curl http://localhost:8083/actuator/health
```

See the [Installation Guide](../getting-started/installation.md) for detailed instructions.

### Which Java version is required?

Java 21 (LTS) is required. We recommend Eclipse Temurin (Adoptium) distribution.

---

## Architecture

### What database does BUTTERFLY use?

BUTTERFLY uses multiple databases optimized for different use cases:

| Database | Service | Purpose |
|----------|---------|---------|
| Cassandra | CAPSULE, PLATO | Time-series, high-write |
| PostgreSQL | PERCEPTION | Relational data |
| JanusGraph | ODYSSEY | Graph relationships |
| Redis | CAPSULE, NEXUS | Caching, rate limiting |
| ClickHouse | PERCEPTION | Analytics |

### How do services communicate?

Services communicate via:
- **Synchronous**: REST APIs through NEXUS gateway
- **Asynchronous**: Apache Kafka events
- **Internal**: Direct HTTP (for health checks)

### What is a RimNodeId?

RimNodeId is BUTTERFLY's canonical identity format:

```
rim:<type>:<namespace>:<localId>

Examples:
rim:entity:finance:EURUSD     # Currency pair
rim:actor:regulator:FED       # Federal Reserve
rim:event:market:evt_123      # Market event
```

---

## API

### How do I authenticate?

BUTTERFLY supports two authentication methods:

1. **JWT Tokens** - For user sessions
   ```
   Authorization: Bearer <jwt_token>
   ```

2. **API Keys** - For service integration
   ```
   X-API-Key: bf_live_xxxxxxxxxxxx
   ```

See [API Authentication](../api/authentication.md) for details.

### What API format does BUTTERFLY use?

REST APIs with JSON request/response bodies. OpenAPI 3.0 specifications are available at `/api-docs` endpoint.

### Are there rate limits?

Yes, default rate limits are:

| Tier | Requests/minute | Burst |
|------|-----------------|-------|
| Free | 100 | 20 |
| Standard | 1,000 | 100 |
| Enterprise | Custom | Custom |

See [Rate Limiting](../api/rate-limiting.md) for details.

---

## CAPSULE

### What is time-travel in CAPSULE?

Time-travel allows you to query data as it existed at any point in time. This is useful for:
- Historical analysis
- Audit compliance
- Debugging data changes

### What's the difference between Current and Omniscient vantage?

- **Current**: Shows only data that was known at the query timestamp
- **Omniscient**: Shows all data including events after the query timestamp

### How long is data retained?

Default retention is configurable per entity type:
- Capsules: 1 year (configurable)
- Events: 90 days
- Audit logs: 7 years

---

## PERCEPTION

### What data sources does PERCEPTION support?

PERCEPTION can ingest from:
- Market data feeds
- News sources
- Social media (with connectors)
- Regulatory filings
- Custom webhooks
- File uploads

### What is a Trust Score?

Trust Score (0.0 to 1.0) indicates the reliability of detected information:
- 1.0: Verified, authoritative source
- 0.7+: High confidence
- 0.4-0.7: Medium confidence
- < 0.4: Low confidence, may need verification

### How does PERCEPTION handle duplicates?

PERCEPTION uses multiple deduplication strategies:
- Content hashing
- Entity matching
- Temporal proximity
- Source correlation

---

## ODYSSEY

### What graph queries can ODYSSEY perform?

- Pathfinding between entities
- Influence analysis
- Community detection
- Temporal relationship tracking
- Actor behavior patterns

### How large can the graph get?

ODYSSEY (via JanusGraph) can scale to billions of vertices and edges. Performance depends on:
- Query complexity
- Index coverage
- Hardware resources

---

## PLATO

### What are the six PLATO engines?

1. **SynthPlane**: Synthetic data and scenario modeling
2. **FeatureForge**: Feature extraction and transformation
3. **LawMiner**: Legal/regulatory requirement extraction
4. **SpecMiner**: Specification extraction
5. **ResearchOS**: Research workflow orchestration
6. **EvidencePlanner**: Verifiable execution planning

### How do I execute a plan?

```bash
curl -X POST http://localhost:8083/api/v1/plato/plans \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Analysis Plan",
    "engine": "RESEARCH_OS",
    "parameters": {"topic": "market-analysis"}
  }'
```

---

## Operations

### How do I monitor BUTTERFLY?

BUTTERFLY exposes:
- Prometheus metrics at `/actuator/prometheus`
- Health checks at `/actuator/health`
- Logs in JSON format

See [Monitoring Guide](../operations/monitoring/README.md) for dashboard setup.

### How do I scale services?

```bash
# Kubernetes
kubectl scale deployment/capsule --replicas=5 -n butterfly

# Docker Compose
docker compose up -d --scale capsule=3
```

See [Scaling Runbook](../operations/runbooks/scaling.md) for details.

### How do I backup BUTTERFLY?

Each component has specific backup procedures:
- Cassandra: Snapshot + sstableloader
- PostgreSQL: pg_dump + WAL archiving
- Redis: RDB + AOF
- Kafka: Topic mirroring

See [Disaster Recovery](../operations/runbooks/disaster-recovery.md) for procedures.

---

## Security

### Is BUTTERFLY SOC2 compliant?

Yes, BUTTERFLY is designed to meet SOC2 Type II requirements. See [Compliance](../security/compliance.md) for control mappings.

### How is data encrypted?

- **In Transit**: TLS 1.3 (minimum TLS 1.2)
- **At Rest**: AES-256 encryption
- **Database**: Native encryption per database

### How are secrets managed?

Secrets can be managed via:
- Kubernetes Secrets
- HashiCorp Vault
- AWS Secrets Manager
- Environment variables (development only)

---

## Troubleshooting

### Service won't start

1. Check logs: `kubectl logs -l app=<service> -n butterfly`
2. Verify dependencies are running
3. Check resource limits
4. Review configuration

### High latency

1. Check consumer lag for Kafka
2. Review database performance
3. Check JVM heap usage
4. Verify network connectivity

### Authentication fails

1. Verify JWT secret matches across services
2. Check token expiration
3. Confirm API key is active
4. Review CORS configuration

See [Common Issues](../operations/runbooks/common-issues.md) for more troubleshooting.

---

## Contributing

### How do I contribute?

1. Fork the repository
2. Create a feature branch
3. Make changes with tests
4. Submit a pull request

See [Contributing Guide](../development/contributing.md) for details.

### Where do I report bugs?

Open an issue on GitHub: https://github.com/butterfly-org/butterfly/issues

### How do I request features?

Use GitHub Discussions: https://github.com/butterfly-org/butterfly/discussions

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Reference Index](README.md) | Reference overview |
| [Glossary](glossary.md) | Terminology |
| [Getting Started](../getting-started/README.md) | Quick start |

