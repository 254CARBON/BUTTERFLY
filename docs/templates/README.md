# Documentation Templates

> Standardized templates for consistent documentation across the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03

---

## Available Templates

| Template | Purpose | Usage |
|----------|---------|-------|
| [module-readme.md](module-readme.md) | README for service modules/packages | New modules, subprojects |
| [runbook.md](runbook.md) | Operational runbooks | Incident response, procedures |
| [adr.md](adr.md) | Architecture Decision Records | Recording design decisions |
| [api-guide.md](api-guide.md) | API usage guides | API documentation beyond OpenAPI |
| [release-notes.md](release-notes.md) | Release announcements | Version releases |

---

## How to Use

1. **Copy the template** to your target location
2. **Replace placeholders** (marked with `{PLACEHOLDER}`)
3. **Fill in sections** - Remove sections that don't apply
4. **Update metadata** - Date, version, authors
5. **Link to related docs** - Add cross-references

---

## Template Guidelines

### Metadata Blocks

All templates include a metadata block at the top:

```markdown
> **Purpose**: Brief description  
> **Audience**: Who should read this  
> **Last Updated**: {DATE}
```

### Section Structure

- Use consistent heading hierarchy (H1 for title, H2 for sections)
- Include a "Related Documentation" section at the end
- Add tables for structured information

### Code Examples

- Include practical code examples where helpful
- Use syntax highlighting with language hints
- Keep examples concise and focused

### Cross-References

Link to related documentation:

```markdown
| Document | Description |
|----------|-------------|
| [Related Doc](path/to/doc.md) | Description |
```

---

## Contributing to Templates

When updating templates:

1. Keep changes minimal and focused
2. Test templates by using them in a real service
3. Update this README if adding new templates
4. Follow the existing style and structure

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Contributing Guide](../development/contributing.md) | Contribution guidelines |
| [Coding Standards](../development/coding-standards.md) | Code style |
| [Documentation Portal](../index.md) | Main docs hub |

