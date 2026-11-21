#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: build-ci-shard.sh <github-version|shared> [release-task]}"
release_task="${2:-}"
gradle=("./gradlew" "--max-workers=${MAX_WORKERS:-2}" --continue)
tasks=()
analysis_modules=()
extra_outputs=()

case "$release_task" in
    snapshot)
        gradle+=(-Pcodegen.format=true)
        ;;
    candidate|final)
        gradle+=(-Prelease.useLastTag=true -Pcodegen.format=true)
        ;;
esac
if [[ -n "$release_task" ]]; then
    # Select the same Nebula version as the publishing job, while omitting the release tasks.
    gradle+=(-PwaenaConfigureRemotePublishing=false "$release_task" -x "$release_task")
fi
if [[ "$release_task" != candidate && "$release_task" != final ]]; then
    gradle+=(-Pcoverage=true)
fi

if [[ "$version" == shared ]]; then
    publications=(pulpogato-common pulpogato-github-files pulpogato-bom)
    tasks+=(:build :pulpogato-rest-tests:build :pulpogato-docs:build)
    analysis_modules=(pulpogato-common pulpogato-github-files pulpogato-rest-tests)
    coverage_modules=("${analysis_modules[@]}")
    if [[ "$release_task" != candidate && "$release_task" != final ]]; then
        tasks+=(:pulpogato-docs:asciidoctor)
        extra_outputs+=(pulpogato-docs/build pulpogato-rest-tests/build/libs)
    fi
else
    publications=("pulpogato-rest-$version" "pulpogato-graphql-$version")
    coverage_modules=("${publications[@]}")
    # Sonar analyzes the generated APIs from FPT, since the variants share class names.
    if [[ "$version" == fpt ]]; then
        analysis_modules=("${publications[@]}")
    fi
    ghes_test_version=$(sed -n 's/^ghes\.test\.version=//p' gradle.properties)
    if [[ "$version" == "${ghes_test_version:?Missing ghes.test.version}" ]]; then
        tasks+=(:pulpogato-rest-ghestest:build)
        analysis_modules+=(pulpogato-rest-ghestest "pulpogato-rest-$version")
        coverage_modules+=(pulpogato-rest-ghestest)
    fi
fi

publication_outputs=()
for module in "${publications[@]}"; do
    tasks+=(":${module}:build" ":${module}:generatePomFileForNebulaPublication" ":${module}:generateMetadataFileForNebulaPublication")
    publication_outputs+=("$module/build/publications/nebula")
    if [[ "$module" != pulpogato-bom ]]; then
        publication_outputs+=("$module/build/libs")
    fi
done

if [[ "$release_task" != candidate && "$release_task" != final ]]; then
    for module in "${coverage_modules[@]}"; do
        tasks+=(":${module}:jacocoTestReport")
        extra_outputs+=("$module/build/reports/jacoco/test/jacocoTestReport.xml")
    done
    for module in "${analysis_modules[@]}"; do
        extra_outputs+=("$module/build/classes" "$module/build/test-results/test")
    done
    case "$version" in
        shared) extra_outputs+=(pulpogato-github-files/build/codegen-src/main/java) ;;
        fpt) extra_outputs+=(pulpogato-rest-fpt/build/codegen-src/{main,test}/java pulpogato-graphql-fpt/build/codegen) ;;
    esac
fi

status=0
"${gradle[@]}" "${tasks[@]}" || status=$?

# Preserve the project-relative paths. Shared dependency outputs belong to the shared job.
# Keep partial analysis inputs after test failures so Sonar can still run.
outputs=()
if (( status == 0 )); then
    outputs+=("${publication_outputs[@]}")
fi
for path in "${extra_outputs[@]}"; do
    if [[ -e "$path" ]]; then
        outputs+=("$path")
    fi
done
mkdir -p build
if (( ${#outputs[@]} )); then
    tar -czf "build/ci-$version.tar.gz" "${outputs[@]}"
else
    tar -czf "build/ci-$version.tar.gz" -T /dev/null
fi
exit "$status"
