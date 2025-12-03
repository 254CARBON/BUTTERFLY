# Contributing to BUTTERFLY

> Canonical guidelines for contributing to the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: Contributors, developers

---

## Welcome

Thank you for your interest in contributing to BUTTERFLY! This is the **canonical contributing guide** for the entire ecosystem. Individual services (CAPSULE, ODYSSEY, PERCEPTION, PLATO, NEXUS, butterfly-common) may have service-specific contributing files that extend these guidelines with domain-specific details.

---

## Quick Links

| Service | Contributing Guide | Development Setup |
|---------|-------------------|-------------------|
| **CAPSULE** | [CAPSULE/CONTRIBUTING.md](../../CAPSULE/CONTRIBUTING.md) | [CAPSULE/DEVELOPMENT.md](../../CAPSULE/DEVELOPMENT.md) |
| **ODYSSEY** | [ODYSSEY/CONTRIBUTING.md](../../ODYSSEY/CONTRIBUTING.md) | [ODYSSEY/docs/DEV_ENVIRONMENT_SETUP.md](../../ODYSSEY/docs/DEV_ENVIRONMENT_SETUP.md) |
| **PERCEPTION** | [PERCEPTION/CONTRIBUTING.md](../../PERCEPTION/CONTRIBUTING.md) | [PERCEPTION/GETTING_STARTED.md](../../PERCEPTION/GETTING_STARTED.md) |
| **PLATO** | [PLATO/CONTRIBUTING.md](../../PLATO/CONTRIBUTING.md) | [PLATO/docs/getting-started/quickstart.md](../../PLATO/docs/getting-started/quickstart.md) |
| **NEXUS** | [butterfly-nexus/CONTRIBUTING.md](../../butterfly-nexus/CONTRIBUTING.md) | [butterfly-nexus/README.md](../../butterfly-nexus/README.md) |
| **Common** | [butterfly-common/CONTRIBUTING.md](../../butterfly-common/CONTRIBUTING.md) | [butterfly-common/README.md](../../butterfly-common/README.md) |

---

## Ways to Contribute

### Code Contributions

- Bug fixes
- New features
- Performance improvements
- Test coverage

### Non-Code Contributions

- Documentation improvements
- Bug reports
- Feature requests
- Code reviews

---

## Getting Started

### Prerequisites

| Tool | Minimum Version | Purpose |
|------|-----------------|---------|
| Java JDK | 17 | Backend services |
| Maven | 3.9+ | Build tool |
| Node.js | 20+ | Frontend portal, tooling, git hooks |
| Docker | 20+ | Dev infrastructure |
| Docker Compose | v2+ | Multi-container orchestration |
| Git | 2.x | Version control |

### 1. Clone and Setup

```bash
# Clone the repository
git clone https://github.com/254CARBON/BUTTERFLY.git
cd BUTTERFLY/apps

# Run setup script (installs hooks, builds common library)
./scripts/setup.sh

# Or manually:
npm install
mvn -f butterfly-common/pom.xml clean install -DskipTests
```

### 2. Start Infrastructure

```bash
# Start Kafka and dependencies
./scripts/dev-up.sh

# Or start specific service infrastructure
docker compose -f PERCEPTION/docker-compose.yml up -d postgres redis kafka
```

### 3. Create Branch

```bash
# Sync with upstream
git fetch origin
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/your-feature-name
```

---

## Branch Naming Convention

Use these prefixes for branch names:

| Prefix | Purpose | Example |
|--------|---------|---------|
| `feature/` | New features | `feature/add-signal-clustering` |
| `bugfix/` | Bug fixes | `bugfix/fix-null-pointer-exception` |
| `hotfix/` | Critical production fixes | `hotfix/security-patch` |
| `refactor/` | Code refactoring | `refactor/extract-common-utils` |
| `docs/` | Documentation updates | `docs/update-api-guide` |
| `chore/` | Build/tooling changes | `chore/upgrade-spring-boot` |
| `perf/` | Performance improvements | `perf/optimize-query-performance` |

---

## Commit Message Format

