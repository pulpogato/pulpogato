package io.github.pulpogato.restcodegen

import tools.jackson.databind.JsonNode
import java.io.File

/**
 * A validator for JSON Schema references in generated Java files.
 *
 * This class validates that `$ref` pointers in annotations like `@Generated` annotations
 * in Java source files actually exist within the provided JSON Schema definitions.
 * It helps ensure that code generation has properly referenced schema elements
 * that actually exist in the source schemas.
 *
 * @property threshold The maximum number of validation errors allowed before throwing an exception.
 */
class JsonRefValidator(
    private val threshold: Int = 0,
) {
    private val schemaRefRegex = Regex("\\s+schemaRef\\s=\\s*\"(.+?)\"")
    private val sourceFileRegex = Regex("\\s+sourceFile\\s=\\s*\"(.+?)\"")

    /**
     * Validates JSON Schema references in the provided Java files against the given schemas.
     *
     * The validation process involves:
     * 1. Parsing each Java file to extract `schemaRef` and `sourceFile` values from annotations
     * 2. Looking up the referenced schema in the provided `schemas` map
     * 3. Validating that the JSON path referenced by `schemaRef` exists in the schema
     * 4. Collecting and reporting any validation errors
     * 5. Throwing an exception if the error count exceeds the threshold
     *
     * @param schemas Map of schema source files to their JSON representations.
     *                Keys are file paths to schema source files, values are the parsed JSON schema nodes.
     * @param javaFiles List of Java files to validate for schema reference correctness.
     * @throws IllegalStateException if the number of validation errors exceeds the configured threshold
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
                .map { match ->
                    val (file, lineNumber, schemaRef, sourceFile) = match

                    // Lookup the appropriate schema
                    val schema = schemas[sourceFile]

                    if (schema == null) {
                        // Error: referenced a source file that wasn't provided
                        synchronized(this) {
                            println(
                                """
                                |${file.absolutePath}:${lineNumber + 1}
                                |    Missing Schema: No schema provided for "$sourceFile"
                                |    Ref: "$schemaRef"
                                |
                                """.trimMargin(),
                            )
                        }
                        "Missing schema for $sourceFile"
                    } else {
                        hasError(schema, Triple(file, lineNumber, schemaRef)).also { error ->
                            if (error != null) {
                                synchronized(this) {
                                    println(
                                        """
                                        |${file.absolutePath}:${lineNumber + 1}
                                        |    Source File: "$sourceFile"
                                        |    Bad Ref    : "$schemaRef"
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

    /**
     * Finds all schema reference matches in the given file using regular expressions.
     *
     * This method searches for occurrences of `schemaRef` and `sourceFile` properties
     * in annotations within the provided file. It extracts the referenced schema path
     * and associated source file, then creates SchemaRefMatch objects containing
     * the file, line number, schema reference, and source file information.
     *
     * @param file The Java file to scan for schema references.
     * @return List of [SchemaRefMatch] objects representing found schema references.
     */
    fun findSchemaRefMatches(file: File): List<SchemaRefMatch> {
        val matches = mutableListOf<SchemaRefMatch>()
        val fileContent = file.readText()

        schemaRefRegex.findAll(fileContent).forEach { matchResult ->
            val schemaRef = matchResult.groupValues[1] // Extract the captured group
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

            matches.add(SchemaRefMatch(file, lineNumber, schemaRef, sourceFile))
        }

        return matches
    }

    /**
     * Checks if a JSON path exists within the provided JSON schema.
     *
     * This method navigates through the JSON schema structure following the path
     * specified in the location parameter. It handles both object properties and
     * array indices, returning null if the path is valid and the reference exists,
     * or the last verified path if the reference is invalid.
     *
     * Special case: If the reference is "#/synthetic", it returns null immediately
     * as this is considered a valid synthetic reference that doesn't need validation.
     *
     * @param json The JSON schema node to validate against.
     * @param location A triple containing the file, line number, and schema reference path to validate.
     * @return null if the path exists and is valid, otherwise returns the last verified path string.
     */
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