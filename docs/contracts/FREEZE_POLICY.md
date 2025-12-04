# Contract Freeze Policy

> Stability-first approach to shared contracts during performance tuning phases

**Last Updated**: 2025-12-04  
**Status**: Active  
**Applies To**: butterfly-common (Avro schemas, RimNodeId, Kafka contracts)

---

## Overview

During critical development phases (such as performance tuning or major feature releases), we enforce a **contract freeze** to ensure stability across all BUTTERFLY services. This policy governs how changes to shared contracts are managed during freeze periods.

## What is Frozen

During a freeze period, the following components in `butterfly-common` require explicit approval:

| Component | Location | Description |
|-----------|----------|-------------|
| **Avro Schemas** | `src/main/avro/*.avsc` | Event contracts for Kafka messaging |
| **RimNodeId** | `src/main/java/.../identity/RimNodeId.java` | Canonical identity format |
| **IdempotencyKeyGenerator** | `src/main/java/.../identity/IdempotencyKeyGenerator.java` | Deterministic key generation |
| **RimKafkaContracts** | `src/main/java/.../RimKafkaContracts.java` | Kafka producer/consumer configuration |

## Current Freeze Period

| Phase | Start Date | End Date | Status |
|-------|------------|----------|--------|
| Phase 0: Alignment & Contract Freeze | Week 0 | Week 2 | **ACTIVE** |

## Approval Process

### For Contributors

1. **Identify contract change**: If your PR modifies any files listed above, it requires approval
2. **Document rationale**: In your PR description, explain:
   - Why the change is necessary during the freeze
   - Impact on downstream services
   - Backward compatibility considerations
3. **Request review**: Tag a tech lead for review
4. **Wait for approval label**: A tech lead must add the `contract-change-approved` label
5. **Re-run CI**: After the label is added, re-run the contracts-guardrails workflow

### For Tech Leads

Before adding the `contract-change-approved` label, verify:

- [ ] Change is necessary and cannot wait until after the freeze
- [ ] Change maintains backward compatibility (or has a migration plan)
- [ ] All consuming services have been notified
- [ ] Documentation is updated (especially `IDENTITY_AND_CONTRACTS.md`)
- [ ] Test coverage exists for the change

### Label Requirements

```
Label: contract-change-approved
Description: Tech lead has approved this contract change during freeze period
Color: #0E8A16 (green)
```

## CI Enforcement

The `contracts-guardrails.yml` workflow enforces this policy:

```yaml
# The workflow checks for the 'contract-change-approved' label
# PRs without this label will fail if they modify contract files
```

### What Triggers the Check

- Changes to `butterfly-common/src/main/avro/**`
- Changes to `butterfly-common/src/main/java/**/identity/**`
- Changes to `butterfly-common/src/main/java/**/RimKafka*`
- Changes to `butterfly-common/src/main/java/**/contract/**`

### Bypassing the Check

The check can only be bypassed by:

1. Adding the `contract-change-approved` label (requires write access)
2. Emergency hotfix branch (must be approved by 2+ maintainers)

## Rationale

### Why Freeze Contracts?

1. **Stability for Performance Tuning**: During performance optimization, stable contracts prevent cascading integration issues
2. **Predictable Downstream Impact**: Services can plan their updates without surprise contract changes
3. **Focused Development**: Teams can concentrate on their phase goals without contract churn
4. **Quality Gate**: Ensures thorough review of any contract changes that do occur

### Related ADRs

| ADR | Title | Relevance |
|-----|-------|-----------|
| [ADR-0002](../adr/0002-shared-contract-library.md) | Shared Contract Library | Defines butterfly-common as single source of truth |
| [ADR-0003](../adr/0003-canonical-identity-model.md) | Canonical Identity Model | Defines RimNodeId format and validation |

## Allowed Changes During Freeze

Even during a freeze, certain changes are permitted without the approval label:

| Change Type | Allowed | Notes |
|-------------|---------|-------|
| Bug fixes in implementation | ✅ | No schema/contract changes |
| Documentation updates | ✅ | `.md` files only |
| Test additions | ✅ | No contract changes |
| Additive Avro fields (with defaults) | ⚠️ | Requires approval |
| New Avro schemas | ⚠️ | Requires approval |
| RimNodeId format changes | ❌ | Never during freeze |
| Removing/renaming Avro fields | ❌ | Breaking change |

## Post-Freeze Process

At the end of a freeze period:

1. Review any deferred contract changes
2. Batch related changes together
3. Communicate changes to all service teams
4. Update version numbers appropriately
5. Run full integration test suite

## Communication

### Before Freeze

- Announce freeze period in `#butterfly-dev` Slack channel
- Update this document with freeze dates
- Notify all service teams via email

### During Freeze

- Track contract change requests in GitHub Issues (label: `contract-change-request`)
- Weekly sync on deferred changes

### After Freeze

- Announce end of freeze
- Publish summary of any approved changes
- Update downstream services as needed

## Related Documentation

| Document | Description |
|----------|-------------|
| [butterfly-common README](../../butterfly-common/README.md) | Library overview |
| [IDENTITY_AND_CONTRACTS.md](../../butterfly-common/IDENTITY_AND_CONTRACTS.md) | Contract specifications |
| [Contributing Guide](../development/contributing.md) | General contribution guidelines |

---

## Document History

| Date | Author | Change |
|------|--------|--------|
| 2025-12-04 | BUTTERFLY Team | Initial freeze policy for Phase 0 |

