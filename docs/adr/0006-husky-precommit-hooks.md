# ADR-0006: Husky Pre-commit Hooks for Developer Workflow

## Status

Accepted

## Date

2025-12-03

## Context

The BUTTERFLY monorepo contains multiple services written in Java and TypeScript. We needed a way to:
- Enforce consistent commit message format across all contributors
- Run automated checks before code is committed
- Prevent low-quality commits from entering the repository

The ecosystem uses conventional commit format as documented in all CONTRIBUTING.md files, but this was not enforced automatically.

## Decision

We implement Husky v9 with lint-staged and commitlint at the repository root level.

### Components

1. **Husky v9** - Modern git hooks manager
   - Lightweight, zero-dependency setup
   - Native shell scripts in `.husky/` directory
   - Activated via `npm prepare` script

2. **lint-staged** - Run linters on staged files only
   - TypeScript/JavaScript files: ESLint
   - Scoped to portal directories to avoid false positives

3. **commitlint** - Validate commit message format
   - Uses `@commitlint/config-conventional` preset
   - Enforces: `<type>(<scope>): <subject>` format
   - Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert

### Hook Configuration

**`.husky/pre-commit`:**
- Runs lint-staged on modified files
- Fails fast on linting errors

**`.husky/commit-msg`:**
- Validates commit message against conventional format
- Provides helpful error messages for malformed commits

### Installation

Hooks are installed automatically when running:
```bash
npm install  # triggers 'prepare' script
```

Or explicitly via the setup script:
```bash
./scripts/setup.sh
```

## Consequences

### Positive

- Commit messages are consistently formatted across all contributors
- Malformed commits are rejected immediately with clear feedback
- Code style issues are caught before commit, not in CI
- Reduces CI failures from trivial issues
- Improves git log readability and changelog generation
- Works with any IDE or git client

### Negative

- Requires Node.js installed for all contributors (including Java-only developers)
- Adds ~3-5 seconds to each commit operation
- Developers can bypass with `--no-verify` (discouraged)
- Initial setup required after clone

### Neutral

- Java checkstyle runs in CI, not in pre-commit (too slow for local)
- Prettier formatting is check-only, not auto-fix (preserves author intent)
- Hooks are shell scripts, portable across platforms

## Alternatives Considered

### Alternative 1: pre-commit Framework (Python)

The `pre-commit` Python framework is widely used and has extensive hook ecosystem.

**Not chosen because:**
- Requires Python installation
- We already have Node.js for frontend development
- Husky is more common in JavaScript/TypeScript ecosystems
- simpler configuration for our use case

### Alternative 2: Manual Git Hooks in Repository

Store git hooks in `scripts/hooks/` and require manual copying.

**Not chosen because:**
- Error-prone manual installation
- Hooks not automatically updated
- Easy to forget during onboarding

### Alternative 3: Server-side Hooks Only

Rely on GitHub branch protection and server-side checks.

**Not chosen because:**
- Slower feedback loop (must push to discover issues)
- More CI resource consumption
- Worse developer experience

### Alternative 4: lefthook

Lefthook is a fast, polyglot git hooks manager written in Go.

**Not chosen because:**
- Less ecosystem familiarity in team
- Husky has better documentation and community
- Node.js already required for frontend

## References

- [Husky Documentation](https://typicode.github.io/husky/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [commitlint](https://commitlint.js.org/)
- [lint-staged](https://github.com/okonet/lint-staged)
- [CONTRIBUTING.md files](../../PERCEPTION/CONTRIBUTING.md) - Commit message guidelines

