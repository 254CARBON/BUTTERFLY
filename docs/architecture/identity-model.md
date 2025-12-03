# BUTTERFLY Identity Model

> Canonical RimNodeId and identity resolution across the ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: Developers, architects, data engineers

---

## Overview

BUTTERFLY uses a canonical identity model based on **RimNodeId** to ensure consistent entity identification across all services. This document defines the identity format, naming conventions, and resolution patterns.

---

## RimNodeId Format

### Structure

```
rim:{nodeType}:{namespace}:{localId}

┌─────┬───────────┬────────────┬──────────────────────────────────────┐
│ rim │ nodeType  │ namespace  │ localId                              │
├─────┼───────────┼────────────┼──────────────────────────────────────┤
│     │           │            │                                      │
│ Fixed prefix    │ Domain     │ Unique identifier within namespace  │
│     │ Category  │ qualifier  │                                      │
│     │           │            │                                      │
└─────┴───────────┴────────────┴──────────────────────────────────────┘
```

### Components

| Component | Description | Constraints |
|-----------|-------------|-------------|
| `rim` | Fixed prefix | Always "rim" |
| `nodeType` | Entity category | Lowercase, alphanumeric, hyphens allowed |
| `namespace` | Domain qualifier | Lowercase, alphanumeric, hyphens allowed |
| `localId` | Unique identifier | Alphanumeric, hyphens, underscores allowed |

### Validation Rules

```java
// From butterfly-common: RimNodeId
public static final String RIM_NODE_ID_PATTERN = 
    "^rim:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-zA-Z0-9_-]+$";

// Valid examples:
// rim:entity:finance:EURUSD
// rim:actor:regulator:SEC
// rim:event:market:fed-rate-decision-2024-03

// Invalid examples:
// entity:finance:EURUSD      (missing rim: prefix)
// rim:Entity:Finance:EUR     (uppercase not allowed in type/namespace)
// rim:entity:finance:        (empty localId)
```

---

## Node Types

### Standard Node Types

| Type | Description | Example |
|------|-------------|---------|
| `entity` | Business entities, assets | `rim:entity:finance:EURUSD` |
| `actor` | Agents, organizations, individuals | `rim:actor:regulator:SEC` |
| `event` | Occurrences, happenings | `rim:event:market:earnings-AAPL-Q4` |
| `scope` | Groupings, scenarios, contexts | `rim:scope:macro:global-recession` |
| `relation` | Connections between entities | `rim:relation:supply:TSMC-AAPL` |
| `metric` | Measurable quantities | `rim:metric:volatility:SPX-30d` |
| `signal` | Detected patterns, alerts | `rim:signal:anomaly:volume-spike-123` |

### Namespace Examples

| Namespace | Domain | Example Entities |
|-----------|--------|------------------|
| `finance` | Financial instruments | Currencies, equities, bonds |
| `market` | Market events | Earnings, IPOs, splits |
| `regulator` | Regulatory bodies | SEC, FED, ECB |
| `institution` | Organizations | Banks, funds, corporates |
| `geography` | Geographic regions | Countries, regions |
| `sector` | Industry sectors | Technology, energy, healthcare |
| `macro` | Macroeconomic | GDP, inflation, employment |

---

## Identity Resolution

### Cross-Service Consistency

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      Identity Resolution Across Services                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  External System              BUTTERFLY                 Internal ID         │
│  ──────────────────          ─────────                 ─────────────        │
│                                                                              │
│  Bloomberg: EURUSD Curncy    rim:entity:finance:EURUSD  UUID in each DB    │
│  Reuters: EUR=                       │                                      │
│  ISIN: XS1234567890                 │                                      │
│                                     │                                       │
│                                     ▼                                       │
│                    ┌────────────────────────────────┐                      │
│                    │   Canonical Identity Registry   │                      │
│                    │                                │                      │
│                    │  rim:entity:finance:EURUSD     │                      │
│                    │  ├─ aliases: [EURUSD, EUR=]    │                      │
│                    │  ├─ external_ids: {...}        │                      │
│                    │  └─ metadata: {...}            │                      │
│                    └────────────────────────────────┘                      │
│                                     │                                       │
│              ┌──────────────────────┼──────────────────────┐               │
│              │                      │                      │               │
│              ▼                      ▼                      ▼               │
│         PERCEPTION             CAPSULE                ODYSSEY              │
│         (RIM node)          (scope_id)            (Entity)                │
│                                                                              │
│  All services use the same rim:entity:finance:EURUSD identifier            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Scope Identifiers

For composite or derived contexts:

```
rim-scope:{namespace}:{scopeName}

Examples:
  rim-scope:macro:global-recession-2024
  rim-scope:portfolio:equity-us-tech
  rim-scope:scenario:fed-rate-hike-50bp
```

---

## Idempotency Keys

### CAPSULE Idempotency

