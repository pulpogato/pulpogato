#!/bin/bash

set -eo pipefail

if [ "$CI" != "" ]; then
    if git diff --quiet; then
        echo "No changes to main. Proceeding..."
    else
        echo "Changes in main, please commit or stash them"
        exit 1
    fi

    git checkout main
    git pull --rebase
else
    git checkout main
fi
bun update

BRANCH_NAME=update-schema-version

if git diff --quiet; then
    echo "No changes to rest-api-description"
    exit 2
else
    git checkout -b ${BRANCH_NAME}
    git add .
    RAD_VERSION=$(bun pm ls | grep rest-api-description | sed 's/\x1B\[[0-9;]\{1,\}[A-Za-z]//g' | cut -d '#' -f 2)
    export RAD_VERSION
    git commit -m "chore(deps): Update rest-api-description to $RAD_VERSION"
    git push origin ${BRANCH_NAME}
    gh pr create \
        --title "Update rest-api-description to $RAD_VERSION" \
        --body "" \
        --base main \
        --head ${BRANCH_NAME}
    gh pr merge --auto --merge
    git checkout main
    if [ "$CI" != "" ]; then
        git branch -D ${BRANCH_NAME}
    fi
fi