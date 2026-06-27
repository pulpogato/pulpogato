package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Computes sealed supertypes for REST API `oneOf` schemas that do NOT carry a discriminator.
 *
 * This is the non-discriminated counterpart to [DiscriminatedOneOfGroups]. Where a discriminator is
 * present, [DiscriminatedOneOfGroups] lets Jackson route by reading the discriminator property; here
 * there is nothing in the payload to route on, so the generated interface uses a custom deserializer
 * that tries each permitted subtype in turn (the same first-match-wins behaviour as the wrapper it
 * replaces).
 *
 * Naming is **context-derived**: each interface is identified by the location where the `oneOf`
 * occurs and named after the enclosing component schema plus the property path (e.g.
 * `integration.owner` -> `IntegrationOwner`). Unlike [DiscriminatedOneOfGroups], groups are therefore
 * keyed by location rather than by member set: two sites that happen to share a member set become two
 * interfaces (and the member classes implement both). That fragmentation is the accepted cost of a
 * name that reads from the usage site instead of from the variant list.
 *
 * Scope (intentionally narrow for now):
 * - Only `oneOf`s reached through component-schema properties are considered. Inline `oneOf`s in path
 *   responses have no natural property name to derive from and keep their existing wrapper.
 * - `empty-object` members are skipped — it is a special pulpogato type, not a generated POJO, and the
 *   unions that contain it (e.g. `commit.author`) are exercised by hand-written tests.
 * - The member count is capped to keep the prototype focused on the simple cases; large unions
 *   (`repository-rule`, `secret-scanning-location` details) are left as wrappers.
 */
object NonDiscriminatedOneOfGroups {
    data class Group(
        val supertype: ClassName,
        val memberSchemaKeys: List<String>,
        /** JSON-pointer location of the `oneOf`, matching [Context.getSchemaStackRef]. */
        val locationRef: String,
    )

    private const val COMPONENTS_SCHEMAS_PREFIX = "#/components/schemas/"
    private const val EMPTY_OBJECT = "empty-object"
    private const val MIN_MEMBERS = 2
    private const val MAX_MEMBERS = 4

    // Structural path segments carry no meaning in the human-facing type name.
    private val STRUCTURAL_SEGMENTS = setOf("properties", "items", "additionalProperties", "oneOf", "anyOf", "allOf")

    fun compute(
        openAPI: OpenAPI,
        schemasPackage: String,
    ): List<Group> {
        val componentSchemas = openAPI.components?.schemas ?: return emptyList()
        // Keyed by locationRef; LinkedHashMap keeps generation order stable across runs.
        val groups = LinkedHashMap<String, Group>()

        componentSchemas.forEach { (key, schema) ->
            walk(schema, "$COMPONENTS_SCHEMAS_PREFIX$key", componentSchemas, schemasPackage, groups)
        }

        return groups.values.toList()
    }

    /**
     * Recurses into inline subschemas only, threading the JSON-pointer [ref] of the current node.
     * `$ref`s are treated as leaves, which bounds the recursion even for mutually recursive schemas.
     */
    private fun walk(
        schema: Schema<*>?,
        ref: String,
        componentSchemas: Map<String, Schema<*>>,
        schemasPackage: String,
        groups: MutableMap<String, Group>,
    ) {
        if (schema == null || schema.`$ref` != null) return

        consider(schema, ref, componentSchemas, schemasPackage, groups)

        schema.properties?.forEach { (key, value) -> walk(value, "$ref/properties/$key", componentSchemas, schemasPackage, groups) }
        schema.items?.let { walk(it, "$ref/items", componentSchemas, schemasPackage, groups) }
        (schema.additionalProperties as? Schema<*>)?.let { walk(it, "$ref/additionalProperties", componentSchemas, schemasPackage, groups) }
        listOf("oneOf" to schema.oneOf, "anyOf" to schema.anyOf, "allOf" to schema.allOf).forEach { (label, branches) ->
            branches?.filterNotNull()?.forEachIndexed { index, branch ->
                walk(branch, "$ref/$label/$index", componentSchemas, schemasPackage, groups)
            }
        }
    }

    private fun consider(
        schema: Schema<*>,
        ref: String,
        componentSchemas: Map<String, Schema<*>>,
        schemasPackage: String,
        groups: MutableMap<String, Group>,
    ) {
        if (schema.discriminator != null) return
        val oneOf = schema.oneOf?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: return

        val refKeys = oneOf.mapNotNull { it.`$ref`?.removePrefix(COMPONENTS_SCHEMAS_PREFIX) }
        // Every branch must be a $ref to a component schema.
        if (refKeys.size != oneOf.size) return
        if (refKeys.size !in MIN_MEMBERS..MAX_MEMBERS) return
        if (refKeys.any { it == EMPTY_OBJECT }) return

        val allObjects =
            refKeys.all { key ->
                val s = componentSchemas[key] ?: return@all false
                s.types == null || s.types.contains("object") || s.properties != null
            }
        if (!allObjects) return

        // Sort members so the `permits` clause and the deserializer's candidate order are stable
        // regardless of branch declaration order. For a well-formed oneOf exactly one branch validates,
        // so try-order has no functional effect on correct payloads. The name comes from the location,
        // so it is already independent of branch order.
        val memberKeys = refKeys.sorted()
        groups[ref] = Group(ClassName.get(schemasPackage, contextName(ref)), memberKeys, ref)
    }

    /**
     * Derives a type name from the `oneOf` location: the enclosing component schema plus the property
     * path, dropping structural segments and array indices. e.g.
     * `#/components/schemas/integration/properties/owner` -> `IntegrationOwner`.
     */
    private fun contextName(ref: String): String =
        ref
            .removePrefix(COMPONENTS_SCHEMAS_PREFIX)
            .split("/")
            .filter { it.isNotEmpty() && it !in STRUCTURAL_SEGMENTS && it.toIntOrNull() == null }
            .joinToString("") { it.pascalCase() }
}