# Branch Protection and Release Strategy

> Branch rules, required checks, and release tagging conventions

**Last Updated**: 2025-12-03  
**Target Audience**: All contributors, release managers

---

## Branch Model

BUTTERFLY follows a simplified GitFlow model:

```
main (production)
  │
  ├── develop (integration)
  │     │
  │     ├── feature/xxx
  │     ├── fix/xxx
  │     └── docs/xxx
  │
  └── hotfix/xxx (direct to main)
```

### Branch Purposes

| Branch | Purpose | Deploys To |
|--------|---------|------------|
| `main` | Production-ready code | Production (via tags) |
| `develop` | Integration branch | Staging |
| `feature/*` | New features | Dev (on PR) |
| `fix/*` | Bug fixes | Dev (on PR) |
| `hotfix/*` | Critical production fixes | Production (after merge) |
| `docs/*` | Documentation only | N/A |

---

## Branch Protection Rules

### main Branch

| Setting | Value |
|---------|-------|
| Require PR before merging | Yes |
| Required approvals | 2 |
| Dismiss stale reviews | Yes |
| Require status checks | Yes |
| Require branches up to date | Yes |
| Require signed commits | Recommended |
| Include administrators | Yes |
| Allow force pushes | No |
| Allow deletions | No |

**Required Status Checks:**
- `Build and Unit Tests` (perception-ci)
- `Build and Test` (plato-ci)
- `CodeQL SAST Analysis`
- `OWASP Dependency Check`
- `Secret Scanning`

### develop Branch

| Setting | Value |
|---------|-------|
| Require PR before merging | Yes |
| Required approvals | 1 |
| Dismiss stale reviews | Yes |
| Require status checks | Yes |
| Require branches up to date | No (for faster iteration) |
| Allow force pushes | No |
| Allow deletions | No |

**Required Status Checks:**
- `Build and Unit Tests` (perception-ci)
- `Build and Test` (plato-ci)
- `Checkstyle Analysis`
- `SpotBugs Analysis`

---

## Release Tagging Strategy

### Semantic Versioning

All releases follow [Semantic Versioning 2.0.0](https://semver.org/):

```
v{MAJOR}.{MINOR}.{PATCH}[-{PRERELEASE}]

Examples:
  v1.0.0        - First stable release
  v1.1.0        - New features, backward compatible
  v1.1.1        - Bug fixes only
  v2.0.0        - Breaking changes
  v2.0.0-RC1    - Release candidate
  v2.0.0-alpha  - Alpha release
```

### Version Increment Rules

| Change Type | Version Bump | Example |
|-------------|--------------|---------|
| Breaking API change | MAJOR | v1.0.0 → v2.0.0 |
| New feature (backward compatible) | MINOR | v1.0.0 → v1.1.0 |
| Bug fix | PATCH | v1.0.0 → v1.0.1 |
| Pre-release | PRERELEASE suffix | v2.0.0-RC1 |

### Creating a Release

1. **Ensure develop is stable**
   ```bash
   git checkout develop
   git pull origin develop
   ./butterfly-e2e/run-scenarios.sh  # Verify all tests pass
   ```

2. **Merge develop to main**
   ```bash
   git checkout main
   git pull origin main
   git merge develop
   git push origin main
   ```

3. **Create annotated tag**
   ```bash
   git tag -a v1.2.0 -m "Release v1.2.0 - Feature XYZ"
   git push origin v1.2.0
   ```

4. **Deployment triggers automatically**
   - Tag push triggers `deploy.yml` workflow
   - Builds Docker images with tag version
   - Deploys to production via Helm

### Release Checklist

Before tagging a release:

- [ ] All CI checks pass on `main`
- [ ] CHANGELOG updated with release notes
- [ ] API documentation updated
- [ ] OpenAPI specs regenerated
- [ ] Client SDKs regenerated (if API changed)
- [ ] Performance baseline verified
- [ ] Security scan clean (no critical findings)
- [ ] Stakeholder sign-off obtained

---

## Deployment Environments

### Environment Progression

```
PR/Feature Branch → Dev → Staging → Production
         │              │         │
   Auto-deploy    On merge to   On tag push
   on PR open     develop       to main
```

### Environment Details

| Environment | Cluster | Deployment Method | Approvals |
|-------------|---------|-------------------|-----------|
| Dev | dev-cluster | Auto on PR | None |
| Staging | stage-cluster | Auto on develop merge | None |
| Production | prod-cluster | Tag-triggered + canary | Implicit (tag creation) |

### Canary Deployment

Production deployments use a canary strategy:

1. **Canary Phase** (5 min)
   - Deploy 1 replica with new version
   - Run health checks
   - Monitor error rates

2. **Promote Phase**
   - If canary healthy, promote to full rollout
   - Rolling update: 0 unavailable, 1 surge

3. **Cleanup**
   - Remove canary deployment
   - Full traffic to new version

### Rollback Procedure

If issues detected after deployment:

```bash
# Option 1: Helm rollback
helm rollback capsule -n capsule

# Option 2: Redeploy previous tag
# Trigger deploy.yml workflow_dispatch with previous tag

# Option 3: Emergency kubectl rollback
kubectl -n capsule rollout undo deployment/capsule
```

---

## Commit Message Convention

All commits must follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, no code change |
| `refactor` | Code change that neither fixes nor adds |
| `perf` | Performance improvement |
| `test` | Adding tests |
| `chore` | Maintenance tasks |
| `ci` | CI/CD changes |
| `build` | Build system changes |
| `revert` | Reverts a previous commit |

### Scopes

| Scope | Service/Area |
|-------|--------------|
| `plato` | PLATO service |
| `perception` | PERCEPTION service |
| `capsule` | CAPSULE service |
| `odyssey` | ODYSSEY service |
| `common` | butterfly-common library |
| `portal` | perception-portal frontend |
| `e2e` | E2E tests |
| `docs` | Documentation |

### Examples

```bash
feat(plato): add governance policy evaluation API
fix(perception): correct signal clustering algorithm
docs(onboarding): add common workflows guide
test(plato): add WebSocket integration tests
ci(perception): add Playwright E2E to CI pipeline
chore: update dependencies to latest versions
```

### Pre-commit Validation

Husky validates commit messages via commitlint:

```bash
# If validation fails, fix message and recommit
git commit --amend -m "feat(plato): correct message format"

# Bypass validation (not recommended)
git commit --no-verify -m "message"
```

---

## PR Guidelines

### PR Title Format

Same as commit message:
```
feat(scope): descriptive title
```

### PR Template Checklist

- [ ] Tests added/updated for changes
- [ ] Documentation updated
- [ ] Commit messages follow conventions
- [ ] No merge conflicts
- [ ] CI checks pass
- [ ] Self-review completed

### Review Requirements

| Target Branch | Required Approvals | Required Reviewers |
|---------------|-------------------|--------------------|
| main | 2 | @butterfly-core team |
| develop | 1 | Any team member |

### Merge Strategy

- **Squash and merge** for feature branches (clean history)
- **Merge commit** for release merges (preserve history)
- **Rebase** not recommended (breaks PR links)

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [CI/CD Overview](README.md) | Pipeline documentation |
| [Troubleshooting](troubleshooting.md) | CI failure debugging |
| [Common Workflows](../../onboarding/common-workflows.md) | Development workflows |

