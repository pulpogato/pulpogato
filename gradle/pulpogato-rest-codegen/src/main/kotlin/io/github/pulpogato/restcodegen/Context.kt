package io.github.pulpogato.restcodegen

import io.swagger.v3.oas.models.OpenAPI

data class Context(
    val openAPI: OpenAPI,
    val version: String,
    val schemaStack: List<String>,
) {
    // Cached schema stack reference to avoid repeated string operations
    private val cachedSchemaStackRef: String by lazy {
        schemaStack.joinToString("/") { it.replace("/", "~1") }
    }

    fun withSchemaStack(vararg elements: String): Context {
        val newStack = schemaStack.toMutableList()
        if (elements.isNotEmpty() && elements.first() == "#") {
            newStack.clear()
        }
        newStack.addAll(elements)
        return copy(schemaStack = newStack)
    }

    fun getSchemaStackRef() = cachedSchemaStackRef
}