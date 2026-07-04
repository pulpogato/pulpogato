#!/usr/bin/env bash

set -eux

./gradlew clean \
    -Pgroup=$GROUP \
    -Pversion=$VERSION \
    --console=plain \
    --max-workers=4 \
    -xtest assemble publishToMavenLocal