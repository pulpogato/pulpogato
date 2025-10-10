package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import java.io.File

class JsonRefValidator(
    private val threshold: Int = 0,
) {
    private val schemaRefRegex = Regex("\\s+schemaRef\\s=\\s*\"(.+?)\"")

    /**
     * Validates the JSON references in given `javaFiles` based on `json`.
     *
     * @param json The JSON to validate against.
     * @param javaFiles List of Java files to validate.
     */
    fun validate(
        json: JsonNode,
        javaFiles: List<File>,
    ) {
        // Process files in parallel to find schema refs
        val allMatches =
            javaFiles
                .parallelStream()
                .flatMap { f -> findSchemaRefMatches(f).stream() }
                .toList()
                .sortedWith(compareBy { it.toString() })

        // Validate refs in parallel and collect errors
        val schemaRefs =
            allMatches
                .parallelStream()
                .map { triple ->
                    val (file, lineNumber, line) = triple
                    hasError(json, triple).also { error ->
                        if (error != null) {
                            synchronized(this) {
                                println(
                                    """
                                    |${file.absolutePath}:${lineNumber + 1}
                                    |    Bad Ref   : "$line"
                                    |    Last Found: "$error"
                                    |
                                    """.trimMargin(),
                                )
                            }
                        }
                    }
                }.toList()

        val errors = schemaRefs.count { it != null }
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
    ): String? {
        if (location.third == "#/synthetic") {
            return null
        }
        val parts = location.third.split("/").drop(1)
        var lastVerified = "#"
        var current: JsonNode? = json
        parts.forEach { t ->
            if (current == null) {
                return lastVerified
            } else {
                val name = t.replace("~1", "/")
                val index = if (name.matches("\\d+".toRegex())) name.toIntOrNull() else null
                current =
                    when {
                        index != null && index < 200 -> (current as JsonNode)[index]
                        else -> (current as JsonNode)[name]
                    }
                if (current != null) {
                    lastVerified += "/$t"
                }
            }
        }
        if (current == null) {
            return lastVerified
        }
        return null
    }
}