We use [Conventional Commits](https://www.conventionalcommits.org/) enforced via commitlint and Husky pre-commit hooks.

### Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (formatting, no logic change) |
| `refactor` | Code refactoring |
| `perf` | Performance improvement |
| `test` | Adding/fixing tests |
| `build` | Build system changes |
| `ci` | CI configuration |
| `chore` | Other changes |
| `revert` | Revert a previous commit |

### Scopes

Use the service or module name:
- `capsule`, `odyssey`, `perception`, `plato`, `nexus`
- `common`, `e2e`, `docs`, `infra`
- Module-specific: `acquisition`, `signals`, `synthplane`, etc.

### Examples

```bash
# Feature
feat(perception): add signal clustering API

# Bug fix
fix(plato): correct temporal rule extraction

# Documentation
docs(capsule): update SDK examples

# Breaking change
feat(odyssey)!: change path engine API

BREAKING CHANGE: PathRequest now requires explicit horizon parameter
```

### Pre-Commit Hooks

Husky runs these checks automatically before each commit:

- **commitlint**: Validates commit message format
- **ESLint**: Lints TypeScript/JavaScript (portal files)

To bypass hooks temporarily (not recommended):
```bash
git commit --no-verify -m "your message"
```

---

## Testing Requirements

### Test Matrix by Service

| Service | Unit Tests | Integration Tests | E2E Tests | Chaos Tests |
|---------|-----------|-------------------|-----------|-------------|
| CAPSULE | `mvn test` | `mvn verify` | `capsule-ui: npm run test:e2e` | `chaos_test.py` |
| ODYSSEY | `mvn test` | `mvn verify -P integration-tests` | E2E harness | - |
| PERCEPTION | `mvn test` | `mvn verify -P integration-tests` | `npm run test:e2e` | `mvn test -Dgroups=chaos` |
| PLATO | `mvn test` | `mvn verify` | - | - |
| NEXUS | `mvn test` | `mvn verify` | - | - |
| Common | `mvn test` | - | - | - |

### Coverage Targets

- **New code**: ≥80% line coverage
- **Critical paths**: ≥90% branch coverage
- Use JaCoCo for coverage reports: `mvn test jacoco:report`

### Running Tests Locally

```bash
# All tests for a service
mvn -f PERCEPTION/pom.xml test

# Specific module
mvn -f PERCEPTION/pom.xml test -pl perception-signals

# Integration tests
mvn -f PERCEPTION/pom.xml verify -P integration-tests

# Chaos tests (PERCEPTION)
mvn -f PERCEPTION/pom.xml test -pl perception-api -Dgroups=chaos

# E2E golden path
./butterfly-e2e/run-golden-path.sh
```

See [Testing Strategy](testing-strategy.md) for comprehensive testing guidelines.

---

## Pull Request Process

### Before Opening PR

Ensure the following checklist passes:

#### General

- [ ] Code follows style guidelines
- [ ] All tests pass locally
- [ ] New tests added for new functionality
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow conventions
- [ ] No merge conflicts with main branch

#### Backend Changes

- [ ] Code coverage maintained or improved
- [ ] API documentation updated (if API changes)
- [ ] `mvn verify` passes
- [ ] Tenant isolation verified (if touching tenant-aware code)

#### Frontend/Portal Changes

- [ ] `npm run lint` passes
- [ ] `npm run test` passes
- [ ] `npm run build` succeeds
- [ ] Component tests added for new components

### Creating PR

1. **Push your branch**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open PR on GitHub**
   - Base: `main`
   - Fill out PR template
   - Link related issues

3. **PR Title Format**: Use conventional commit format
   ```
   feat(scope): Short description
   ```

### PR Template

```markdown
## Description
[Describe what this PR does]

## Related Issues
Fixes #123

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Checklist
- [ ] Tests added/updated and passing
- [ ] Documentation updated
- [ ] Code follows project style guidelines
- [ ] Self-reviewed code
- [ ] No merge conflicts

## Testing Done
[Describe testing performed]

## Screenshots (if applicable)
[Add screenshots for UI changes]
```

### Review Process

1. **Automated checks** run (CI/CD)
2. **Code review** by maintainer
3. **Address feedback** (update PR)
4. **Approval** from maintainer
5. **Merge** via squash-merge

---

## Code Review Guidelines

### For Authors

- Keep PRs focused and small (<400 lines ideal)
- Self-review before requesting review
- Respond to feedback promptly
- Explain complex changes

### For Reviewers

- Review within 24-48 hours
- Be respectful and constructive
- Approve when ready, not perfect
- Focus on: correctness, performance, security, maintainability, test coverage

---

## Issue Guidelines

### Bug Reports

Include:
1. **Description**: Clear description of the issue
2. **Steps to Reproduce**: Detailed steps
3. **Expected Behavior**: What you expected
4. **Actual Behavior**: What actually happened
5. **Environment**: Java version, OS, service version
6. **Logs**: Relevant error messages or stack traces

### Feature Requests

Include:
1. **Use Case**: Problem you're trying to solve
2. **Proposed Solution**: How should it work
3. **Alternatives Considered**: Other approaches
4. **Additional Context**: Any other relevant info

---

## Development Guidelines

### Code Quality

- Follow [coding standards](coding-standards.md)
- Write self-documenting code
- Add comments for complex logic
- Keep methods small and focused

### Documentation

- Update README when needed
- Document public APIs
- Add examples for new features
- Keep changelog current
- Use templates from [docs/templates/](../templates/) for new docs

---

## Getting Help

| Resource | Description |
|----------|-------------|
| [Documentation Portal](../index.md) | Full ecosystem docs |
| [DEVELOPMENT_OVERVIEW.md](../../DEVELOPMENT_OVERVIEW.md) | Quick start guide |
| `#butterfly-dev` Slack | Real-time help |
| GitHub Issues | Bug reports |
| GitHub Discussions | Questions and ideas |

---

## Code of Conduct

We follow the [Contributor Covenant](https://www.contributor-covenant.org/).

**Be respectful, inclusive, and constructive.**

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Coding Standards](coding-standards.md) | Code style |
| [Testing Strategy](testing-strategy.md) | Testing approach |
| [CI/CD](ci-cd.md) | Build pipelines |
| [DEVELOPMENT_OVERVIEW.md](../../DEVELOPMENT_OVERVIEW.md) | Unified developer entry point |

