#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BUTTERFLY Documentation Semantic Linter

Validates bidirectional cross-references between docs/index.md and service READMEs.

Rules:
1. Every service in docs/index.md must have a README that links back to docs/index.md
2. Service READMEs must include ecosystem badge pattern
3. Services in docs/services/README.md must have corresponding directories
4. Service READMEs should link to their docs/services/{service}.md summary

Exit codes:
  0 - All checks passed
  1 - Validation errors found
"""

import os
import re
import sys
from pathlib import Path
from dataclasses import dataclass, field
from typing import List, Optional, Set, Tuple


@dataclass
class ValidationError:
    """Represents a documentation validation error."""
    file: str
    message: str
    severity: str = "error"  # error or warning
    suggestion: Optional[str] = None

    def __str__(self) -> str:
        prefix = "ERROR" if self.severity == "error" else "WARNING"
        result = f"[{prefix}] {self.file}: {self.message}"
        if self.suggestion:
            result += f"\n  → Suggestion: {self.suggestion}"
        return result


@dataclass
class LintResult:
    """Collects validation results."""
    errors: List[ValidationError] = field(default_factory=list)
    warnings: List[ValidationError] = field(default_factory=list)
    passed: int = 0

    def add_error(self, file: str, message: str, suggestion: Optional[str] = None):
        self.errors.append(ValidationError(file, message, "error", suggestion))

    def add_warning(self, file: str, message: str, suggestion: Optional[str] = None):
        self.warnings.append(ValidationError(file, message, "warning", suggestion))

    def add_pass(self):
        self.passed += 1

    @property
    def has_errors(self) -> bool:
        return len(self.errors) > 0


# Service definitions - maps service name to directory path (relative to workspace root)
SERVICES = {
    "PERCEPTION": "PERCEPTION",
    "CAPSULE": "CAPSULE",
    "ODYSSEY": "ODYSSEY",
    "PLATO": "PLATO",
    "NEXUS": "butterfly-nexus",
    "butterfly-common": "butterfly-common",
}

# Expected ecosystem badge pattern in service READMEs
ECOSYSTEM_BADGE_PATTERNS = [
    r">\s*📚?\s*\*\*Part of the BUTTERFLY Ecosystem\*\*",
    r"Part of the BUTTERFLY Ecosystem.*See \[Ecosystem Documentation\]",
    r"\[Ecosystem Documentation\]\(\.\./docs/index\.md\)",
]

# Expected link back to docs/index.md
DOCS_INDEX_LINK_PATTERN = r"\]\(\.\./docs/index\.md\)"

# Expected link to service summary
SERVICE_SUMMARY_LINK_PATTERN = r"\]\(\.\./docs/services/{service}\.md\)"


def find_workspace_root() -> Path:
    """Find the workspace root (directory containing docs/ and services)."""
    current = Path(__file__).resolve().parent.parent
    
    # Verify we found the right directory
    if (current / "docs" / "index.md").exists():
        return current
    
    # Try one level up
    parent = current.parent
    if (parent / "docs" / "index.md").exists():
        return parent
    
    raise RuntimeError(f"Could not find workspace root from {__file__}")


def read_file_content(filepath: Path) -> Optional[str]:
    """Read file content, returning None if file doesn't exist."""
    try:
        return filepath.read_text(encoding="utf-8")
    except FileNotFoundError:
        return None
    except Exception as e:
        print(f"Warning: Could not read {filepath}: {e}", file=sys.stderr)
        return None


def check_ecosystem_badge(content: str) -> bool:
    """Check if content contains the ecosystem badge pattern."""
    for pattern in ECOSYSTEM_BADGE_PATTERNS:
        if re.search(pattern, content, re.IGNORECASE):
            return True
    return False


def check_docs_index_link(content: str) -> bool:
    """Check if content links back to docs/index.md."""
    return bool(re.search(DOCS_INDEX_LINK_PATTERN, content))


def check_service_summary_link(content: str, service_name: str) -> bool:
    """Check if content links to the service summary page."""
    # Handle case variations and special naming
    service_lower = service_name.lower()
    if service_lower == "nexus":
        service_lower = "nexus"
    
    pattern = SERVICE_SUMMARY_LINK_PATTERN.format(service=service_lower)
    return bool(re.search(pattern, content))


def extract_services_from_index(content: str) -> Set[str]:
    """Extract service names mentioned in docs/index.md service table."""
    services = set()
    
    # Look for service references in the "By Service" table
    # Pattern matches: | **SERVICE** | ... | [Source](../SERVICE/README.md) |
    table_pattern = r"\|\s*\*\*([A-Za-z-]+)\*\*\s*\|"
    matches = re.findall(table_pattern, content)
    
    for match in matches:
        services.add(match.upper() if match != "butterfly-common" else "butterfly-common")
    
    return services


def validate_service_readme(
    service_name: str,
    service_dir: str,
    workspace_root: Path,
    result: LintResult
) -> None:
    """Validate a service README meets all requirements."""
    readme_path = workspace_root / service_dir / "README.md"
    readme_relative = f"{service_dir}/README.md"
    
    content = read_file_content(readme_path)
    
    if content is None:
        result.add_error(
            readme_relative,
            f"README.md not found for service {service_name}",
            f"Create {readme_relative} with ecosystem links"
        )
        return
    
    # Check 1: Ecosystem badge
    if not check_ecosystem_badge(content):
        result.add_error(
            readme_relative,
            "Missing ecosystem badge/header",
            'Add: > 📚 **Part of the BUTTERFLY Ecosystem** — See [Ecosystem Documentation](../docs/index.md)'
        )
    else:
        result.add_pass()
    
    # Check 2: Link back to docs/index.md
    if not check_docs_index_link(content):
        result.add_error(
            readme_relative,
            "Missing link to ../docs/index.md",
            "Add: [Ecosystem Documentation](../docs/index.md)"
        )
    else:
        result.add_pass()
    
    # Check 3: Link to service summary (warning, not error)
    service_lower = service_name.lower()
    if service_name == "NEXUS":
        service_lower = "nexus"
    
    if not check_service_summary_link(content, service_lower):
        result.add_warning(
            readme_relative,
            f"Missing link to service summary docs/services/{service_lower}.md",
            f"Add: [{service_name} Summary](../docs/services/{service_lower}.md)"
        )
    else:
        result.add_pass()


