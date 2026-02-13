package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.schemaStackRef
import io.github.pulpogato.restcodegen.updateSchemaStack
import tools.jackson.databind.node.ObjectNode

/**
 * Holds context for a single JSON Schema file being processed.
 *
 * @property rootSchema The parsed root schema node
 * @property definitionRegistry Pre-registered type names for `$ref` resolution and cycle breaking
 * @property generatedTypes Accumulated generated type specs keyed by fully-qualified class name
 * @property resolvedDefinitions Set of definition names that have been fully resolved
 * @property typeAliases Map of definition name to resolved TypeName for definitions that are simple type aliases
 */
data class JsonSchemaContext(
    val rootSchema: ObjectNode,
    val sourceFile: String,
    val definitionRegistry: MutableMap<String, ClassName> = mutableMapOf(),
    val generatedTypes: MutableMap<String, TypeSpec> = mutableMapOf(),
    val resolvedDefinitions: MutableSet<String> = mutableSetOf(),
    val typeAliases: MutableMap<String, TypeName> = mutableMapOf(),
    val schemaStack: List<String> = emptyList(),
) {
    fun withSchemaStack(vararg elements: String): JsonSchemaContext = copy(schemaStack = updateSchemaStack(schemaStack, *elements))

    fun getSchemaStackRef(): String = schemaStackRef(schemaStack).ifBlank { "#" }
}