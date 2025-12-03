/**
 * Commitlint configuration for BUTTERFLY ecosystem
 * 
 * Enforces conventional commit format:
 *   <type>(<scope>): <subject>
 * 
 * Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert
 * Scopes: Module names (plato, perception, capsule, odyssey, common, e2e, etc.)
 */
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // Type must be one of the allowed values
    'type-enum': [
      2,
      'always',
      [
        'feat',     // New feature
        'fix',      // Bug fix
        'docs',     // Documentation changes
        'style',    // Code style changes (formatting, no logic change)
        'refactor', // Code refactoring
        'test',     // Adding or updating tests
        'chore',    // Build process or auxiliary tool changes
        'perf',     // Performance improvements
        'ci',       // CI/CD changes
        'build',    // Build system changes
        'revert',   // Revert a previous commit
      ],
    ],
    // Scope should be lowercase
    'scope-case': [2, 'always', 'lower-case'],
    // Subject must not be empty
    'subject-empty': [2, 'never'],
    // Subject must be lowercase
    'subject-case': [2, 'always', 'lower-case'],
    // Subject must not end with period
    'subject-full-stop': [2, 'never', '.'],
    // Type must not be empty
    'type-empty': [2, 'never'],
    // Type must be lowercase
    'type-case': [2, 'always', 'lower-case'],
    // Header max length
    'header-max-length': [2, 'always', 100],
    // Body max line length
    'body-max-line-length': [1, 'always', 120],
  },
  // Help message shown on commit lint failure
  helpUrl: 'https://www.conventionalcommits.org/',
};