CAPSULEs use a composite key for deduplication:

```java
// From butterfly-common: IdempotencyKeyGenerator
public static String forSnapshot(
    RimNodeId nodeId,
    Instant timestamp,
    int resolutionSeconds,
    String vantageMode
) {
    String composite = String.format(
        "%s:%s:%d:%s",
        nodeId.toString(),
        timestamp.toString(),
        resolutionSeconds,
        vantageMode
    );
    return sha256(composite);
}
```

**Composite Key Formula:**

```
idempotencyKey = SHA256(rimNodeId + ":" + timestamp + ":" + resolution + ":" + vantageMode)

Example:
  Input: "rim:entity:finance:EURUSD:2024-12-03T10:00:00Z:60:omniscient"
  Output: "a1b2c3d4e5f6..."  (64-character hex)
```

### Resolution Values

| Resolution | Seconds | Use Case |
|------------|---------|----------|
| 1s | 1 | High-frequency tick data |
| 1m | 60 | Minute bars |
| 5m | 300 | Medium-frequency |
| 1h | 3600 | Hourly snapshots |
| 1d | 86400 | Daily snapshots |

### Vantage Modes

| Mode | Description |
|------|-------------|
| `omniscient` | Complete view with all available information |
| `observer` | Specific observer's perspective |
| `system` | System-internal view |
| `public` | Publicly available information only |

---

## Using RimNodeId in Code

### Java (butterfly-common)

```java
import com.z254.butterfly.common.identity.RimNodeId;

// Parse from string
RimNodeId nodeId = RimNodeId.parse("rim:entity:finance:EURUSD");

// Construct programmatically
RimNodeId nodeId = RimNodeId.of("entity", "finance", "EURUSD");

// Access components
String nodeType = nodeId.getNodeType();    // "entity"
String namespace = nodeId.getNamespace();  // "finance"
String localId = nodeId.getLocalId();      // "EURUSD"

// Convert to string
String idString = nodeId.toString();  // "rim:entity:finance:EURUSD"

// Validation
boolean isValid = RimNodeId.isValid("rim:entity:finance:EURUSD");  // true
boolean isValid = RimNodeId.isValid("invalid-id");  // false
```

### REST API Usage

```http
# Path parameter
GET /api/v1/capsules/rim:entity:finance:EURUSD

# Query parameter (URL-encoded)
GET /api/v1/history?scopeId=rim%3Aentity%3Afinance%3AEURUSD

# Request body
POST /api/v1/capsules
{
  "scopeId": "rim:entity:finance:EURUSD",
  ...
}
```

### Kafka Message Keys

```java
// Use RimNodeId as Kafka message key for partition affinity
ProducerRecord<String, RimFastEvent> record = new ProducerRecord<>(
    "rim.fast-path",
    nodeId.toString(),  // Key ensures same entity goes to same partition
    event
);
```

---

## Migration and Aliases

### External ID Mapping

```json
{
  "rimNodeId": "rim:entity:finance:EURUSD",
  "aliases": ["EURUSD", "EUR/USD", "EUR="],
  "externalIds": {
    "bloomberg": "EURUSD Curncy",
    "reuters": "EUR=",
    "figi": "BBG0013HHZ91"
  },
  "metadata": {
    "assetClass": "fx",
    "baseCurrency": "EUR",
    "quoteCurrency": "USD"
  }
}
```

### ID Resolution API

```http
# Resolve external ID to RimNodeId
GET /api/v1/identity/resolve?externalId=EURUSD&source=bloomberg

Response:
{
  "rimNodeId": "rim:entity:finance:EURUSD",
  "confidence": 1.0,
  "source": "bloomberg"
}
```

---

## Best Practices

### Naming Conventions

1. **Use lowercase** for nodeType and namespace
2. **Use meaningful localIds** that are human-readable when possible
3. **Avoid special characters** except hyphens and underscores
4. **Be consistent** within a namespace

### Do's and Don'ts

| Do | Don't |
|----|-------|
| `rim:entity:finance:EURUSD` | `rim:Entity:Finance:eur-usd` |
| `rim:actor:regulator:SEC` | `rim:ACTOR:reg:sec` |
| `rim:event:market:earnings-AAPL-Q4-2024` | `rim:event:market:aapl earnings q4` |

### Performance Considerations

- Store RimNodeId as string in databases
- Index on full RimNodeId for exact lookups
- Consider partial indexes on nodeType + namespace for range queries
- Use RimNodeId as Kafka key for partition affinity

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [butterfly-common](../../butterfly-common/IDENTITY_AND_CONTRACTS.md) | Implementation details |
| [Data Flow](data-flow.md) | How identity flows through services |
| [API Catalog](../api/api-catalog.md) | API usage patterns |
| [Kafka Contracts](../integration/kafka-contracts.md) | Event schemas |

