# DX Backlog Issues

This folder contains issue templates for Developer Experience (DX) improvements.

## Creating Issues

To create these issues in GitHub, run:

```bash
# Create all DX epic issues
for file in .github/dx-issues/epic-*.md; do
  title=$(head -1 "$file" | sed 's/^# //')
  gh issue create --title "$title" --body-file "$file" --label "dx,epic,phase-0"
done
```

Or create them manually by copying the content from each markdown file.

## Issue Index

| Epic | File | Description |
|------|------|-------------|
| API Documentation | [epic-api-docs.md](epic-api-docs.md) | Auto-generate OpenAPI docs from CI |
| Scaffolding CLI | [epic-scaffolding-cli.md](epic-scaffolding-cli.md) | Module scaffolding tool |
| IDE Snippets | [epic-ide-snippets.md](epic-ide-snippets.md) | IntelliJ and VS Code snippets |

## Labels

These issues should use the following labels:

- `dx` - Developer Experience improvements
- `epic` - Epic-level tracking issue
- `phase-0` - Phase 0 backlog item
- `contract-change-approved` - Tech lead approved contract change

## Related

- [DX_NOTES.md](../../DX_NOTES.md) - Full DX improvement notes
- [docs/development/contributing.md](../../docs/development/contributing.md) - Contributing guide

