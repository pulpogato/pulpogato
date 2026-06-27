package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Computes sealed supertypes for REST API `oneOf` schemas that carry a discriminator.
 *
 * A qualifying schema must have all branches pointing to component-schema `$ref`s that resolve
 * to object types (not arrays). Duplicate groups (same member set appearing in multiple paths)
 * are collapsed into a single entry.
 *
 * Shared by [SchemasBuilder] (which emits the interfaces and tags member classes) and
 * [SchemaExtensions] (which returns the interface type instead of a wrapper class when a
 * discriminated oneOf is encountered inline in a path response).
 */
object DiscriminatedOneOfGroups {
    data class Group(
        val supertype: ClassName,
        val memberSchemaKeys: List<String>,
        val discriminatorProperty: String,
        /** Maps each member schema key to the discriminator values that select it. */
        val valuesByKey: Map<String, List<String>>,
    )

    fun compute(
        openAPI: OpenAPI,
        schemasPackage: String,
    ): List<Group> {
        val componentSchemas = openAPI.components?.schemas ?: return emptyList()
        val groups = mutableMapOf<Set<String>, Group>()

        openAPI.paths?.values?.forEach { pathItem ->
            pathItem.readOperationsMap().values.forEach { operation ->
                operation.responses?.values?.forEach { response ->
                    response?.content?.values?.forEach { mediaType ->
                        processSchema(mediaType?.schema, componentSchemas, schemasPackage, groups)
                    }
                }
            }
        }

        return groups.values.toList()
    }

    private fun processSchema(
        schema: Schema<*>?,
        componentSchemas: Map<String, Schema<*>>,
        schemasPackage: String,
        groups: MutableMap<Set<String>, Group>,
    ) {
        val discriminator = schema?.discriminator ?: return
        val discriminatorProperty = discriminator.propertyName ?: return
        val mapping = discriminator.mapping ?: return
        val oneOf = schema.oneOf?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: return

        // All branches must be $refs to component schemas
        val memberKeys =
            oneOf.mapNotNull { branch ->
                branch.`$ref`?.removePrefix("#/components/schemas/")
            }
        if (memberKeys.size != oneOf.size) return

        // All referenced schemas must be object types (not arrays)
        val allObjects =
            memberKeys.all { key ->
                val s = componentSchemas[key] ?: return@all false
                s.types == null || s.types.contains("object") || s.properties != null
            }
        if (!allObjects) return

        val memberSet = memberKeys.toSet()
        if (memberSet in groups) return

        val valuesByKey = mutableMapOf<String, MutableList<String>>()
        mapping.forEach { (value, ref) ->
            val key = ref.removePrefix("#/components/schemas/")
            if (key in memberSet) {
                valuesByKey.getOrPut(key) { mutableListOf() }.add(value)
            }
        }

        val interfaceName = computeInterfaceName(memberKeys, discriminatorProperty)
        val supertype = ClassName.get(schemasPackage, interfaceName)
        groups[memberSet] = Group(supertype, memberKeys, discriminatorProperty, valuesByKey)
    }

    /**
     * Derives the interface name from the common suffix (or prefix) of the member schema key parts.
     * Falls back to PascalCase of the discriminator property name.
     */
    fun computeInterfaceName(
        memberKeys: List<String>,
        discriminatorProperty: String,
    ): String {
        val parts = memberKeys.map { it.split("-") }
        val first = parts.first()

        // Common suffix
        for (len in first.size downTo 1) {
            val suffix = first.takeLast(len)
            if (parts.all { it.size >= len && it.takeLast(len) == suffix }) {
                return suffix.joinToString("") { it.pascalCase() }
            }
        }

        // Common prefix
        for (len in first.size downTo 1) {
            val prefix = first.take(len)
            if (parts.all { it.size >= len && it.take(len) == prefix }) {
                return prefix.joinToString("") { it.pascalCase() }
            }
        }

        return discriminatorProperty.pascalCase()
    }
}