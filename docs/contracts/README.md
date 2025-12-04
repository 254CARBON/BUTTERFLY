# Contract Documentation

> Policies and guidelines for shared contracts in the BUTTERFLY ecosystem

---

## Contents

| Document | Description |
|----------|-------------|
| [FREEZE_POLICY.md](FREEZE_POLICY.md) | Contract freeze periods and approval process |

## Related Documentation

- [butterfly-common README](../../butterfly-common/README.md) - Shared library overview
- [IDENTITY_AND_CONTRACTS.md](../../butterfly-common/IDENTITY_AND_CONTRACTS.md) - Contract specifications
- [ADR-0002: Shared Contract Library](../adr/0002-shared-contract-library.md) - Architectural decision
- [ADR-0003: Canonical Identity Model](../adr/0003-canonical-identity-model.md) - RimNodeId design

## Quick Reference

### Contract Locations

| Contract | Location |
|----------|----------|
| Avro Schemas | `butterfly-common/src/main/avro/` |
| RimNodeId | `butterfly-common/src/main/java/.../identity/RimNodeId.java` |
| Kafka Helpers | `butterfly-common/src/main/java/.../RimKafkaContracts.java` |

### CI Workflows

| Workflow | Purpose |
|----------|---------|
| `contracts-guardrails.yml` | Validates schema changes, enforces freeze policy |

