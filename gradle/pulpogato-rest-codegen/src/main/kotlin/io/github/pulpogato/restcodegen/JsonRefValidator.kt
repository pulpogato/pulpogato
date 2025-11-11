package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import java.io.File

data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

class JsonRefValidator(
    private val threshold: Int = 0,
) {
    private val schemaRefRegex = Regex("\\s+schemaRef\\s=\\s*\"(.+?)\"")
    private val sourceFileRegex = Regex("\\s+sourceFile\\s=\\s*\"(.+?)\"")

    /**
     * Validates the JSON references in given `javaFiles` based on `schemas`.
     *
     * @param schemas Map of schema source files to their JSON representations.
     * @param javaFiles List of Java files to validate.
     */
    fun validate(
        schemas: Map<String, JsonNode>,
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
                .map { quad ->
                    val (file, lineNumber, line, sourceFile) = quad

                    // Lookup the appropriate schema
                    val schema = schemas[sourceFile]

                    if (schema == null) {
                        // Error: referenced a source file that wasn't provided
                        synchronized(this) {
                            println(
                                """
                                |${file.absolutePath}:${lineNumber + 1}
                                |    Missing Schema: No schema provided for "$sourceFile"
                                |    Ref: "$line"
                                |
                                """.trimMargin(),
                            )
                        }
                        "Missing schema for $sourceFile"
                    } else {
                        hasError(schema, Triple(file, lineNumber, line)).also { error ->
                            if (error != null) {
                                synchronized(this) {
                                    println(
                                        """
                                        |${file.absolutePath}:${lineNumber + 1}
                                        |    Source File: "$sourceFile"
                                        |    Bad Ref    : "$line"
                                        |    Last Found : "$error"
                                        |
                                        """.trimMargin(),
                                    )
                                }
                            }
                        }
                    }
                }.toList()

        val errors = schemaRefs.count { it != null }
        val total = schemaRefs.size
        check(errors <= threshold) { "Found $errors errors in $total JSON references" }
        println("Found $errors errors in $total JSON references")
    }

    fun findSchemaRefMatches(file: File): List<Quad<File, Int, String, String>> {
        val matches = mutableListOf<Quad<File, Int, String, String>>()
        val fileContent = file.readText()

        schemaRefRegex.findAll(fileContent).forEach { matchResult ->
            val match = matchResult.groupValues[1] // Extract the captured group
            val startIndex = matchResult.range.first
            val precedingText = fileContent.take(startIndex)
            val lineNumber = precedingText.count { it == '\n' } + 1 // Calculate line number

            // Find sourceFile on the same or following lines within the same annotation
            val remainingText = fileContent.substring(matchResult.range.last)
            val annotationEnd = remainingText.indexOf(')')
            val sourceFileMatch =
                if (annotationEnd != -1) {
                    sourceFileRegex.find(remainingText.take(annotationEnd))
                } else {
                    null
                }
            val sourceFile = sourceFileMatch?.groupValues?.get(1) ?: "schema.json"

            matches.add(Quad(file, lineNumber, match, sourceFile))
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
        val lastVerified = StringBuilder("#")
        var current: JsonNode? = json
        parts.forEach { t ->
            if (current == null) {
                return lastVerified.toString()
            } else {
                val name = t.replace("~1", "/")
                // Check if it's a non-negative integer (array index)
                // Must be all digits to qualify as an index
                val index = if (name.all { it.isDigit() }) name.toIntOrNull() else null
                current =
                    when {
                        index != null && index < 200 -> current[index]
                        else -> current[name]
                    }
                if (current != null) {
                    lastVerified.append('/').append(t)
                }
            }
        }
        if (current == null) {
            return lastVerified.toString()
        }
        return null
    }
}