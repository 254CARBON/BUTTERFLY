#!/usr/bin/env python3
"""
BUTTERFLY Resilience Report Generator

Aggregates results from chaos experiments, DLQ replay, and scaling validation
into unified JUnit XML and Markdown reports for CI integration.

Usage:
    python generate-resilience-report.py [OPTIONS]

Options:
    --input-dir DIR      Directory containing result JSON files
    --output FILE        JUnit XML output file
    --summary FILE       JSON summary file to include
    --markdown FILE      Markdown summary output file
    --help               Show this help message

Example:
    python generate-resilience-report.py \
        --input-dir ./resilience-results \
        --output ./resilience-junit.xml \
        --summary ./resilience-summary.json \
        --markdown ./resilience-summary.md
"""

import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any
from xml.etree.ElementTree import Element, SubElement, tostring
from xml.dom import minidom

# SLO Constants
SLO_CHAOS_RECOVERY_SECONDS = 60
SLO_DLQ_SUCCESS_RATE = 93.0
SLO_MESSAGE_LOSS_RATE = 1.0
SLO_SCALING_REACTION_MINUTES = 2


class ResilienceTestCase:
    """Represents a single resilience test case."""
    
    def __init__(
        self,
        name: str,
        classname: str,
        time: float = 0.0,
        status: str = "passed",
        failure_message: str = "",
        failure_type: str = "",
        system_out: str = ""
    ):
        self.name = name
        self.classname = classname
        self.time = time
        self.status = status  # passed, failed, skipped, error
        self.failure_message = failure_message
        self.failure_type = failure_type
        self.system_out = system_out


class ResilienceTestSuite:
    """Represents a resilience test suite (e.g., chaos, dlq, scaling)."""
    
    def __init__(self, name: str, timestamp: str = ""):
        self.name = name
        self.timestamp = timestamp or datetime.now().isoformat()
        self.test_cases: List[ResilienceTestCase] = []
        self.properties: Dict[str, str] = {}
    
    def add_test(self, test_case: ResilienceTestCase):
        self.test_cases.append(test_case)
    
    @property
    def tests(self) -> int:
        return len(self.test_cases)
    
    @property
    def failures(self) -> int:
        return sum(1 for tc in self.test_cases if tc.status == "failed")
    
    @property
    def errors(self) -> int:
        return sum(1 for tc in self.test_cases if tc.status == "error")
    
    @property
    def skipped(self) -> int:
        return sum(1 for tc in self.test_cases if tc.status == "skipped")
    
    @property
    def time(self) -> float:
        return sum(tc.time for tc in self.test_cases)


def parse_chaos_results(input_dir: Path) -> ResilienceTestSuite:
    """Parse chaos experiment results from JSON files."""
    suite = ResilienceTestSuite("Chaos Experiments")
    suite.properties["category"] = "chaos"
    
    chaos_dir = input_dir / "chaos"
    if not chaos_dir.exists():
        return suite
    
    for json_file in chaos_dir.glob("*.json"):
        try:
            with open(json_file) as f:
                data = json.load(f)
            
            experiment = data.get("experiment", json_file.stem)
            duration = float(data.get("duration_seconds", 0))
            result = data.get("result", "UNKNOWN")
            
            status = "passed" if result == "PASSED" else "failed" if result == "FAILED" else "skipped"
            failure_msg = ""
            
            if status == "failed":
                failure_msg = f"Chaos experiment {experiment} failed. Recovery time exceeded SLO of {SLO_CHAOS_RECOVERY_SECONDS}s"
            
            test_case = ResilienceTestCase(
                name=f"chaos_{experiment}",
                classname="com.z254.butterfly.resilience.chaos",
                time=duration,
                status=status,
                failure_message=failure_msg,
                failure_type="AssertionError" if status == "failed" else "",
                system_out=json.dumps(data, indent=2)
            )
            suite.add_test(test_case)
            
        except (json.JSONDecodeError, IOError) as e:
            print(f"Warning: Could not parse {json_file}: {e}", file=sys.stderr)
    
    return suite


def parse_dlq_results(input_dir: Path) -> ResilienceTestSuite:
    """Parse DLQ replay results from JSON files."""
    suite = ResilienceTestSuite("DLQ Replay")
    suite.properties["category"] = "dlq"
    
    dlq_dir = input_dir / "dlq"
    if not dlq_dir.exists():
        return suite
    
    for json_file in dlq_dir.glob("*.json"):
        try:
            with open(json_file) as f:
                data = json.load(f)
            
            matched = data.get("matched", 0)
            replayed = data.get("replayed", 0)
            failed = data.get("failed", 0)
            success_rate = float(data.get("success_rate", 100))
            slo_met = data.get("slo_met", True)
            
            status = "passed" if slo_met else "failed"
            failure_msg = ""
            
            if not slo_met:
                failure_msg = f"DLQ replay success rate {success_rate}% below SLO of {SLO_DLQ_SUCCESS_RATE}%"
            
            # Calculate message loss
            if matched > 0:
                message_loss = (failed / matched) * 100
                if message_loss > SLO_MESSAGE_LOSS_RATE:
                    status = "failed"
                    failure_msg += f"\nMessage loss rate {message_loss:.2f}% exceeds SLO of {SLO_MESSAGE_LOSS_RATE}%"
            
            test_case = ResilienceTestCase(
                name=f"dlq_replay_{json_file.stem}",
                classname="com.z254.butterfly.resilience.dlq",
                time=0.0,
                status=status,
                failure_message=failure_msg,
                failure_type="AssertionError" if status == "failed" else "",
                system_out=f"Matched: {matched}, Replayed: {replayed}, Failed: {failed}, Success Rate: {success_rate}%"
            )
            suite.add_test(test_case)
            
        except (json.JSONDecodeError, IOError) as e:
            print(f"Warning: Could not parse {json_file}: {e}", file=sys.stderr)
    
    return suite


