package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import java.io.File

class JsonRefValidator(private val threshold: Int = 0) {
    private val schemaRefRegex = Regex(".+schemaRef *= *\"(.+?)\".*")

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
        val schemaRefs =
            roots.flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.name.endsWith(".java") }
                    .flatMap {
                        it.readLines().mapIndexed { lineNumber, line -> Triple(it, lineNumber, line) }
                            .filter { (_, _, line) -> line.matches(schemaRefRegex) }
                            .map { (file, lineNumber, line) -> Triple(file, lineNumber, line.replace(schemaRefRegex, "$1")) }
                    }
                    .toSortedSet { o1, o2 -> o1.toString().compareTo(o2.toString()) }
                    .map {
                        val (file, lineNumber, line) = it
                        hasError(json, it).also { l ->
                            if (l) {
                                println("${file.absolutePath}:${lineNumber + 1}: E:BAD_REF \"${line}\"\n")
                            }
                        }
                    }
            }
        val errors = schemaRefs.count { it }
        val total = schemaRefs.size
        check(errors <= threshold) { "Found $errors errors in $total JSON references" }
        println("Found $errors errors in $total JSON references")
    }

    private fun hasError(
        json: JsonNode,
        location: Triple<File, Int, String>,
    ): Boolean {
        val parts = location.third.split("/").drop(1)
        var current: JsonNode? = json
        parts.forEach { t ->
            if (current == null) {
                return true
            } else {
                val name = t.replace("~1", "/")
                val index = if (name.matches("\\d+".toRegex())) name.toIntOrNull() else null
                current =
                    when {
                        index != null && index < 200 -> (current as JsonNode)[index]
                        else -> (current as JsonNode)[name]
                    }
            }
        }
        if (current == null) {
            return true
        }
        return false
    }
}