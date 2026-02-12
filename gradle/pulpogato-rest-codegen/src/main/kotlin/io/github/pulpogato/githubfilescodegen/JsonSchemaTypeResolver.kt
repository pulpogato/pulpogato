package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Types
import io.github.pulpogato.restcodegen.ext.pascalCase
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * Core recursive type resolver for JSON Schema → Java type mapping.
 *
 * Returns a pair of (TypeName for fields, optional TypeSpec if a new type was generated).
 */
object JsonSchemaTypeResolver {
    /**
     * Resolves a JSON Schema node into a Java type.
     *
     * @param ctx The schema context
     * @param name The name to use for any generated class
     * @param node The JSON Schema node
     * @param parentPackage The package to place generated types in
     * @return A pair of (Java TypeName, optional generated TypeSpec)
     */
    fun resolveType(
        ctx: JsonSchemaContext,
        name: String,
        node: JsonNode,
        parentPackage: String,
    ): ResolvedType {
        if (!node.isObject) {
            // Bare type like `true` or `false` in additionalProperties
            return ResolvedType(Types.OBJECT)
        }

        val obj = node as ObjectNode

        // 1. $ref — look up definition
        val ref = obj.get("\$ref")
        if (ref != null) {
            return resolveRef(ctx, ref.asString(), parentPackage)
        }

        // 2. patternProperties — Map<String, T>
        val patternProps = obj.get("patternProperties")
        if (patternProps != null && patternProps.isObject) {
            return resolvePatternProperties(ctx, name, patternProps as ObjectNode, parentPackage)
        }

        // 3. oneOf with $ref variants → union type
        val oneOf = obj.get("oneOf")
        if (oneOf != null && oneOf.isArray) {
            return resolveOneOf(ctx, name, oneOf as ArrayNode, parentPackage, obj)
        }

        // 4. anyOf — typically validation-only; collapse to widest type
        val anyOf = obj.get("anyOf")
        if (anyOf != null && anyOf.isArray) {
            return resolveAnyOf(ctx, name, anyOf as ArrayNode, parentPackage)
        }

        // 5. allOf — merge all sub-schema properties
        val allOf = obj.get("allOf")
        if (allOf != null && allOf.isArray) {
            return resolveAllOf(ctx, name, allOf as ArrayNode, parentPackage, obj)
        }

        // 6. enum
        val enumNode = obj.get("enum")
        if (enumNode != null && enumNode.isArray) {
            return resolveEnum(ctx, name, enumNode as ArrayNode, obj, parentPackage)
        }

        // 7. const — just a string constraint
        val constNode = obj.get("const")
        if (constNode != null) {
            return ResolvedType(Types.STRING)
        }

        // 8. type-based dispatch
        val typeNode = obj.get("type")
        if (typeNode != null) {
            if (typeNode.isArray) {
                // Multi-type array like ["string", "integer"] → Object
                return ResolvedType(Types.OBJECT)
            }
            return resolveByType(ctx, name, typeNode.asString(), obj, parentPackage)
        }

        // 9. object with properties but no explicit type
        val properties = obj.get("properties")
        if (properties != null && properties.isObject) {
            return resolveObject(ctx, name, obj, parentPackage)
        }

        // 10. additionalProperties with $ref
        val additionalProps = obj.get("additionalProperties")
        if (additionalProps != null && additionalProps.isObject) {
            val valueType = resolveType(ctx, "${name}Value", additionalProps, parentPackage)
            return ResolvedType(
                ParameterizedTypeName.get(Types.MAP, Types.STRING, valueType.typeName),
                valueType.typeSpec,
            )
        }

        // Fallback
        return ResolvedType(Types.OBJECT)
    }

