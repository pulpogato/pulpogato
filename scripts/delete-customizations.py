#!/usr/bin/env uv run
# /// script
# dependencies = []
# ///

import subprocess
import sys
from pathlib import Path

COMMON_SCHEMA_DIR = Path("pulpogato-common/src/main/resources")


def gradle_check(tasks):
    cmd = ["./gradlew"] + tasks + ["--max-workers=3", "--continue"]
    print(f"Running {' '.join(cmd)}...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode == 0:
        print("Gradle check passed!")
    else:
        print("Gradle check failed:")
        print(result.stdout[-3000:] if len(result.stdout) > 3000 else result.stdout)
    return result.returncode == 0


def start_group(title):
    print(f"::group::{title}")


def end_group():
    print("::endgroup::")


def find_common_schema_files():
    return sorted(COMMON_SCHEMA_DIR.glob("*.schema.json"))


def find_module_schema_files():
    """Return {gradle_task: [schema_files]} for all rest submodules that have schema files."""
    modules = {}
    for resources_dir in sorted(Path(".").glob("pulpogato-rest-*/src/main/resources")):
        files = sorted(resources_dir.glob("*.schema.json"))
        if files:
            module_name = resources_dir.parts[0]
            modules[f":{module_name}:check"] = files
    return modules


def schema_base_name(f: Path) -> str:
    """Strip .del.schema.json or .schema.json to get a pairing key."""
    name = f.name
    if name.endswith(".del.schema.json"):
        return name[: -len(".del.schema.json")]
    return name[: -len(".schema.json")]


def group_into_units(files) -> list[list[Path]]:
    """Group files sharing the same base name into test units (pairs or singles)."""
    groups: dict[str, list[Path]] = {}
    for f in files:
        groups.setdefault(schema_base_name(f), []).append(f)
    return list(groups.values())


def unit_label(unit: list[Path]) -> str:
    if len(unit) == 1:
        return unit[0].name
    return f"{schema_base_name(unit[0])} ({len(unit)} paired files)"


def remove_files(files):
    saved = {}
    for f in files:
        saved[f] = f.read_text()
        f.unlink()
    return saved


def restore_files(saved):
    for f, content in saved.items():
        f.write_text(content)


def try_without(files, gradle_tasks):
    """Remove files, run the given gradle tasks, restore on failure."""
    saved = remove_files(files)
    success = gradle_check(gradle_tasks)
    if not success:
        restore_files(saved)
    return success


def process_schema_group(label, files, gradle_tasks):
    """Try to remove each schema unit in the group, returning the list of removable files."""
    units = group_into_units(files)

    print(f"\n{'='*60}")
    print(f"Group: {label}  ({len(files)} file(s), {len(units)} unit(s))")
    print(f"{'='*60}")
    for unit in units:
        print(f"  - {unit[0].name}")
        for f in unit[1:]:
            print(f"    {f.name}  (paired)")

    start_group(f"Attempt: removing all {len(files)} file(s) at once")
    if try_without(files, gradle_tasks):
        end_group()
        print(f"All {len(files)} file(s) in group can be removed.")
        return list(files)
    end_group()

    print("\nNot all units can be removed. Testing each unit individually...\n")
    removable = []
    for unit in units:
        start_group(f"Testing removal of unit: {unit_label(unit)}")
        if try_without(unit, gradle_tasks):
            removable.extend(unit)
            print(f"→ Can be removed: {unit_label(unit)}")
        else:
            print(f"→ Still needed: {unit_label(unit)}")
        end_group()

    if len(removable) > 1:
        start_group(f"Final check: removing {len(removable)} candidate(s) together")
        if not try_without(removable, gradle_tasks):
            end_group()
            print("ERROR: Individual removals passed but combined removal failed.")
            print("Restoring all files. Manual investigation required.")
            sys.exit(2)
        end_group()

    return removable


def main():
    common_files = find_common_schema_files()
    module_files = find_module_schema_files()

    total = len(common_files) + sum(len(v) for v in module_files.values())
    if total == 0:
        print("No *.schema.json files found.")
        sys.exit(1)

    all_removable = []

    if common_files:
        removable = process_schema_group(
            "pulpogato-common (applies to all REST modules)",
            common_files,
            ["check"],
        )
        all_removable.extend(removable)

    for gradle_task, files in module_files.items():
        module_name = gradle_task.lstrip(":").removesuffix(":check")
        removable = process_schema_group(
            f"{module_name} (module-specific)",
            files,
            [gradle_task],
        )
        all_removable.extend(removable)

    print(f"\n{'='*60}")
    print(f"Summary: {len(all_removable)}/{total} file(s) can be safely removed")
    print(f"{'='*60}")
    if all_removable:
        for f in all_removable:
            print(f"  ✓ {f}")
    else:
        print("  (none)")

    still_needed = [
        f
        for f in list(common_files) + [f for fs in module_files.values() for f in fs]
        if f.exists()
    ]
    if still_needed:
        print(f"\n{len(still_needed)} file(s) still needed:")
        for f in still_needed:
            print(f"  ✗ {f}")


if __name__ == "__main__":
    main()