def parse_scaling_results(input_dir: Path) -> ResilienceTestSuite:
    """Parse scaling validation results from JSON files."""
    suite = ResilienceTestSuite("Scaling Validation")
    suite.properties["category"] = "scaling"
    
    scaling_dir = input_dir / "scaling"
    if not scaling_dir.exists():
        return suite
    
    for json_file in scaling_dir.glob("*.json"):
        try:
            with open(json_file) as f:
                data = json.load(f)
            
            all_passed = data.get("all_checks_passed", False)
            check_results = data.get("check_results", {})
            
            # Create a test case for each scaling check
            for check_name, result in check_results.items():
                status = "passed"
                failure_msg = ""
                
                if result in ["SCALE_UP_NEEDED", "DEGRADED"]:
                    status = "failed"
                    failure_msg = f"Scaling check {check_name} indicates issues: {result}"
                elif result == "UNKNOWN":
                    status = "skipped"
                
                test_case = ResilienceTestCase(
                    name=f"scaling_{check_name}",
                    classname="com.z254.butterfly.resilience.scaling",
                    time=0.0,
                    status=status,
                    failure_message=failure_msg,
                    failure_type="AssertionError" if status == "failed" else ""
                )
                suite.add_test(test_case)
            
        except (json.JSONDecodeError, IOError) as e:
            print(f"Warning: Could not parse {json_file}: {e}", file=sys.stderr)
    
    return suite


def generate_junit_xml(suites: List[ResilienceTestSuite]) -> str:
    """Generate JUnit XML from test suites."""
    testsuites = Element("testsuites")
    testsuites.set("name", "BUTTERFLY Resilience Tests")
    testsuites.set("tests", str(sum(s.tests for s in suites)))
    testsuites.set("failures", str(sum(s.failures for s in suites)))
    testsuites.set("errors", str(sum(s.errors for s in suites)))
    testsuites.set("time", str(sum(s.time for s in suites)))
    
    for suite in suites:
        testsuite = SubElement(testsuites, "testsuite")
        testsuite.set("name", suite.name)
        testsuite.set("tests", str(suite.tests))
        testsuite.set("failures", str(suite.failures))
        testsuite.set("errors", str(suite.errors))
        testsuite.set("skipped", str(suite.skipped))
        testsuite.set("time", str(suite.time))
        testsuite.set("timestamp", suite.timestamp)
        
        # Add properties
        if suite.properties:
            properties = SubElement(testsuite, "properties")
            for key, value in suite.properties.items():
                prop = SubElement(properties, "property")
                prop.set("name", key)
                prop.set("value", value)
        
        # Add test cases
        for tc in suite.test_cases:
            testcase = SubElement(testsuite, "testcase")
            testcase.set("name", tc.name)
            testcase.set("classname", tc.classname)
            testcase.set("time", str(tc.time))
            
            if tc.status == "failed":
                failure = SubElement(testcase, "failure")
                failure.set("message", tc.failure_message)
                failure.set("type", tc.failure_type)
                failure.text = tc.failure_message
            elif tc.status == "error":
                error = SubElement(testcase, "error")
                error.set("message", tc.failure_message)
                error.set("type", tc.failure_type)
            elif tc.status == "skipped":
                SubElement(testcase, "skipped")
            
            if tc.system_out:
                system_out = SubElement(testcase, "system-out")
                system_out.text = tc.system_out
    
    # Pretty print
    rough_string = tostring(testsuites, encoding='unicode')
    reparsed = minidom.parseString(rough_string)
    return reparsed.toprettyxml(indent="  ")


