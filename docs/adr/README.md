# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records (ADRs) for the BUTTERFLY ecosystem.

## What is an ADR?

An Architecture Decision Record (ADR) captures an important architectural decision made along with its context and consequences. ADRs help teams:

- Understand why decisions were made
- Onboard new team members faster
- Avoid revisiting the same debates
- Track the evolution of the architecture

## ADR Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| [0000](0000-adr-template.md) | ADR Template | - | - |
| [0001](0001-event-driven-architecture.md) | Event-Driven Architecture with Apache Kafka | Accepted | 2024-11-01 |
| [0002](0002-shared-contract-library.md) | Shared Contract Library (butterfly-common) | Accepted | 2024-11-15 |
| [0003](0003-canonical-identity-model.md) | Canonical Identity Model (RimNodeId) | Accepted | 2024-11-20 |
| [0004](0004-dead-letter-queue-strategy.md) | Dead Letter Queue (DLQ) Strategy | Accepted | 2024-11-25 |
| [0005](0005-perception-ci-pipeline-architecture.md) | PERCEPTION CI Pipeline Architecture | Accepted | 2025-12-03 |
| [0006](0006-husky-precommit-hooks.md) | Husky Pre-commit Hooks for Developer Workflow | Accepted | 2025-12-03 |

## Creating a New ADR

1. Copy `0000-adr-template.md` to a new file with the next number
2. Fill in the template sections
3. Submit as a pull request for review
4. Update this README with the new ADR

### Naming Convention

```
NNNN-short-title.md
```

Where `NNNN` is a zero-padded sequential number.

### Status Values

- **Proposed**: Under discussion, not yet accepted
- **Accepted**: Decision has been made and is in effect
- **Deprecated**: Decision is no longer relevant
- **Superseded**: Replaced by a newer ADR (link to replacement)

## Guidelines

- Keep ADRs focused on one decision
- Include context: what problem are we solving?
- Document alternatives considered
- Be honest about trade-offs
- Update status when decisions change
- Link to related ADRs and documentation

## References

- [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) - Michael Nygard
- [ADR GitHub Organization](https://adr.github.io/)

