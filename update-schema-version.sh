#!/bin/bash

if git diff --quiet; then
    echo "No changes to main. Proceeding..."
else
    echo "Changes in main, please commit or stash them"
    exit 1
fi

git checkout main
git pull --rebase
bun update

if git diff --quiet; then
    echo "No changes to rest-api-description"
    exit 2
else
    git checkout -b update-schema-version
    git add .
    RAD_VERSION=$(bun pm ls | grep rest-api-description | sed 's/\x1B\[[0-9;]\{1,\}[A-Za-z]//g' | cut -d '#' -f 2)
    export RAD_VERSION
    git commit -m "chore(deps): Update rest-api-description to $RAD_VERSION"
    git push origin update-schema-version
    gh pr create \
        --title "Update rest-api-description to $RAD_VERSION" \
        --body "" \
        --base main \
        --head update-schema-version
    gh pr merge --auto --merge
    git checkout main
    git branch -D update-schema-version
fi