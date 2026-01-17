#!/usr/bin/env uv run
# /// script
# dependencies = [
#   "ruamel.yaml",
# ]
# ///

import io
import sys
import subprocess
import copy
from pathlib import Path
from ruamel.yaml import YAML


def run_gradle_check():
    """Run the gradle check command and return True if it passes, False otherwise."""
    print("Running ./gradlew check --max-workers=3...")
    result = subprocess.run(["./gradlew", "check", "--max-workers=3"],
                          capture_output=True, text=True)
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


def remove_root_level_entry(data, index):
    """Remove a root-level entry from the data."""
    if index < len(data):
        removed_entry = data.pop(index)
        print(f"Removed entry: {removed_entry['example']}")
        return removed_entry
    return None


def remove_version_from_entry(data, entry_index, version_index):
    """Remove a version from a specific entry."""
    if entry_index < len(data):
        entry = data[entry_index]
        if 'versions' in entry and version_index < len(entry['versions']):
            removed_version = entry['versions'].pop(version_index)
            print(f"Removed version '{removed_version}' from entry: {entry['example']}")
            # If no versions left, remove the entire entry
            if not entry['versions']:
                print("No versions left, removing entire entry")
                data.pop(entry_index)
            return removed_version
    return None


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


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ['1', '2']:
        print("Usage: python unignore.py [1|2]")
        print("1: Remove one root-level entry at a time")
        print("2: Remove one version at a time from entries")
        sys.exit(1)

    mode = int(sys.argv[1])
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

    # Work on a copy of the data
    working_data = copy.deepcopy(original_data)
    
    if mode == 1:
        # Remove one root-level entry at a time
        i = 0
        iteration = 0
        while i < len(working_data):
            iteration += 1
            entry_name = working_data[i].get('example', f'entry {i}')
            start_group(f"Iteration {iteration}: Testing removal of '{entry_name}'")

            # Create a temporary copy to test
            test_data = copy.deepcopy(working_data)
            removed_entry = remove_root_level_entry(test_data, i)

            # Save the test data temporarily
            save_yaml(yaml, test_data, yaml_file)

            # Run gradle check
            if run_gradle_check():
                # If check passes, keep the change and continue
                remove_root_level_entry(working_data, i)
                print(f"Successfully removed '{entry_name}'")
                # Don't increment i since we removed an item
            else:
                # If check fails, revert the change
                print("Reverting change...")
                save_yaml(yaml, working_data, yaml_file)
                i += 1  # Move to next item

            end_group()
                
    elif mode == 2:
        # Remove one version at a time
        entry_idx = 0
        iteration = 0
        while entry_idx < len(working_data):
            if 'versions' in working_data[entry_idx] and len(working_data[entry_idx]['versions']) > 0:
                version_idx = 0
                while version_idx < len(working_data[entry_idx]['versions']):
                    iteration += 1
                    entry_name = working_data[entry_idx].get('example', f'entry {entry_idx}')
                    version_name = working_data[entry_idx]['versions'][version_idx]
                    start_group(f"Iteration {iteration}: Testing removal of version '{version_name}' from '{entry_name}'")

                    # Create a temporary copy to test
                    test_data = copy.deepcopy(working_data)
                    removed_version = remove_version_from_entry(test_data, entry_idx, version_idx)

                    # Save the test data temporarily
                    save_yaml(yaml, test_data, yaml_file)

                    # Run gradle check
                    if run_gradle_check():
                        # If check passes, keep the change
                        remove_version_from_entry(working_data, entry_idx, version_idx)
                        print(f"Successfully removed version '{version_name}' from '{entry_name}'")
                        # Don't increment version_idx since we removed an item
                    else:
                        # If check fails, revert the change
                        print("Reverting change...")
                        save_yaml(yaml, working_data, yaml_file)
                        version_idx += 1  # Move to next version

                    end_group()
                entry_idx += 1  # Move to next entry after processing all versions
            else:
                entry_idx += 1  # Move to next entry if no versions to process

    print("Processing complete!")


if __name__ == "__main__":
    main()