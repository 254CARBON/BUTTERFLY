# BUTTERFLY API Versioning

> API versioning strategy and compatibility guidelines

**Last Updated**: 2025-12-03  
**Target Audience**: API consumers, developers

---

## Overview

BUTTERFLY uses URL-based API versioning to ensure backward compatibility while enabling evolution of the API surface.

---

## Versioning Strategy

### URL-Based Versioning

All API endpoints include a version number in the URL path:

```
/api/v1/capsules
/api/v1/specs
/api/v1/temporal/slice/{nodeId}
```

### Version Format

```
v{major}

Where:
  major = Major version number (1, 2, 3, ...)
```

### Current Versions

| Service | Current Version | Status |
|---------|-----------------|--------|
| PERCEPTION | v1 | Stable |
| CAPSULE | v1 | Stable |
| ODYSSEY | v1 | Stable |
| PLATO | v1 | Stable |
| NEXUS | v1 | Stable |

---

## Compatibility Guarantees

### What Constitutes a Breaking Change

The following changes require a major version increment:

| Change Type | Breaking? | Example |
|-------------|-----------|---------|
| Remove endpoint | Yes | DELETE `/api/v1/legacy` |
| Remove required field | Yes | Remove `scopeId` from request |
| Change field type | Yes | Change `id` from string to number |
| Rename field | Yes | Rename `nodeId` to `entityId` |
| Change error codes | Yes | Change `NOT_FOUND` to `MISSING` |
| Change authentication | Yes | Remove API key support |

### Non-Breaking Changes

The following changes are backward compatible:

| Change Type | Breaking? | Example |
|-------------|-----------|---------|
| Add endpoint | No | Add POST `/api/v1/new-resource` |
| Add optional field | No | Add optional `metadata` field |
| Add response field | No | Add `createdBy` to response |
| Deprecate (not remove) | No | Mark endpoint as deprecated |
| Add enum value | No | Add new status value |
| Improve error messages | No | Clearer error text |

---

## Deprecation Policy

### Deprecation Timeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Deprecation Timeline                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Feature/Endpoint lifecycle:                                                │
│                                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │   Active    │───▶│ Deprecated  │───▶│  Warning    │───▶│  Removed    │ │
│  │             │    │             │    │   Period    │    │             │ │
│  │  Normal     │    │  6 months   │    │  3 months   │    │  v(N+1)     │ │
│  │  operation  │    │  notice     │    │  errors     │    │  release    │ │
│  └─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘ │
│                                                                              │
│  Total deprecation period: Minimum 9 months before removal                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Deprecation Headers

Deprecated endpoints include warning headers:

```http
HTTP/1.1 200 OK
Deprecation: true
Sunset: Sat, 01 Jun 2025 00:00:00 GMT
Link: </api/v2/capsules>; rel="successor-version"
Warning: 299 - "This endpoint is deprecated. Use /api/v2/capsules instead."
```

### Deprecation Response Example

```json
{
  "data": {...},
  "meta": {
    "requestId": "req-abc123",
    "deprecation": {
      "deprecated": true,
      "sunset": "2025-06-01T00:00:00Z",
      "alternative": "/api/v2/capsules",
      "message": "This endpoint will be removed on 2025-06-01. Please migrate to /api/v2/capsules"
    }
  }
}
```

---

## Migration Guide

### When a New Version Releases

1. **Review changelog** for breaking changes
2. **Test in staging** against new version
3. **Update client code** for new endpoints/fields
4. **Monitor deprecation warnings** in current version
5. **Switch to new version** before sunset date

### Example Migration: v1 to v2

```python
# v1 code
response = client.get("/api/v1/capsules", params={"node_id": "rim:entity:finance:EURUSD"})

# v2 code (hypothetical changes)
response = client.get("/api/v2/capsules", params={"scope_id": "rim:entity:finance:EURUSD"})
```

### Parallel Version Support

During migration periods, both versions are available:

```bash
# v1 (deprecated)
curl http://localhost:8081/api/v1/capsules

# v2 (current)
curl http://localhost:8081/api/v2/capsules
```

---

## Version Discovery

### OpenAPI Spec

Each version has its own OpenAPI specification:

```bash
# v1 spec
curl http://localhost:8081/v3/api-docs/v1

# v2 spec (when available)
curl http://localhost:8081/v3/api-docs/v2
```

### Version Metadata

```http
GET /api/versions

Response:
{
  "versions": [
    {
      "version": "v1",
      "status": "stable",
      "default": true,
      "deprecation": null
    },
    {
      "version": "v2",
      "status": "beta",
      "default": false,
      "deprecation": null
    }
  ],
  "current": "v1",
  "latest": "v1"
}
```

---

## Best Practices

### For API Consumers

1. **Always specify version** explicitly in URLs
2. **Monitor deprecation headers** in responses
3. **Subscribe to changelog** notifications
4. **Test against beta versions** before they become stable
5. **Have migration plan** ready before sunset dates

### For Version Management

```python
# Good: Explicit version
API_VERSION = "v1"
BASE_URL = f"http://api.butterfly.example.com/api/{API_VERSION}"

# Bad: No version (may break unexpectedly)
BASE_URL = "http://api.butterfly.example.com/api/capsules"
```

### Version Configuration

```yaml
# config.yaml
butterfly:
  api:
    version: v1
    base_url: http://localhost:8081
    
# Support version override
butterfly:
  api:
    version: ${BUTTERFLY_API_VERSION:-v1}
```

---

## Changelog

### v1 (Current - Stable)

**Released**: 2024-11-01

Initial stable release with:
- CAPSULE CRUD operations
- History queries
- PERCEPTION event detection
- PLATO spec management
- ODYSSEY state queries
- NEXUS temporal fusion

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [API Overview](README.md) | API section overview |
| [API Catalog](api-catalog.md) | All endpoints |
| [Changelog](../reference/changelog.md) | Version history |

