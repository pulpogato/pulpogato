#!/usr/bin/env uv run
# /// script
# dependencies = [
#   "ruamel.yaml",
# ]
# ///

import io
import json
import sys
import subprocess
import copy
from pathlib import Path
from ruamel.yaml import YAML

GRADLE_CMD = ["./gradlew", "check", "--max-workers=3", "--continue"]


def run_gradle_check():
    """Run the gradle check command and return True if it passes, False otherwise."""
    print(f"Running {' '.join(GRADLE_CMD)}...")
    result = subprocess.run(GRADLE_CMD, capture_output=True, text=True)
    success = result.returncode == 0
    if not success:
        print("Gradle check failed:")
        print(result.stdout)
        print(result.stderr)
    else:
        print("Gradle check passed!")
    return success


def start_group(title):
    """Start a GitHub Actions log group."""
    print(f"::group::{title}")


def end_group():
    """End a GitHub Actions log group."""
    print("::endgroup::")


def clear_failure_reports():
    """Delete stale generated test failure reports before a new gradle run."""
    for report in Path(".").rglob("generated-test-failures.jsonl"):
        print(f"Deleting stale report: {report}")
        report.unlink()


def read_failure_reports():
    """Read generated test failure reports emitted by the JUnit watcher."""
    failures = []
    for report in sorted(Path(".").rglob("generated-test-failures.jsonl")):
        print(f"Reading generated failure report: {report}")
        with open(report, "r") as f:
            for line in f:
                line = line.strip()
                if line:
                    failures.append(json.loads(line))
    return failures


def flatten_pairs(data):
    """Flatten YAML entries into (example, version) pairs."""
    pairs = set()
    for entry in data:
        example = entry["example"]
        for version in entry.get("versions", []):
            pairs.add((example, version))
    return pairs


def validate_examples_are_unique(data):
    """Ensure every example appears in at most one YAML entry."""
    seen = set()
    duplicates = set()
    for entry in data:
        example = entry["example"]
        if example in seen:
            duplicates.add(example)
        seen.add(example)
    if duplicates:
        print("IgnoredTests.yml contains duplicate examples, which smart mode cannot disambiguate:")
        for example in sorted(duplicates):
            print(f"  - {example}")
        sys.exit(1)


def build_data_from_pairs(original_data, pairs_to_keep):
    """Rebuild the YAML structure while preserving entry order and reasons."""
    rebuilt = []
    for entry in original_data:
        versions = [version for version in entry.get("versions", []) if (entry["example"], version) in pairs_to_keep]
        if versions:
            new_entry = copy.deepcopy(entry)
            new_entry["versions"] = versions
            rebuilt.append(new_entry)
    return rebuilt


def extract_failing_pairs(failures, original_pairs):
    """Extract the ignored (example, version) pairs that are still failing."""
    failing_pairs = set()
    unexpected_failures = []
    for failure in failures:
        example = failure.get("exampleRef")
        version = failure.get("ghVersion")
        if not example or not version:
            continue
        pair = (example, version)
        if pair in original_pairs:
            failing_pairs.add(pair)
        else:
            unexpected_failures.append(pair)

    if unexpected_failures:
        print("Generated failures not present in IgnoredTests.yml:")
        for example, version in sorted(set(unexpected_failures)):
            print(f"  - {version}: {example}")

    return failing_pairs


def run_check_with_data(yaml, data, yaml_file):
    """Write a candidate YAML file, run gradle, and return (success, failures)."""
    save_yaml(yaml, data, yaml_file)
    clear_failure_reports()
    success = run_gradle_check()
    failures = read_failure_reports()
    return success, failures


def save_yaml(yaml, data, file_path):
    """Save data to YAML file, preserving formatting."""
    output = io.StringIO()
    yaml.dump(data, output)
    result = output.getvalue()

    # Fix root sequence indentation (ruamel.yaml adds 2 spaces we don't want)
    lines = result.split('\n')
    fixed_lines = []
    for line in lines:
        if line.startswith('  - '):
            fixed_lines.append(line[2:])
        else:
            fixed_lines.append(line)

    with open(file_path, 'w') as f:
        f.write('\n'.join(fixed_lines))


def smart_unignore(yaml, original_data, yaml_file):
    """Batch-remove ignored pairs, then restore only the pairs that still fail."""
    validate_examples_are_unique(original_data)
    original_pairs = flatten_pairs(original_data)
    retained_pairs = set()
    iteration = 0

    while True:
        iteration += 1
        candidate_data = build_data_from_pairs(original_data, retained_pairs)
        removed_count = len(original_pairs) - len(retained_pairs)
        start_group(
            f"Smart iteration {iteration}: testing {removed_count} unignored pair(s), "
            f"retaining {len(retained_pairs)} known failing pair(s)"
        )
        success, failures = run_check_with_data(yaml, candidate_data, yaml_file)

        if success:
            end_group()
            print(f"Smart mode completed after {iteration} iteration(s).")
            print(f"Unignored {removed_count} pair(s).")
            return

        failing_pairs = extract_failing_pairs(failures, original_pairs)
        if not failing_pairs:
            end_group()
            print("Gradle failed, but no generated test failure reports were found.")
            print("Restoring the original IgnoredTests.yml to avoid dropping valid ignores.")
            save_yaml(yaml, original_data, yaml_file)
            sys.exit(2)

        updated_retained_pairs = retained_pairs | failing_pairs
        newly_retained = updated_retained_pairs - retained_pairs
        print(f"Detected {len(failing_pairs)} failing ignored pair(s).")
        print(f"Restoring {len(newly_retained)} pair(s) for the next iteration.")

        if updated_retained_pairs == retained_pairs:
            end_group()
            print("Smart mode did not make further progress.")
            print("Keeping the latest reconstructed IgnoredTests.yml and exiting with an error.")
            sys.exit(2)

        retained_pairs = updated_retained_pairs
        end_group()


def main():
    if len(sys.argv) != 1:
        print("Usage: python unignore.py")
        print("Batch-remove ignored (example, version) pairs and restore only failing pairs")
        sys.exit(1)
    yaml_file = Path("pulpogato-rest-tests/src/main/resources/IgnoredTests.yml")
    
    if not yaml_file.exists():
        print(f"YAML file not found: {yaml_file}")
        sys.exit(1)

    # Create YAML instance that preserves formatting
    yaml = YAML()
    yaml.preserve_quotes = True
    yaml.indent(mapping=2, sequence=2, offset=2)

    # Read the YAML file
    with open(yaml_file, 'r') as f:
        original_data = yaml.load(f)

    if not original_data:
        print("YAML file is empty or invalid")
        sys.exit(1)

    smart_unignore(yaml, original_data, yaml_file)

    print("Processing complete!")


if __name__ == "__main__":
    main()
