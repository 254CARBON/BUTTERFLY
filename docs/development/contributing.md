# Contributing to BUTTERFLY

> Guidelines for contributing to the BUTTERFLY ecosystem

**Last Updated**: 2025-12-03  
**Target Audience**: Contributors, developers

---

## Welcome

Thank you for your interest in contributing to BUTTERFLY! This guide will help you get started.

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

### 1. Fork and Clone

```bash
# Fork on GitHub, then:
git clone https://github.com/YOUR_USERNAME/butterfly.git
cd butterfly
git remote add upstream https://github.com/butterfly-org/butterfly.git
```

### 2. Setup Environment

```bash
# Install dependencies
npm install
npx husky install

# Start infrastructure
cd apps
docker compose -f docker-compose.infra.yml up -d

# Build
./gradlew build
```

### 3. Create Branch

```bash
# Sync with upstream
git fetch upstream
git checkout develop
git merge upstream/develop

# Create feature branch
git checkout -b feature/your-feature-name
```

---

## Development Workflow

### Making Changes

1. **Write tests first** (TDD encouraged)
2. **Implement the feature**
3. **Run tests locally**
   ```bash
   ./gradlew test
   ./gradlew integrationTest
   ```
4. **Check code quality**
   ```bash
   ./gradlew check
   ```
5. **Format code**
   ```bash
   ./gradlew spotlessApply
   ```

### Commit Guidelines

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

**Types:**

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (no logic change) |
| `refactor` | Code refactoring |
| `perf` | Performance improvement |
| `test` | Adding/fixing tests |
| `build` | Build system changes |
| `ci` | CI configuration |
| `chore` | Other changes |

**Scopes:**
- `capsule`, `odyssey`, `perception`, `plato`, `nexus`
- `common`, `e2e`, `docs`, `infra`

**Examples:**

```bash
feat(capsule): implement time-travel optimization

BREAKING CHANGE: CapsuleQuery API signature changed

fix(nexus): resolve rate limiting bypass issue

Fixes #123

docs(api): update authentication examples
```

### Pre-Commit Hooks

Husky runs these checks before each commit:

- Code formatting (Spotless)
- Linting (Checkstyle)
- Tests (affected modules)
- Commit message format

---

## Pull Request Process

### Before Opening PR

- [ ] Tests pass locally
- [ ] Code quality checks pass
- [ ] Documentation updated (if needed)
- [ ] Changelog entry added (if user-facing)
- [ ] Commit messages follow convention

### Creating PR

1. **Push your branch**
   ```bash
   git push origin feature/your-feature-name
   ```

2. **Open PR on GitHub**
   - Base: `develop`
   - Fill out PR template
   - Link related issues

3. **PR Title Format**
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
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Changelog updated
- [ ] Self-reviewed code

## Testing Done
[Describe testing performed]

## Screenshots (if applicable)
[Add screenshots]
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

- Keep PRs focused and small
- Respond to feedback promptly
- Explain complex changes
- Be open to suggestions

### For Reviewers

- Be respectful and constructive
- Explain reasoning for suggestions
- Approve when requirements met
- Focus on:
  - Correctness
  - Performance
  - Security
  - Maintainability
  - Test coverage

---

## Issue Guidelines

### Bug Reports

Use the bug report template:

```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce:
1. ...
2. ...

**Expected behavior**
What you expected to happen.

**Environment:**
- OS: [e.g., Ubuntu 22.04]
- Java version: [e.g., 21]
- BUTTERFLY version: [e.g., 1.2.0]

**Additional context**
Any other context or screenshots.
```

### Feature Requests

Use the feature request template:

```markdown
**Is your feature request related to a problem?**
A description of the problem.

**Describe the solution you'd like**
A clear description of what you want.

**Describe alternatives you've considered**
Alternative solutions or features.

**Additional context**
Any other context or screenshots.
```

---

## Development Guidelines

### Code Quality

- Follow [coding standards](coding-standards.md)
- Write self-documenting code
- Add comments for complex logic
- Keep methods small and focused

### Testing

- Unit tests for all business logic
- Integration tests for service interactions
- E2E tests for critical paths
- See [testing strategy](testing-strategy.md)

### Documentation

- Update README when needed
- Document public APIs
- Add examples for new features
- Keep changelog current

---

## Getting Help

### Questions

- Check existing [documentation](../index.md)
- Search [GitHub Discussions](https://github.com/butterfly-org/butterfly/discussions)
- Ask in `#dev-butterfly` Slack

### Stuck on a PR?

- Ask for help in PR comments
- Reach out on Slack
- Join weekly office hours

---

## Recognition

Contributors are recognized in:

- CONTRIBUTORS.md file
- Release notes
- Monthly contributor spotlight

---

## Code of Conduct

We follow the [Contributor Covenant](https://www.contributor-covenant.org/).

**Be respectful, inclusive, and constructive.**

Report issues to: conduct@butterfly.example.com

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Coding Standards](coding-standards.md) | Code style |
| [Testing Strategy](testing-strategy.md) | Testing approach |
| [CI/CD](ci-cd.md) | Build pipelines |

