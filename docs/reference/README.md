# BUTTERFLY Reference Documentation

> Reference materials and quick lookups

**Last Updated**: 2025-12-03  
**Target Audience**: All users

---

## Overview

This section contains reference materials for quick lookups and common questions.

---

## Section Contents

| Document | Description |
|----------|-------------|
| [Glossary](glossary.md) | Terminology and definitions |
| [FAQ](faq.md) | Frequently asked questions |
| [Changelog](changelog.md) | Version history |

---

## Quick Reference

### Service Endpoints

| Service | Default URL | Health |
|---------|-------------|--------|
| CAPSULE | `http://localhost:8080` | `/actuator/health` |
| ODYSSEY | `http://localhost:8081` | `/actuator/health` |
| PERCEPTION | `http://localhost:8082` | `/actuator/health` |
| NEXUS | `http://localhost:8083` | `/actuator/health` |
| PLATO | `http://localhost:8086` | `/actuator/health` |

### API Versions

| Service | Current | Supported |
|---------|---------|-----------|
| CAPSULE | v1 | v1 |
| ODYSSEY | v1 | v1 |
| PERCEPTION | v1 | v1 |
| NEXUS | v1 | v1 |
| PLATO | v1 | v1 |

### RimNodeId Format

```
rim:<type>:<namespace>:<localId>

Examples:
rim:entity:finance:EURUSD
rim:actor:regulator:FED
rim:event:market:evt_123
```

---

## Common Links

| Resource | URL |
|----------|-----|
| GitHub | https://github.com/butterfly-org/butterfly |
| Issue Tracker | https://github.com/butterfly-org/butterfly/issues |
| Discussions | https://github.com/butterfly-org/butterfly/discussions |
| Status Page | https://status.butterfly.example.com |
| API Docs | https://api.butterfly.example.com/docs |

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Getting Started](../getting-started/README.md) | Quick start |
| [API Documentation](../api/README.md) | API reference |
| [Architecture](../architecture/README.md) | System design |

