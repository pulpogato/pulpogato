package io.github.pulpogato.restcodegen

import java.io.File

/**
 * Represents a match of a schema reference in a Java file.
 *
 * @property file The Java file containing the reference
 * @property lineNumber The line number where the schema reference was found
 * @property schemaRef The actual schema reference value (e.g., "#/path/to/definition")
 * @property sourceFile The source file name (e.g., "schema.json")
 */
data class SchemaRefMatch(
    val file: File,
    val lineNumber: Int,
    val schemaRef: String,
    val sourceFile: String,
)