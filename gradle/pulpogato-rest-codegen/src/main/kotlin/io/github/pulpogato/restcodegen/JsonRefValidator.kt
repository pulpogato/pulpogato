package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import java.io.File

class JsonRefValidator {
    /**
     * Validates the JSON references in given `roots` based on `json`.
     *
     * @param json The JSON to validate against.
     * @param roots The roots to search for Java files.
     */
    fun validate(
        json: JsonNode,
        roots: List<File>,
    ) {
        val count =
            roots.sumOf { dir ->
                dir.walkTopDown()
                    .filter { it.name.endsWith(".java") }
                    .flatMap {
                        it.readLines().mapIndexed { lineNumber, line -> Triple(it, lineNumber, line) }
                            .filter { l -> l.third.matches(Regex(" +from = \".+\",")) }
                            .map { x -> Triple(x.first, x.second, x.third.replace(Regex(" +from = \"(.+)\","), "$1")) }
                    }
                    .toSortedSet { o1, o2 -> o1.toString().compareTo(o2.toString()) }
                    .count { hasError(json, it) }
            }
        check(count <= 0) { "Found $count errors in JSON references" }
    }

    private fun hasError(
        json: JsonNode,
        location: Triple<File, Int, String>,
    ): Boolean {
        val parts = location.third.split("/").drop(1)
        var current: JsonNode? = json
        parts.forEach { t ->
            if (current == null) {
                val first = location.first.absolutePath.replace(Regex(".+/pulpogato/pulpogato-"), "pulpogato-")
                println("$first:${location.second + 1}: E:BAD_REF ${location.third}")
                return true
            } else {
                val name = t.replace("~1", "/")
                val index = name.toIntOrNull()
                current =
                    when {
                        index != null && index < 200 -> (current as JsonNode)[index]
                        else -> (current as JsonNode)[name]
                    }
            }
        }
        return false
    }
}