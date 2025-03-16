package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

object IgnoredTests {
    fun compute(): Map<String, Map<String, String>> {
        val tests =
            ObjectMapper(YAMLFactory()).readValue(
                IgnoredTests.javaClass.getResourceAsStream("/IgnoredTests.yml"),
                object : TypeReference<List<IgnoredTest>>() {},
            )
        val result =
            tests
                .flatMap { test ->
                    test.versions!!.map { ver -> mapOf("version" to ver, "example" to test.example!!, "reason" to test.reason) }
                }.groupBy { it["version"] }
                .map { (version, tests) -> version as String to tests.associate { it["example"] as String to it["reason"] as String } }
                .toMap()
        return result
    }

    class IgnoredTest() {
        var example: String? = null
        var reason: String? = null
        var versions: List<String>? = null
    }

    /**
     * This maps the schemaRef of the test to the reason why the test is ignored.
     * Ideally, this should be a link to an issue on [github/rest-api-description](https://github.com/github/rest-api-description/issues).
     */
    val causes = compute()
}