    internal fun resolveRef(
        ctx: JsonSchemaContext,
        ref: String,
        parentPackage: String,
    ): ResolvedType {
        // e.g. "#/definitions/normalJob"
        val defName = ref.substringAfterLast("/")

        // If already fully resolved, return the known type
        if (defName in ctx.resolvedDefinitions) {
            val alias = ctx.typeAliases[defName]
            if (alias != null) {
                return ResolvedType(alias)
            }
            val registered = ctx.definitionRegistry[defName]
            if (registered != null) {
                // Check if this is a generated type or just a pre-registered name
                if (ctx.generatedTypes.containsKey(registered.toString())) {
                    return ResolvedType(registered)
                }
                // Pre-registered but no generated type and no alias — resolve again
                // This can happen if initial resolution was part of a cycle
            }
        }

        // If pre-registered but not yet resolved
        val registered = ctx.definitionRegistry[defName]
        if (registered != null && defName !in ctx.resolvedDefinitions) {
            // Mark as "being resolved" to detect cycles
            ctx.resolvedDefinitions.add(defName)

            val definitions = ctx.rootSchema.get("definitions") ?: return ResolvedType(registered)
            val defNode = definitions.get(defName) ?: return ResolvedType(registered)

            val resolved = resolveType(ctx, defName.pascalCase(), defNode, parentPackage)

            if (resolved.typeSpec != null) {
                ctx.generatedTypes[registered.toString()] = resolved.typeSpec
                return ResolvedType(registered, resolved.typeSpec)
            } else if (resolved.typeName != registered) {
                // This definition resolved to a simple type alias, not a generated class
                ctx.typeAliases[defName] = resolved.typeName
                return resolved
            }

            // Resolution returned the same ClassName but no typeSpec — possible cycle placeholder
            return ResolvedType(registered)
        }

        // Not pre-registered at all — register and resolve
        val className = ClassName.get(parentPackage, defName.pascalCase())
        ctx.definitionRegistry[defName] = className
        ctx.resolvedDefinitions.add(defName)

        val definitions = ctx.rootSchema.get("definitions") ?: return ResolvedType(Types.OBJECT)
        val defNode = definitions.get(defName) ?: return ResolvedType(Types.OBJECT)

        val resolved = resolveType(ctx, defName.pascalCase(), defNode, parentPackage)

        if (resolved.typeSpec != null) {
            ctx.generatedTypes[className.toString()] = resolved.typeSpec
        } else if (resolved.typeName != className) {
            ctx.typeAliases[defName] = resolved.typeName
            return resolved
        }

        return ResolvedType(className, resolved.typeSpec)
    }

    private fun resolvePatternProperties(
        ctx: JsonSchemaContext,
        name: String,
        patternProps: ObjectNode,
        parentPackage: String,
    ): ResolvedType {
        // Take the first (typically only) pattern's value schema
        val entry = patternProps.properties().iterator().next()
        val valueSchema = entry.value

        val valueName = "${name}Value"
        val valueResolved = resolveType(ctx, valueName, valueSchema, parentPackage)

        val mapType = ParameterizedTypeName.get(Types.MAP, Types.STRING, valueResolved.typeName)
        return ResolvedType(mapType, valueResolved.typeSpec)
    }

