# {MODULE_NAME}

> **Purpose**: One or two sentences describing what this module is responsible for.  
> **Owner**: Team or role accountable for this module.  
> **Tier**: Critical | Important | Supporting

---

## 1. Role and Responsibilities

- High-level description of what this module does.
- Domains or capabilities it supports.
- Any explicit non-goals.

## 2. Public Interfaces

### REST APIs (if any)

- Base path and summary (e.g., `/api/v1/{domain}/...`).
- Link to the relevant OpenAPI spec or API documentation.

### Messaging / Kafka Topics (if any)

- Topics produced and consumed, including key/value schemas.
- Link to Avro schemas in `butterfly-common` if applicable.

### Configuration

- Primary configuration keys (e.g., `service.{domain}.*`).
- Environment variables.

## 3. Dependencies

### Internal Modules

- Other modules this one depends on.

### External Systems

- Datastores, queues, caches, or third-party services.

## 4. Configuration and Environment

- Required environment variables.
- Profiles or Spring Boot configuration sections.
- Local development notes (how to run this module on its own).

## 5. Observability

- Key metrics (Micrometer metric names, SLOs).
- Important log categories or logger names.
- Traces or spans of interest.

## 6. Failure Modes and Runbooks

- Typical failure modes and where they surface.
- Links to relevant runbooks under `docs/runbooks/` or `{service}/docs/runbooks/`.

## 7. Testing

- Unit, integration, and end-to-end test expectations for this module.
- How to run the most important test suites locally.
- Coverage targets.

## 8. Roadmap and Notes

- Known gaps and planned improvements specific to this module.
- Pointers to engineering roadmaps if applicable.

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Parent Service README](../README.md) | Service overview |
| [Architecture](../docs/architecture/) | Architecture documentation |
| [Operations](../docs/operations/) | Operational guides |

