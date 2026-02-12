package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import tools.jackson.databind.node.ObjectNode

/**
 * Holds context for a single JSON Schema file being processed.
 *
 * @property rootSchema The parsed root schema node
 * @property packageName The target Java package for generated types
 * @property schemaPath The file path of the schema (for diagnostics)
 * @property definitionRegistry Pre-registered type names for `$ref` resolution and cycle breaking
 * @property generatedTypes Accumulated generated type specs keyed by fully-qualified class name
 * @property resolvedDefinitions Set of definition names that have been fully resolved
 * @property typeAliases Map of definition name to resolved TypeName for definitions that are simple type aliases
 */
data class JsonSchemaContext(
    val rootSchema: ObjectNode,
    val packageName: String,
    val schemaPath: String,
    val definitionRegistry: MutableMap<String, ClassName> = mutableMapOf(),
    val generatedTypes: MutableMap<String, TypeSpec> = mutableMapOf(),
    val resolvedDefinitions: MutableSet<String> = mutableSetOf(),
    val typeAliases: MutableMap<String, TypeName> = mutableMapOf(),
)