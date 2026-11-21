#!/usr/bin/env bash

set -euo pipefail

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "Usage: $0 <version>"
    exit 1
fi

GROUP_ID="com.github.pulpogato.pulpogato"
ARTIFACT_ID="pulpogato-common"
GROUP_PATH="${GROUP_ID//.//}"

echo "GROUP_ID: $GROUP_ID"
echo "GROUP_PATH: $GROUP_PATH"
echo "ARTIFACT_ID: $ARTIFACT_ID"
echo "VERSION: $VERSION"

URL="https://jitpack.io/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.pom"
echo "URL: $URL"

echo ""
echo + curl -sv "${URL}" -o /tmp/jitpack.txt
curl --no-progress-meter \
    --verbose \
    --output /tmp/jitpack.txt \
    "${URL}"

echo ""
echo "=== Begin Output ==="
cat /tmp/jitpack.txt
echo ""
echo "==== End Output ===="