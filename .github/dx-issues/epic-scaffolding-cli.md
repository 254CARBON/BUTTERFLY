# [Epic] Template Scaffolding CLI

## Overview

Create a CLI tool to scaffold new modules using standardized templates, ensuring consistent structure and documentation across the BUTTERFLY ecosystem.

## Tasks

- [ ] Create `scripts/scaffold.sh` CLI tool
- [ ] Implement module README template scaffolding
- [ ] Implement runbook template scaffolding
- [ ] Implement ADR template scaffolding
- [ ] Implement API guide template scaffolding
- [ ] Add interactive prompts for module name/type/options
- [ ] Add validation for module naming conventions

## Acceptance Criteria

- Running `scripts/scaffold.sh module MyNewService` creates a complete module skeleton
- Generated modules follow the same structure as existing services
- All required documentation files are created from templates
- Interactive mode guides users through options
- Dry-run mode shows what would be created without making changes

## Implementation Notes

### CLI Interface

```bash
# Create a new module
./scripts/scaffold.sh module MyNewService

# Create specific documentation
./scripts/scaffold.sh docs runbook --module PERCEPTION
./scripts/scaffold.sh docs adr --title "My Decision"

# Interactive mode
./scripts/scaffold.sh --interactive

# Dry run
./scripts/scaffold.sh module MyNewService --dry-run
```

### Module Structure

A scaffolded module should include:

```
MyNewService/
├── README.md                    # From template
├── CONTRIBUTING.md              # Links to canonical guide
├── pom.xml                      # Maven project
├── docker-compose.yml           # Dev infrastructure
├── .env.example                 # Environment template
├── src/
│   ├── main/java/...
│   └── test/java/...
└── docs/
    ├── README.md
    ├── api/
    └── operations/
```

### Templates Location

Templates should be stored in `docs/templates/`:

- `module-readme.md` - Module README template
- `runbook.md` - Operational runbook template
- `adr.md` - Architecture Decision Record template
- `api-guide.md` - API usage guide template

## Related Documents

- [DX_NOTES.md](../../DX_NOTES.md) - Developer experience roadmap
- [docs/templates/](../../docs/templates/) - Existing templates

## Labels

- `dx` - Developer Experience improvement
- `epic` - Epic-level tracking issue
- `phase-0` - Phase 0 backlog item

---

**Priority**: Medium  
**Estimate**: 1-2 sprints  
**Dependencies**: None

