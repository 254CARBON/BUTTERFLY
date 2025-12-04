# [Epic] API Documentation Generation

## Overview

Automate API documentation generation from CI to improve developer experience and ensure documentation stays in sync with code.

## Tasks

- [ ] Integrate OpenAPI spec generation into CI pipeline
- [ ] Auto-publish generated docs to documentation site on merge to main
- [ ] Add Swagger UI endpoint to each service
- [ ] Configure documentation versioning aligned with service versions

## Acceptance Criteria

- OpenAPI specs are generated automatically during CI builds
- Documentation is published to a central location after successful merges
- Each service exposes a `/swagger-ui` endpoint for interactive API exploration
- Breaking API changes are flagged in CI

## Implementation Notes

### OpenAPI Generation

Each Spring Boot service should use `springdoc-openapi`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### CI Integration

Add to each service's CI workflow:

```yaml
- name: Generate OpenAPI spec
  run: |
    mvn spring-boot:run &
    sleep 30
    curl http://localhost:8080/v3/api-docs -o openapi.json
    kill %1
```

### Swagger UI Endpoints

| Service | Swagger UI URL |
|---------|----------------|
| PERCEPTION | http://localhost:8080/swagger-ui.html |
| CAPSULE | http://localhost:8081/swagger-ui.html |
| ODYSSEY | http://localhost:8082/swagger-ui.html |
| PLATO | http://localhost:8083/swagger-ui.html |
| NEXUS | http://localhost:8084/swagger-ui.html |

## Related Documents

- [DX_NOTES.md](../../DX_NOTES.md) - Developer experience roadmap
- [docs/api/README.md](../../docs/api/README.md) - API documentation structure

## Labels

- `dx` - Developer Experience improvement
- `epic` - Epic-level tracking issue
- `phase-0` - Phase 0 backlog item

---

**Priority**: Medium  
**Estimate**: 2-3 sprints  
**Dependencies**: None