def generate_markdown_summary(
    suites: List[ResilienceTestSuite],
    summary_data: Optional[Dict[str, Any]] = None
) -> str:
    """Generate Markdown summary report."""
    lines = []
    lines.append("# BUTTERFLY Resilience Test Report")
    lines.append("")
    lines.append(f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}")
    lines.append("")
    
    # Overall summary
    total_tests = sum(s.tests for s in suites)
    total_failures = sum(s.failures for s in suites)
    total_errors = sum(s.errors for s in suites)
    total_skipped = sum(s.skipped for s in suites)
    total_passed = total_tests - total_failures - total_errors - total_skipped
    
    lines.append("## Summary")
    lines.append("")
    lines.append("| Metric | Value |")
    lines.append("|--------|-------|")
    lines.append(f"| Total Tests | {total_tests} |")
    lines.append(f"| Passed | {total_passed} |")
    lines.append(f"| Failed | {total_failures} |")
    lines.append(f"| Errors | {total_errors} |")
    lines.append(f"| Skipped | {total_skipped} |")
    
    if total_tests > 0:
        pass_rate = (total_passed / total_tests) * 100
        lines.append(f"| Pass Rate | {pass_rate:.1f}% |")
    
    lines.append("")
    
    # SLO Targets
    lines.append("## SLO Targets")
    lines.append("")
    lines.append("| Metric | Target | Status |")
    lines.append("|--------|--------|--------|")
    lines.append(f"| Chaos Recovery Time | < {SLO_CHAOS_RECOVERY_SECONDS}s | - |")
    lines.append(f"| DLQ Replay Success Rate | >= {SLO_DLQ_SUCCESS_RATE}% | - |")
    lines.append(f"| Message Loss Rate | < {SLO_MESSAGE_LOSS_RATE}% | - |")
    lines.append(f"| Scaling Reaction Time | < {SLO_SCALING_REACTION_MINUTES} min | - |")
    lines.append("")
    
    # Per-suite details
    for suite in suites:
        if suite.tests == 0:
            continue
        
        lines.append(f"## {suite.name}")
        lines.append("")
        lines.append(f"**Tests:** {suite.tests} | **Passed:** {suite.tests - suite.failures - suite.errors - suite.skipped} | **Failed:** {suite.failures} | **Skipped:** {suite.skipped}")
        lines.append("")
        
        if suite.test_cases:
            lines.append("| Test | Status | Time |")
            lines.append("|------|--------|------|")
            
            for tc in suite.test_cases:
                status_icon = {
                    "passed": "✅",
                    "failed": "❌",
                    "error": "⚠️",
                    "skipped": "⏭️"
                }.get(tc.status, "❓")
                
                lines.append(f"| {tc.name} | {status_icon} {tc.status} | {tc.time:.2f}s |")
            
            lines.append("")
        
        # Show failures
        failures = [tc for tc in suite.test_cases if tc.status in ("failed", "error")]
        if failures:
            lines.append("### Failures")
            lines.append("")
            for tc in failures:
                lines.append(f"**{tc.name}:**")
                lines.append(f"```")
                lines.append(tc.failure_message)
                lines.append(f"```")
                lines.append("")
    
    # Include summary data if provided
    if summary_data:
        lines.append("## Additional Details")
        lines.append("")
        lines.append("```json")
        lines.append(json.dumps(summary_data, indent=2))
        lines.append("```")
    
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Generate resilience test reports from JSON results"
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        required=True,
        help="Directory containing result JSON files"
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="JUnit XML output file"
    )
    parser.add_argument(
        "--summary",
        type=Path,
        help="JSON summary file to include"
    )
    parser.add_argument(
        "--markdown",
        type=Path,
        help="Markdown summary output file"
    )
    
    args = parser.parse_args()
    
    if not args.input_dir.exists():
        print(f"Error: Input directory does not exist: {args.input_dir}", file=sys.stderr)
        sys.exit(1)
    
    # Parse all result files
    suites = []
    
    chaos_suite = parse_chaos_results(args.input_dir)
    if chaos_suite.tests > 0:
        suites.append(chaos_suite)
    
    dlq_suite = parse_dlq_results(args.input_dir)
    if dlq_suite.tests > 0:
        suites.append(dlq_suite)
    
    scaling_suite = parse_scaling_results(args.input_dir)
    if scaling_suite.tests > 0:
        suites.append(scaling_suite)
    
    # Load summary data if provided
    summary_data = None
    if args.summary and args.summary.exists():
        try:
            with open(args.summary) as f:
                summary_data = json.load(f)
        except (json.JSONDecodeError, IOError) as e:
            print(f"Warning: Could not load summary file: {e}", file=sys.stderr)
    
    # Generate JUnit XML
    if args.output:
        junit_xml = generate_junit_xml(suites)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with open(args.output, "w") as f:
            f.write(junit_xml)
        print(f"JUnit XML written to: {args.output}")
    
    # Generate Markdown
    if args.markdown:
        markdown = generate_markdown_summary(suites, summary_data)
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        with open(args.markdown, "w") as f:
            f.write(markdown)
        print(f"Markdown summary written to: {args.markdown}")
    
    # Print summary to stdout
    total_tests = sum(s.tests for s in suites)
    total_failures = sum(s.failures for s in suites) + sum(s.errors for s in suites)
    
    print(f"\nResilience Report Summary:")
    print(f"  Total Tests: {total_tests}")
    print(f"  Failures: {total_failures}")
    
    if total_failures > 0:
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    main()