    private fun resolveOneOf(
        ctx: JsonSchemaContext,
        name: String,
        oneOf: ArrayNode,
        parentPackage: String,
        parentNode: ObjectNode,
    ): ResolvedType {
        // Filter out null type elements — they just mean the value is nullable
        val elements =
            oneOf.toList().filter {
                !(it.has("type") && it.get("type").asString() == "null")
            }

        // If only one element remains after filtering nulls, just resolve it
        if (elements.size == 1) {
            return resolveType(ctx, name, elements[0], parentPackage)
        }

        if (elements.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        // Check if this is a oneOf of $ref types → union type
        val refElements = elements.filter { it.has("\$ref") }
        val simpleTypeElements = elements.filter { it.has("type") && !it.has("properties") }

        // If all elements are simple types (string/boolean/number) → Object
        if (simpleTypeElements.size == elements.size && refElements.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        // If this is a validation-only oneOf (e.g., required-field sets), just resolve the parent as object
        val requiredOnlyElements = elements.filter { it.has("required") && it.size() == 1 }
        if (requiredOnlyElements.size == elements.size) {
            // This is a validation constraint, not a type union
            return ResolvedType(Types.OBJECT)
        }

        // Build union variants
        val variants = mutableListOf<UnionGenerator.VariantSpec>()

        elements.forEach { element ->
            val resolved =
                if (element.has($$"$ref")) {
                    resolveType(ctx, name, element, parentPackage)
                } else if (element.has("type") && element.get("type").asString() == "object" && element.has("properties")) {
                    val variantName = "${name}Variant${variants.size}"
                    resolveType(ctx, variantName, element, parentPackage)
                } else if (element.has("type")) {
                    resolveType(ctx, name, element, parentPackage)
                } else {
                    resolveType(ctx, "${name}Variant${variants.size}", element, parentPackage)
                }

            val fieldName = typeNameToFieldName(resolved.typeName)
            // Deduplicate variants with the same field name
            if (variants.none { it.fieldName == fieldName }) {
                variants.add(UnionGenerator.VariantSpec(fieldName, resolved.typeName))
            }
        }

        // If we only have one real variant + simple types, just return Object
        if (variants.all { isSimpleTypeName(it.typeName) }) {
            return ResolvedType(Types.OBJECT)
        }

        // If there's only one non-simple variant left, just return it directly
        if (variants.size == 1) {
            return ResolvedType(variants[0].typeName)
        }

        val description = parentNode.get("description")?.asString()
        val unionSpec = UnionGenerator.generate(name, variants, description)
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = unionSpec
        return ResolvedType(className, unionSpec)
    }

    private fun resolveAnyOf(
        ctx: JsonSchemaContext,
        name: String,
        anyOf: ArrayNode,
        parentPackage: String,
    ): ResolvedType {
        val elements = anyOf.toList()

        // anyOf in these schemas is typically validation-only (e.g., shell can be any string OR specific enum)
        // Check if there's a string type — if so, collapse to String
        val hasString = elements.any { it.has("type") && it.get("type").asString() == "string" }
        if (hasString) {
            return ResolvedType(Types.STRING)
        }

        // Check if all are $refs or typed
        if (elements.size == 1) {
            return resolveType(ctx, name, elements[0], parentPackage)
        }

        // Otherwise treat as Object
        return ResolvedType(Types.OBJECT)
    }

    private fun resolveAllOf(
        ctx: JsonSchemaContext,
        name: String,
        allOf: ArrayNode,
        parentPackage: String,
        parentNode: ObjectNode,
    ): ResolvedType {
        // Merge all properties from all sub-schemas
        val mergedProps = mutableListOf<ObjectGenerator.PropertySpec>()

        allOf.forEach { element ->
            if (element.has("properties") && element.get("properties").isObject) {
                val props = element.get("properties") as ObjectNode
                props.properties().forEach { (propName, propSchema) ->
                    val propResolved = resolveType(ctx, "${name}${propName.pascalCase()}", propSchema, parentPackage)
                    mergedProps.add(
                        ObjectGenerator.PropertySpec(
                            propName,
                            propResolved.typeName,
                            propSchema.get("description")?.asString(),
                        ),
                    )
                }
            } else if (element.has("\$ref")) {
                // Resolve the ref and merge its properties if it's an object
                val resolved = resolveType(ctx, name, element, parentPackage)
                // Just use the ref type directly if we can't merge
                if (mergedProps.isEmpty() && allOf.size() == 1) {
                    return resolved
                }
            }
        }

        if (mergedProps.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        val description = parentNode.get("description")?.asString()
        val spec = ObjectGenerator.generate(name, mergedProps, description)
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = spec
        return ResolvedType(className, spec)
    }

    private fun resolveEnum(
        ctx: JsonSchemaContext,
        name: String,
        enumNode: ArrayNode,
        parentNode: ObjectNode,
        parentPackage: String,
    ): ResolvedType {
        val values = enumNode.map { it.asString() }
        val description = parentNode.get("description")?.asString()
        val spec = EnumGenerator.generate(name, values, description)
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = spec
        return ResolvedType(className, spec)
    }

    private fun resolveByType(
        ctx: JsonSchemaContext,
        name: String,
        type: String,
        node: ObjectNode,
        parentPackage: String,
    ): ResolvedType =
        when (type) {
            "string" -> {
                val format = node.get("format")?.asString()
                when (format) {
                    "date-time" -> ResolvedType(ClassName.get(java.time.OffsetDateTime::class.java))
                    else -> ResolvedType(Types.STRING)
                }
            }

            "integer" -> {
                val format = node.get("format")?.asString()
                when (format) {
                    "int32" -> ResolvedType(Types.INTEGER)
                    else -> ResolvedType(Types.LONG)
                }
            }

            "number" -> {
                ResolvedType(Types.BIG_DECIMAL)
            }

            "boolean" -> {
                ResolvedType(Types.BOOLEAN)
            }

            "array" -> {
                resolveArray(ctx, name, node, parentPackage)
            }

            "object" -> {
                resolveObject(ctx, name, node, parentPackage)
            }

            else -> {
                ResolvedType(Types.OBJECT)
            }
        }

    private fun resolveArray(
        ctx: JsonSchemaContext,
        name: String,
        node: ObjectNode,
        parentPackage: String,
    ): ResolvedType {
        val items = node.get("items")
        if (items != null) {
            val itemName = name.removeSuffix("s").removeSuffix("S").ifEmpty { "${name}Item" }
            val itemResolved = resolveType(ctx, itemName, items, parentPackage)
            val listType = ParameterizedTypeName.get(Types.LIST, itemResolved.typeName)
            return ResolvedType(listType, itemResolved.typeSpec)
        }
        return ResolvedType(ParameterizedTypeName.get(Types.LIST, Types.OBJECT))
    }

    private fun resolveObject(
        ctx: JsonSchemaContext,
        name: String,
        node: ObjectNode,
        parentPackage: String,
    ): ResolvedType {
        val properties = node.get("properties")

        // Object with additionalProperties and no fixed properties → Map<String, T>
        if ((properties == null || !properties.isObject || properties.isEmpty) && node.has("additionalProperties")) {
            val additionalProps = node.get("additionalProperties")
            if (additionalProps.isObject) {
                val valueType = resolveType(ctx, "${name}Value", additionalProps, parentPackage)
                return ResolvedType(
                    ParameterizedTypeName.get(Types.MAP, Types.STRING, valueType.typeName),
                    valueType.typeSpec,
                )
            }
            if (additionalProps.isBoolean && additionalProps.asBoolean()) {
                return ResolvedType(ParameterizedTypeName.get(Types.MAP, Types.STRING, Types.OBJECT))
            }
        }

        // Object with patternProperties → Map
        val patternProps = node.get("patternProperties")
        if (patternProps != null && patternProps.isObject) {
            return resolvePatternProperties(ctx, name, patternProps as ObjectNode, parentPackage)
        }

        if (properties == null || !properties.isObject) {
            // Empty object → Map<String, Object>
            return ResolvedType(ParameterizedTypeName.get(Types.MAP, Types.STRING, Types.OBJECT))
        }

        // Build properties
        val propSpecs = mutableListOf<ObjectGenerator.PropertySpec>()

        // Gather if/then/else properties as well
        val allPropertyNodes = mutableListOf(properties as ObjectNode)
        gatherConditionalProperties(node, allPropertyNodes)

        allPropertyNodes.forEach { propsNode ->
            propsNode.properties().forEach { (propName, propSchema) ->
                // Skip if we already have this property
                if (propSpecs.any { it.jsonName == propName }) return@forEach

                val propTypeName = propName.pascalCase()
                val propResolved = resolveType(ctx, "${name}$propTypeName", propSchema, parentPackage)
                propSpecs.add(
                    ObjectGenerator.PropertySpec(
                        propName,
                        propResolved.typeName,
                        propSchema.get("description")?.asString(),
                    ),
                )
            }
        }

        val description = node.get("description")?.asString()
        val spec = ObjectGenerator.generate(name, propSpecs, description)
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = spec
        return ResolvedType(className, spec)
    }

    /**
     * Gathers properties from if/then/else branches and adds their property nodes to the list.
     */
    private fun gatherConditionalProperties(
        node: ObjectNode,
        propertyNodes: MutableList<ObjectNode>,
    ) {
        listOf("then", "else").forEach { branch ->
            val branchNode = node.get(branch)
            if (branchNode != null && branchNode.isObject) {
                val branchProps = branchNode.get("properties")
                if (branchProps != null && branchProps.isObject) {
                    val branchPropsObj = branchProps as ObjectNode
                    // For each property in the branch, check if it has actual schema content
                    propertyNodes.add(branchPropsObj)
                }
            }
        }
    }

    private fun typeNameToFieldName(typeName: TypeName): String {
        val rawName =
            when (typeName) {
                is ClassName -> typeName.simpleName()
                is ParameterizedTypeName -> typeName.rawType().simpleName()
                else -> typeName.toString().substringAfterLast(".")
            }
        return rawName.toSafeFieldName()
    }

    private fun isSimpleTypeName(typeName: TypeName): Boolean =
        typeName == Types.STRING ||
            typeName == Types.BOOLEAN ||
            typeName == Types.INTEGER ||
            typeName == Types.LONG ||
            typeName == Types.BIG_DECIMAL ||
            typeName == Types.OBJECT

    data class ResolvedType(
        val typeName: TypeName,
        val typeSpec: TypeSpec? = null,
    )
}