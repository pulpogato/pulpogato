#!/bin/bash

set -exo pipefail

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

OLD_SHA=$(grep gh.api.version gradle.properties | cut -d'=' -f2)
export OLD_SHA

./gradlew updateRestSchemaVersion
NEW_SHA=$(grep gh.api.version gradle.properties | cut -d'=' -f2)
export NEW_SHA

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
    COMMIT_MESSAGE="chore(deps): Update rest-api-description to ${NEW_SHA}"
    PR_BODY="See changes: https://github.com/github/rest-api-description/compare/${OLD_SHA}...${NEW_SHA}"
    git commit -m "${COMMIT_MESSAGE}" -m "${PR_BODY}"
    git push origin ${BRANCH_NAME} --force
    PR_NUMBER=$(gh pr list --head ${BRANCH_NAME} --json number -q '.[0].number' || echo "")
    if [ -z "$PR_NUMBER" ]; then
        gh pr create \
            --title "${COMMIT_MESSAGE}" \
            --body "${PR_BODY}" \
            --base main \
            --head ${BRANCH_NAME} \
            --label "dependency"
    else
        gh pr edit ${PR_NUMBER} \
            --title "${COMMIT_MESSAGE}" \
            --body "${PR_BODY}"
    fi
    gh pr merge --auto --merge
    git checkout main
    if [ "$CI" != "" ]; then
        git branch -D ${BRANCH_NAME}
    fi
fi
