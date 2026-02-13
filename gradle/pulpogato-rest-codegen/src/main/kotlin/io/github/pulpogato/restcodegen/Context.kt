package io.github.pulpogato.restcodegen

import io.swagger.v3.oas.models.OpenAPI

/**
 * Context for REST code generation operations.
 *
 * This class holds the necessary information for code generation including
 * the OpenAPI specification, version information, schema stack for tracking
 * nested schema references, and a map of added properties to avoid duplicates.
 *
 * @property openAPI The OpenAPI specification being processed
 * @property version The version of the OpenAPI specification
 * @property schemaStack A list representing the current path in the schema hierarchy,
 *                      used for generating unique identifiers and references
 * @property addedProperties A map of schema names to their property names that have
 *                          already been added from external schema additions (e.g., additions.schema.json)
 *                          to prevent duplication during code generation
 */
data class Context(
    val openAPI: OpenAPI,
    val version: String,
    val schemaStack: List<String>,
    val addedProperties: Map<String, Map<String, String>> = emptyMap(),
) {
    // Cached schema stack reference to avoid repeated string operations
    private val cachedSchemaStackRef: String by lazy { schemaStackRef(schemaStack) }

    /**
     * Creates a new context with an updated schema stack.
     *
     * If the first element is "#", the schema stack is cleared before adding the new elements.
     * Otherwise, the new elements are appended to the existing stack.
     *
     * @param elements The elements to add to the schema stack
     * @return A new [Context] instance with the updated schema stack
     */
    fun withSchemaStack(vararg elements: String): Context = copy(schemaStack = updateSchemaStack(schemaStack, *elements))

    /**
     * Gets the schema stack reference as a string with proper escaping.
     *
     * The elements are joined with "/" and any "/" characters within the elements
     * are escaped as "~1" according to JSON Pointer specification.
     *
     * @return The schema stack reference as a properly formatted string
     */
    fun getSchemaStackRef() = cachedSchemaStackRef

    /**
     * Gets the source file for an added property.
     *
     * This method is used to determine if a property was added from an external schema
     * additions file (like additions.schema.json) rather than being part of the original
     * OpenAPI specification.
     *
     * @param schemaName The name of the schema to check
     * @param propertyName The name of the property to check
     * @return The source file name if the property was added, null otherwise
     */
    fun getAddedPropertySource(
        schemaName: String,
        propertyName: String,
    ): String? = addedProperties[schemaName]?.get(propertyName)
}