def validate_service_summary_exists(
    service_name: str,
    workspace_root: Path,
    result: LintResult
) -> None:
    """Validate that a service summary page exists in docs/services/."""
    service_lower = service_name.lower()
    if service_name == "NEXUS":
        service_lower = "nexus"
    
    summary_path = workspace_root / "docs" / "services" / f"{service_lower}.md"
    
    if not summary_path.exists():
        result.add_error(
            f"docs/services/{service_lower}.md",
            f"Service summary page missing for {service_name}",
            f"Create docs/services/{service_lower}.md with service overview"
        )
    else:
        result.add_pass()


def validate_docs_index_services(workspace_root: Path, result: LintResult) -> None:
    """Validate that docs/index.md references all services correctly."""
    index_path = workspace_root / "docs" / "index.md"
    content = read_file_content(index_path)
    
    if content is None:
        result.add_error("docs/index.md", "Main documentation index not found")
        return
    
    # Check that all known services are mentioned
    for service_name in SERVICES:
        if service_name not in content and service_name.lower() not in content.lower():
            result.add_warning(
                "docs/index.md",
                f"Service {service_name} not mentioned in documentation index",
                f"Add {service_name} to the services table in docs/index.md"
            )
        else:
            result.add_pass()


def validate_services_readme_references(workspace_root: Path, result: LintResult) -> None:
    """Validate docs/services/README.md references match actual service directories."""
    services_readme = workspace_root / "docs" / "services" / "README.md"
    content = read_file_content(services_readme)
    
    if content is None:
        result.add_error("docs/services/README.md", "Services README not found")
        return
    
    # Check that all known services are mentioned
    for service_name, service_dir in SERVICES.items():
        service_dir_path = workspace_root / service_dir
        
        if not service_dir_path.exists():
            result.add_error(
                "docs/services/README.md",
                f"Service directory {service_dir}/ not found for service {service_name}"
            )
        else:
            result.add_pass()


def validate_service_contributing_link(
    service_name: str,
    service_dir: str,
    workspace_root: Path,
    result: LintResult
) -> None:
    """Check if service CONTRIBUTING.md links to canonical guide."""
    contrib_path = workspace_root / service_dir / "CONTRIBUTING.md"
    contrib_relative = f"{service_dir}/CONTRIBUTING.md"
    
    content = read_file_content(contrib_path)
    
    if content is None:
        # CONTRIBUTING.md is optional
        return
    
    # Check for link to canonical contributing guide
    canonical_link_pattern = r"docs/development/contributing\.md"
    if not re.search(canonical_link_pattern, content):
        result.add_warning(
            contrib_relative,
            "CONTRIBUTING.md should reference canonical guide",
            "Add: For full guidelines, see [Contributing Guide](../docs/development/contributing.md)"
        )
    else:
        result.add_pass()


def run_validation() -> LintResult:
    """Run all documentation validation checks."""
    result = LintResult()
    
    try:
        workspace_root = find_workspace_root()
    except RuntimeError as e:
        result.add_error(".", str(e))
        return result
    
    print(f"Workspace root: {workspace_root}")
    print()
    
    # Validate docs/index.md has all services
    print("Checking docs/index.md service references...")
    validate_docs_index_services(workspace_root, result)
    
    # Validate docs/services/README.md references
    print("Checking docs/services/README.md references...")
    validate_services_readme_references(workspace_root, result)
    
    # Validate each service README
    print("Checking service READMEs...")
    for service_name, service_dir in SERVICES.items():
        print(f"  - {service_name} ({service_dir}/README.md)")
        validate_service_readme(service_name, service_dir, workspace_root, result)
        validate_service_summary_exists(service_name, workspace_root, result)
        validate_service_contributing_link(service_name, service_dir, workspace_root, result)
    
    return result


def main() -> int:
    """Main entry point."""
    print("=" * 60)
    print("BUTTERFLY Documentation Semantic Linter")
    print("=" * 60)
    print()
    
    result = run_validation()
    
    print()
    print("=" * 60)
    print("RESULTS")
    print("=" * 60)
    print()
    
    # Print errors
    if result.errors:
        print(f"❌ ERRORS ({len(result.errors)}):")
        print("-" * 40)
        for error in result.errors:
            print(str(error))
            print()
    
    # Print warnings
    if result.warnings:
        print(f"⚠️  WARNINGS ({len(result.warnings)}):")
        print("-" * 40)
        for warning in result.warnings:
            print(str(warning))
            print()
    
    # Summary
    print("-" * 40)
    print(f"Passed:   {result.passed}")
    print(f"Warnings: {len(result.warnings)}")
    print(f"Errors:   {len(result.errors)}")
    print()
    
    if result.has_errors:
        print("❌ Documentation validation FAILED")
        print()
        print("Fix the errors above and run this script again.")
        print("For questions, see docs/development/contributing.md")
        return 1
    
    print("✅ Documentation validation PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())

