package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import java.io.File

class JsonRefValidator(private val threshold: Int = 0) {
    private val schemaRefRegex = Regex("\\s+schemaRef\\s=\\s*\"(.+?)\"")

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
                    .flatMap { f -> findSchemaRefMatches(f) }
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

    fun findSchemaRefMatches(file: File): List<Triple<File, Int, String>> {
        val matches = mutableListOf<Triple<File, Int, String>>()
        val fileContent = file.readText()

        schemaRefRegex.findAll(fileContent).forEach { matchResult ->
            val match = matchResult.groupValues[1] // Extract the captured group
            val startIndex = matchResult.range.first
            val precedingText = fileContent.substring(0, startIndex)
            val lineNumber = precedingText.count { it == '\n' } + 1 // Calculate line number
            matches.add(Triple(file, lineNumber, match))
        }

        return matches
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