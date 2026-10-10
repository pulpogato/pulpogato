#!/bin/bash

set -euo pipefail

# Mirrors already-published artifacts from GitHub Packages into the local Maven repo
# layout jreleaserDeploy reads from (build/repos/releases), so a Central-only failure
# can be retried without rebuilding or re-publishing to GitHub Packages.
#
# Arguments:
#   $1 - version to republish (e.g. 3.0.5)
#
# Requires GITHUB_TOKEN (read:packages) in the environment.

VERSION="${1:?Usage: republish-to-central.sh <version>}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN must be set}"

REPO="pulpogato/pulpogato"
GROUP_PATH="io/github/pulpogato"
DEST_ROOT="build/repos/releases/${GROUP_PATH}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_PROPS="${SCRIPT_DIR}/../gradle.properties"
GH_VERSIONS_CSV="$(grep '^gh\.versions=' "${GRADLE_PROPS}" | cut -d'=' -f2 | tr -d '\r')"
IFS=',' read -ra GH_VERSIONS <<< "${GH_VERSIONS_CSV}"

ARTIFACTS=(pulpogato-common pulpogato-bom pulpogato-github-files)
for gh_version in "${GH_VERSIONS[@]}"; do
    ARTIFACTS+=("pulpogato-rest-${gh_version}" "pulpogato-graphql-${gh_version}")
done

# Every real file (pom/module/jar/sources/javadoc) is signed and checksummed the
# same way, so the set of possible sibling files is fixed regardless of artifact.
CHECKSUM_SUFFIXES=("" ".asc" ".md5" ".sha1" ".sha256" ".sha512" ".asc.md5" ".asc.sha1" ".asc.sha256" ".asc.sha512")

fetch() {
    local url="$1" out="$2"
    # GitHub Packages redirects downloads to pre-signed S3 URLs (3xx); -f alone
    # doesn't catch that, so without -L curl "succeeds" while saving the HTML
    # redirect body instead of the real artifact.
    if curl -sfL -u "x-access-token:${GITHUB_TOKEN}" -o "$out" "$url"; then
        return 0
    fi
    rm -f "$out"
    return 1
}

for artifact in "${ARTIFACTS[@]}"; do
    dir="${DEST_ROOT}/${artifact}/${VERSION}"
    mkdir -p "${dir}"
    base_url="https://maven.pkg.github.com/${REPO}/${GROUP_PATH}/${artifact}/${VERSION}"

    echo "Fetching ${artifact}:${VERSION}"
    fetched_count=0

    # pom/module have no classifier; jar/sources/javadoc share the "jar" extension.
    files=("${artifact}-${VERSION}.pom" "${artifact}-${VERSION}.module")
    for classifier in "" "-sources" "-javadoc"; do
        files+=("${artifact}-${VERSION}${classifier}.jar")
    done

    for file in "${files[@]}"; do
        for suffix in "${CHECKSUM_SUFFIXES[@]}"; do
            if fetch "${base_url}/${file}${suffix}" "${dir}/${file}${suffix}"; then
                fetched_count=$((fetched_count + 1))
            fi
        done
    done

    if [ "${fetched_count}" -eq 0 ]; then
        echo "No files found for ${artifact}:${VERSION} — is the version correct?" >&2
        exit 1
    fi
    echo "  fetched ${fetched_count} files"
done
