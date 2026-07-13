package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema

/**
 * Computes the sealed supertypes generated for webhook subcategories that have more than one event.
 *
 * Shared by [SchemasBuilder] (which emits the interfaces and tags the member body classes) and
 * [WebhooksBuilder] (which routes the synthetic handler straight to the typed supertype when it can be
 * discriminated). Keeping the grouping in one place ensures both builders agree on which subcategories
 * have a supertype and whether it can be deserialized polymorphically.
 */
object WebhookSupertypes {
    data class Group(
        val subcategory: String,
        val supertype: ClassName,
        val memberSchemaKeys: List<String>,
        val actionsByKey: Map<String, List<String>?>,
    ) {
        /**
         * True when every member is identified by a distinct `action` value, so Jackson can pick the
         * concrete subtype from the payload. When false the supertype is still generated for pattern
         * matching, but callers must deserialize the members themselves.
         */
        val discriminable: Boolean =
            memberSchemaKeys.all { !actionsByKey[it].isNullOrEmpty() } &&
                memberSchemaKeys.flatMap { actionsByKey[it].orEmpty() }.let { it.size == it.toSet().size }
    }

    fun compute(
        openAPI: OpenAPI,
        schemasPackage: String,
    ): List<Group> {
        val webhooks = openAPI.webhooks ?: return emptyList()
        val existingSchemaNames =
            openAPI.components.schemas.keys
                .map { it.pascalCase() }
                .toSet()
        return webhooks.entries
            .groupBy {
                subcategoryOf(
                    it.value
                        .readOperationsMap()
                        .values
                        .first(),
                )
            }.mapNotNull { (subcategory, members) ->
                if (members.size <= 1) return@mapNotNull null
                val interfaceName = "Webhook${subcategory.pascalCase()}"
                // A single-event subcategory's body is itself named webhook-<subcategory>; never clobber
                // a real schema with a synthetic supertype of the same name.
                if (interfaceName in existingSchemaNames) return@mapNotNull null
                val memberKeys = members.mapNotNull { requestBodySchemaKey(it.value) }.distinct()
                if (memberKeys.size <= 1) return@mapNotNull null
                val actions = memberKeys.associateWith { key -> discriminatorValues(openAPI.components.schemas[key]) }
                Group(subcategory, ClassName.get(schemasPackage, interfaceName), memberKeys, actions)
            }
    }

    /**
     * The body schema keys for subcategories with exactly one event, i.e. the schemas that are used
     * directly as a `@RequestBody` rather than as a permitted subtype of a generated sealed supertype.
     * Mirrors the `springEndpoint = v.size == 1` grouping in [WebhooksBuilder].
     */
    fun singleEventBodyKeys(openAPI: OpenAPI): Set<String> {
        val webhooks = openAPI.webhooks ?: return emptySet()
        return webhooks.entries
            .groupBy {
                subcategoryOf(
                    it.value
                        .readOperationsMap()
                        .values
                        .first(),
                )
            }.values
            .filter { it.size == 1 }
            .mapNotNull { requestBodySchemaKey(it.first().value) }
            .toSet()
    }

    /**
     * The distinct values of a schema's `action` discriminator, or null when it can't be determined.
     *
     * Plain object bodies declare `action` directly. Composite (`oneOf`/`anyOf`) bodies — e.g.
     * `webhook-pull-request-review-requested` — declare it inside every branch instead; those are
     * supported as long as every branch agrees on the same enum value(s).
     */
    private fun discriminatorValues(schema: Schema<*>?): List<String>? {
        if (schema == null) return null
        actionEnum(schema)?.let { return it }

        val branches = (schema.oneOf.orEmpty() + schema.anyOf.orEmpty()).filterNotNull()
        if (branches.isEmpty()) return null
        val perBranch = branches.map { actionEnum(it) }
        // Every branch must contribute a discriminator, otherwise the body isn't safely discriminable.
        if (perBranch.any { it == null }) return null
        return perBranch
            .filterNotNull()
            .flatten()
            .distinct()
            .ifEmpty { null }
    }

    private fun actionEnum(schema: Schema<*>): List<String>? {
        val action = schema.properties?.get("action") ?: return null
        val values = action.enum?.mapNotNull { it?.toString() } ?: return null
        return values.ifEmpty { null }
    }

    private fun requestBodySchemaKey(pathItem: PathItem): String? {
        val operation = pathItem.readOperationsMap().values.firstOrNull() ?: return null
        val mediaType =
            operation.requestBody
                ?.content
                ?.entries
                ?.firstOrNull { it.key.contains("json") }
                ?.value ?: return null
        val ref = mediaType.schema?.`$ref` ?: return null
        return ref.replace("#/components/schemas/", "")
    }

    private fun subcategoryOf(operation: Operation): String {
        val xGitHub = operation.extensions["x-github"] as? Map<*, *> ?: throw RuntimeException("Missing x-github extension")
        return xGitHub["subcategory"] as String
    }
}