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

# Updates a property via a Gradle task and creates/updates a PR if changed.
#
# Arguments:
#   $1 - Gradle task name (e.g. updateRestSchemaVersion)
#   $2 - Property name in gradle.properties (e.g. gh.api.commit)
#   $3 - Branch name for the PR (e.g. update-schema-version)
#   $4 - GitHub repo for compare URL (e.g. github/rest-api-description)
update_and_pr() {
    local gradle_task="$1"
    local property_name="$2"
    local branch_name="$3"
    local repo_for_compare="$4"

    local old_sha
    old_sha=$(grep "${property_name}=" gradle.properties | cut -d'=' -f2)

    ./gradlew "${gradle_task}"

    local new_sha
    new_sha=$(grep "${property_name}=" gradle.properties | cut -d'=' -f2)

    if [ "${old_sha}" = "${new_sha}" ]; then
        echo "No changes to ${property_name}"
        return
    fi

    if [ "$(git branch | grep -c "${branch_name}")" -gt 0 ]; then
        echo "Branch ${branch_name} already exists. Deleting..."
        git branch -D "${branch_name}"
    fi
    git checkout -b "${branch_name}"
    git add .
    local commit_message="chore(deps): Update ${property_name} to ${new_sha}"
    local pr_body="See changes: https://github.com/${repo_for_compare}/compare/${old_sha}...${new_sha}"
    git commit -m "${commit_message}" -m "${pr_body}"
    git push origin "${branch_name}" --force
    local pr_number
    pr_number=$(gh pr list --head "${branch_name}" --json number -q '.[0].number' || echo "")
    if [ -z "$pr_number" ]; then
        gh pr create \
            --title "${commit_message}" \
            --body "${pr_body}" \
            --base main \
            --head "${branch_name}" \
            --label "dependency"
    else
        gh pr edit "${pr_number}" \
            --title "${commit_message}" \
            --body "${pr_body}"
    fi
    gh pr merge --auto --merge
    git checkout main
    if [ "$CI" != "" ]; then
        git branch -D "${branch_name}"
    fi
}

GH_API_REPO=$(grep "gh.api.repo=" gradle.properties | cut -d'=' -f2)
update_and_pr \
    "updateRestSchemaVersion" \
    "gh.api.commit" \
    "update-schema-version" \
    "${GH_API_REPO}"

SCHEMASTORE_REPO=$(grep "schemastore.repo=" gradle.properties | cut -d'=' -f2)
update_and_pr \
    "updateSchemastoreVersion" \
    "schemastore.commit" \
    "update-schemastore-version" \
    "${SCHEMASTORE_REPO}"
