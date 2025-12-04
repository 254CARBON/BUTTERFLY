# [Epic] IDE Snippets and Live Templates

## Overview

Create IDE snippets and live templates for common BUTTERFLY patterns to speed up development and ensure consistency.

## Tasks

- [ ] Create IntelliJ IDEA live templates for BUTTERFLY patterns
- [ ] Create VS Code snippets extension/package
- [ ] Document RimNodeId usage patterns
- [ ] Document Avro schema patterns
- [ ] Document Kafka producer/consumer patterns
- [ ] Create snippet documentation and usage guide

## Acceptance Criteria

- IntelliJ users can import live templates for BUTTERFLY patterns
- VS Code users can install snippets for BUTTERFLY patterns
- Snippets cover the most common use cases (RimNodeId, Kafka, Avro)
- Snippets are documented with examples
- Snippets auto-complete with sensible placeholders

## Implementation Notes

### IntelliJ Live Templates

Create a `butterfly-templates.xml` file that can be imported:

```xml
<template name="rimnode" value="RimNodeId nodeId = RimNodeId.parse(&quot;rim:$TYPE$:$NAMESPACE$:$ID$&quot;);" 
  description="Create RimNodeId" toReformat="true" toShortenFQNames="true">
  <variable name="TYPE" expression="" defaultValue="entity" alwaysStopAt="true"/>
  <variable name="NAMESPACE" expression="" defaultValue="domain" alwaysStopAt="true"/>
  <variable name="ID" expression="" defaultValue="id" alwaysStopAt="true"/>
  <context>
    <option name="JAVA_CODE" value="true"/>
  </context>
</template>
```

### VS Code Snippets

Create `.vscode/butterfly.code-snippets`:

```json
{
  "RimNodeId Parse": {
    "scope": "java",
    "prefix": "rimnode",
    "body": [
      "RimNodeId nodeId = RimNodeId.parse(\"rim:${1:entity}:${2:namespace}:${3:id}\");"
    ],
    "description": "Create RimNodeId from string"
  }
}
```

### Snippet Categories

| Category | Snippets |
|----------|----------|
| **Identity** | RimNodeId parse, RimNodeId.of, IdempotencyKeyGenerator |
| **Kafka** | Producer config, Consumer config, KafkaTemplate |
| **Avro** | Schema field, Union type, Default value |
| **Spring** | REST controller, Service class, Repository |
| **Testing** | Unit test, Integration test, Mock setup |

### Installation

**IntelliJ:**
1. File → Manage IDE Settings → Import Settings
2. Select `ide-config/intellij/butterfly-templates.zip`

**VS Code:**
1. Copy `.vscode/butterfly.code-snippets` to your workspace
2. Or install the BUTTERFLY Snippets extension

## Related Documents

- [DX_NOTES.md](../../DX_NOTES.md) - Developer experience roadmap
- [butterfly-common/IDENTITY_AND_CONTRACTS.md](../../butterfly-common/IDENTITY_AND_CONTRACTS.md) - Contract patterns

## Labels

- `dx` - Developer Experience improvement
- `epic` - Epic-level tracking issue
- `phase-0` - Phase 0 backlog item

---

**Priority**: Low  
**Estimate**: 1 sprint  
**Dependencies**: None

