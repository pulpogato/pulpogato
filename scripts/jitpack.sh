#!/usr/bin/env bash

# This is invoked by Jitpack based on the jitpack.yml configuration file.
# It is used to build the project and publish it to the local Maven repository.
# Jitpack then publishes the artifacts to its own Maven repository.

set -eux

./gradlew clean \
    -Pgroup="$GROUP" \
    -Pversion="$VERSION" \
    --console=plain \
    --max-workers=4 \
    -xtest assemble publishToMavenLocal