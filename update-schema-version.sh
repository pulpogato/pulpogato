#!/bin/bash

git checkout main
git pull --rebase
git checkout -b update-schema-version
bun update

if git diff --quiet; then
    echo "No changes to rest-api-description"
else
    git add .
    export RAD_VERSION=$(bun pm ls | grep rest-api-description | sed 's/\x1B\[[0-9;]\{1,\}[A-Za-z]//g' | cut -d '#' -f 2)
    git commit -m "chore(deps): Update rest-api-description to $RAD_VERSION"
    git push origin update-schema-version
    gh pr create \
        --title "Update rest-api-description to $RAD_VERSION" \
        --body "" \
        --base main \
        --head update-schema-version
    gh pr merge --auto --merge
fi