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

./gradlew updateRestSchemaVersion
SHORT_SHA=$(grep github.api.version gradle.properties | cut -d'=' -f2)
export SHORT_SHA

BRANCH_NAME=update-schema-version

if git diff --quiet; then
    echo "No changes to rest-api-description"
    exit 2
else
    if [ $(git branch | grep -c "${BRANCH_NAME}") -gt 0 ]; then
        echo "Branch ${BRANCH_NAME} already exists. Deleting..."
        git branch -D ${BRANCH_NAME}
    fi
    git checkout -b ${BRANCH_NAME}
    git add .
    git commit -m "chore(deps): Update rest-api-description to $SHORT_SHA"
    git push origin ${BRANCH_NAME} --force
    gh pr create \
        --title "Update rest-api-description to $SHORT_SHA" \
        --body "" \
        --base main \
        --head ${BRANCH_NAME} \
        --label "dependency"
    gh pr merge --auto --merge
    git checkout main
    if [ "$CI" != "" ]; then
        git branch -D ${BRANCH_NAME}
    fi
fi