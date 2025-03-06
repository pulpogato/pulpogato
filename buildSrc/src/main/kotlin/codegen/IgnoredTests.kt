package codegen

object IgnoredTests {
    /**
     * This maps the schemaRef of the test to the reason why the test is ignored.
     * Ideally, this should be a link to an issue on [github/rest-api-description](https://github.com/github/rest-api-description/issues).
     */
    val causes =
        mapOf(
            "ghes-3.12" to mapOf(
                "#/paths/~1markdown~1raw/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Something to do with MIME types",
                "#/paths/~1repos~1{owner}~1{repo}~1pages~1deployments/post/requestBody/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4471",
                "#/paths/~1scim~1v2~1enterprises~1{enterprise}~1Groups~1{scim_group_id}/patch/requestBody/content/application~1json/examples/addMembers/value"
                        to "https://github.com/github/rest-api-description/issues/4470",
                "#/paths/~1zen/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4427",
                "#/webhooks/secret-scanning-alert-location-created/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4472",
                "#/paths/~1repos~1{owner}~1{repo}~1replicas~1caches/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4590",
                "#/webhooks/check-run-rerequested/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4593",
                "#/webhooks/check-run-requested-action/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-created/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-completed/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
            ),
            "ghes-3.13" to mapOf(
                "#/paths/~1markdown~1raw/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Something to do with MIME types",
                "#/paths/~1repos~1{owner}~1{repo}~1pages~1deployments/post/requestBody/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4471",
                "#/paths/~1scim~1v2~1enterprises~1{enterprise}~1Groups~1{scim_group_id}/patch/requestBody/content/application~1json/examples/addMembers/value"
                        to "https://github.com/github/rest-api-description/issues/4470",
                "#/paths/~1zen/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4427",
                "#/webhooks/secret-scanning-alert-location-created/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4472",
                "#/paths/~1repos~1{owner}~1{repo}~1replicas~1caches/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4590",
                "#/webhooks/check-run-rerequested/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4593",
                "#/webhooks/check-run-requested-action/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-created/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-completed/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
            ),
            "ghes-3.14" to mapOf(
                "#/paths/~1markdown~1raw/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Something to do with MIME types",
                "#/paths/~1orgs~1{org}~1organization-roles/post/responses/201/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1orgs~1{org}~1organization-roles~1{role_id}/patch/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1pages~1deployments/post/requestBody/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4471",
                "#/paths/~1scim~1v2~1enterprises~1{enterprise}~1Groups~1{scim_group_id}/patch/requestBody/content/application~1json/examples/addMembers/value"
                        to "https://github.com/github/rest-api-description/issues/4470",
                "#/paths/~1zen/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4427",
                "#/webhooks/secret-scanning-alert-location-created/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4472",
                "#/paths/~1repos~1{owner}~1{repo}~1replicas~1caches/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4590",
                "#/webhooks/check-run-rerequested/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4593",
                "#/webhooks/check-run-requested-action/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-created/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-completed/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
            ),
            "ghes-3.15" to mapOf(
                "#/paths/~1markdown~1raw/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Something to do with MIME types",
                "#/paths/~1orgs~1{org}~1organization-roles/post/responses/201/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1orgs~1{org}~1organization-roles~1{role_id}/patch/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1pages~1deployments/post/requestBody/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4471",
                "#/paths/~1scim~1v2~1enterprises~1{enterprise}~1Groups~1{scim_group_id}/patch/requestBody/content/application~1json/examples/addMembers/value"
                        to "https://github.com/github/rest-api-description/issues/4470",
                "#/paths/~1zen/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4427",
                "#/webhooks/secret-scanning-alert-location-created/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4472",
                "#/paths/~1orgs~1{org}~1code-security~1configurations~1{configuration_id}~1repositories/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1replicas~1caches/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4590",
                "#/webhooks/check-run-rerequested/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4593",
                "#/webhooks/check-run-requested-action/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-created/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-completed/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
            ),
            "ghec" to mapOf(
                "#/paths/~1markdown~1raw/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Something to do with MIME types",
                "#/paths/~1orgs~1{org}~1organization-roles/post/responses/201/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1orgs~1{org}~1organization-roles~1{role_id}/patch/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1scim~1v2~1enterprises~1{enterprise}~1Groups~1{scim_group_id}/patch/requestBody/content/application~1json/examples/addMembers/value"
                        to "https://github.com/github/rest-api-description/issues/4470",
                "#/paths/~1zen/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4427",
                "#/webhooks/secret-scanning-alert-location-created/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4472",
                "#/paths/~1orgs~1{org}~1code-security~1configurations~1{configuration_id}~1repositories/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-rerequested/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4593",
                "#/webhooks/check-run-requested-action/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-created/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-completed/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1code-scanning~1codeql~1variant-analyses/post/responses/201/content/application~1json/examples/repositories_parameter/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1code-scanning~1codeql~1variant-analyses/post/responses/201/content/application~1json/examples/repository_owners/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1code-scanning~1codeql~1variant-analyses/post/responses/201/content/application~1json/examples/repository_lists/value"
                        to "TODO: Diagnose this",
                "#/paths/~1enterprises~1{enterprise}~1code-security~1configurations~1{configuration_id}~1repositories/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1user~1codespaces~1{codespace_name}~1publish/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1user~1codespaces~1secrets~1{secret_name}~1repositories/put/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1user~1codespaces~1secrets~1{secret_name}/put/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1pulls~1{pull_number}~1codespaces/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1versions/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1import/patch/requestBody/content/application~1json/examples/example-2/value"
                        to "TODO: Diagnose this",
                "#/paths/~1enterprises~1{enterprise}~1rulesets~1{ruleset_id}/put/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1enterprises~1{enterprise}~1rulesets/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1scim~1v2~1organizations~1{org}~1Users~1{scim_user_id}/patch/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1scim~1v2~1organizations~1{org}~1Users~1{scim_user_id}/put/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1scim~1v2~1organizations~1{org}~1Users/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1security-advisories~1{ghsa_id}/patch/requestBody/content/application~1json/examples/update_vvrs/value"
                        to "TODO: Diagnose this",
            ),
            "fpt" to mapOf(
                "#/paths/~1markdown~1raw/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Something to do with MIME types",
                "#/paths/~1zen/get/responses/200/content/application~1json/examples/default/value"
                        to "https://github.com/github/rest-api-description/issues/4427",
                "#/webhooks/secret-scanning-alert-location-created/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4472",
                "#/paths/~1orgs~1{org}~1code-security~1configurations~1{configuration_id}~1repositories/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-rerequested/post/requestBody/content/application~1json/examples"
                        to "https://github.com/github/rest-api-description/issues/4593",
                "#/webhooks/check-run-requested-action/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-created/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/webhooks/check-run-completed/post/requestBody/content/application~1json/examples"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1code-scanning~1codeql~1variant-analyses/post/responses/201/content/application~1json/examples/repositories_parameter/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1code-scanning~1codeql~1variant-analyses/post/responses/201/content/application~1json/examples/repository_owners/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1code-scanning~1codeql~1variant-analyses/post/responses/201/content/application~1json/examples/repository_lists/value"
                        to "TODO: Diagnose this",
                "#/paths/~1enterprises~1{enterprise}~1code-security~1configurations~1{configuration_id}~1repositories/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1user~1codespaces~1{codespace_name}~1publish/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1user~1codespaces~1secrets~1{secret_name}~1repositories/put/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1user~1codespaces~1secrets~1{secret_name}/put/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1pulls~1{pull_number}~1codespaces/post/requestBody/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1versions/get/responses/200/content/application~1json/examples/default/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1import/patch/requestBody/content/application~1json/examples/example-2/value"
                        to "TODO: Diagnose this",
                "#/paths/~1repos~1{owner}~1{repo}~1security-advisories~1{ghsa_id}/patch/requestBody/content/application~1json/examples/update_vvrs/value"
                        to "TODO: Diagnose this",
            ),
        )
}