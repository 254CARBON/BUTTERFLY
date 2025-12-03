# {SERVICE} {VERSION} Release Notes

> **Release Date**: {DATE}  
> **Status**: GA | RC | Beta  
> **Changelog**: [CHANGELOG.md](../CHANGELOG.md)

---

## 1. Highlights

- High-level summary of the most important changes.
- Key features or improvements that users should know about.

## 2. New Features

### Feature 1: {Name}

- Short description and impacted modules.
- How to use it.

### Feature 2: {Name}

- Short description and impacted modules.
- How to use it.

## 3. Changes and Improvements

- Behavioral changes, refactors, and performance improvements.
- Configuration changes.

| Change | Impact |
|--------|--------|
| Description | Low/Medium/High |

## 4. Bug Fixes

- List of bugs fixed in this release.
- Reference issue numbers where applicable.

| Issue | Description |
|-------|-------------|
| #123 | Fixed null pointer exception in X |
| #456 | Corrected Y behavior |

## 5. Breaking Changes

- List breaking API or contract changes.
- Migration steps for each breaking change.

### Breaking Change 1: {Name}

**What changed**: Description.

**Migration steps**:
1. Update your code to...
2. Change configuration to...

## 6. Deprecations

- Features or APIs deprecated in this release.
- Timeline for removal.
- Recommended alternatives.

| Deprecated | Alternative | Removal Version |
|------------|-------------|-----------------|
| `oldMethod()` | `newMethod()` | 2.0.0 |

## 7. Upgrade Notes

- Steps required to upgrade from the previous version.
- Compatibility notes (schema, configuration, clients).
- Required infrastructure changes.

```bash
# Example upgrade commands
mvn versions:set -DnewVersion={VERSION}
./scripts/migrate-schema.sh
```

## 8. Known Issues and Limitations

- Any known issues or caveats for this release.
- Workarounds if available.

| Issue | Description | Workaround |
|-------|-------------|------------|
| #789 | Description | Use X instead |

## 9. Dependencies

- Updated dependencies.
- New dependencies added.
- Security patches.

| Dependency | Old Version | New Version |
|------------|-------------|-------------|
| Spring Boot | 3.1.0 | 3.2.0 |

## 10. References

- [Engineering Roadmap](../ENGINEERING_ROADMAP.md)
- [Architecture Documentation](../docs/architecture/)
- Relevant ADRs

---

## Contributors

Thank you to everyone who contributed to this release:

- @contributor1
- @contributor2

---

*Release managed by {SERVICE} Engineering Team*

