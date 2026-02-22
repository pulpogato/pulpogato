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
            val resolvedFromSiblings = resolveRefWithSiblings(ctx, name, obj, parentPackage)
            if (resolvedFromSiblings != null) {
                return resolvedFromSiblings
            }
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
            if (isValidationOnlyOneOf(oneOf as ArrayNode)) {
                val typeNode = obj.get("type")
                if (typeNode != null) {
                    if (typeNode.isArray) {
                        return resolveTypeArrayUnion(ctx, name, typeNode as ArrayNode, obj, parentPackage)
                    }
                    return resolveByType(ctx, name, typeNode.asString(), obj, parentPackage)
                }
                val properties = obj.get("properties")
                if (properties != null && properties.isObject) {
                    return resolveObject(ctx, name, obj, parentPackage)
                }
            }
            return resolveOneOf(ctx, name, oneOf, parentPackage, obj)
        }

        // 4. anyOf — typically validation-only; collapse to widest type
        val anyOf = obj.get("anyOf")
        if (anyOf != null && anyOf.isArray) {
            val typeNode = obj.get("type")
            if (typeNode != null && typeNode.asString() == "array") {
                return resolveByType(ctx, name, "array", obj, parentPackage)
            }
            return resolveAnyOf(ctx, name, anyOf as ArrayNode, parentPackage, obj)
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
                return resolveTypeArrayUnion(ctx, name, typeNode as ArrayNode, obj, parentPackage)
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
            val valueType = resolveType(ctx.withSchemaStack("additionalProperties"), "${name}Value", additionalProps, parentPackage)
            return ResolvedType(
                ParameterizedTypeName.get(Types.MAP, Types.STRING, valueType.typeName),
                valueType.typeSpec,
            )
        }

        // Fallback
        return ResolvedType(Types.OBJECT)
    }

    private fun resolveRefWithSiblings(
        ctx: JsonSchemaContext,
        name: String,
        node: ObjectNode,
        parentPackage: String,
    ): ResolvedType? {
        if (!hasTypeAffectingRefSiblings(node)) {
            return null
        }

        val overlayNode = node.deepCopy()
        overlayNode.remove("\$ref")
        val overlayResolved = resolveType(ctx, name, overlayNode, parentPackage)
        return if (overlayResolved.typeSpec != null || overlayResolved.typeName != Types.OBJECT) {
            overlayResolved
        } else {
            null
        }
    }

    private fun hasTypeAffectingRefSiblings(node: ObjectNode): Boolean {
        val keysThatAffectType =
            setOf(
                "properties",
                "patternProperties",
                "oneOf",
                "anyOf",
                "allOf",
                "type",
                "items",
                "enum",
                "const",
            )
        return node.properties().any { (key, _) -> key in keysThatAffectType }
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
                return ResolvedType(registered, ctx.generatedTypes[registered.toString()])
            }
        }

        // If pre-registered but not yet resolved
        val registered = ctx.definitionRegistry[defName]
        if (registered != null && defName !in ctx.resolvedDefinitions) {
            // Mark as "being resolved" to detect cycles
            ctx.resolvedDefinitions.add(defName)

            val definitions = ctx.rootSchema.get("definitions") ?: return ResolvedType(registered)
            val defNode = definitions.get(defName) ?: return ResolvedType(registered)

            val resolved =
                resolveType(
                    ctx.withSchemaStack("#", "definitions", defName),
                    defName.pascalCase(),
                    defNode,
                    parentPackage,
                )

            if (resolved.typeName != registered) {
                // This definition resolved to a simple type alias, not a generated class
                ctx.typeAliases[defName] = resolved.typeName
                return resolved
            }
            if (resolved.typeSpec != null) {
                ctx.generatedTypes[registered.toString()] = resolved.typeSpec
                return ResolvedType(registered, resolved.typeSpec)
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

        val resolved =
            resolveType(
                ctx.withSchemaStack("#", "definitions", defName),
                defName.pascalCase(),
                defNode,
                parentPackage,
            )

        if (resolved.typeName != className) {
            ctx.typeAliases[defName] = resolved.typeName
            return resolved
        }
        if (resolved.typeSpec != null) {
            ctx.generatedTypes[className.toString()] = resolved.typeSpec
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
        val valueResolved = resolveType(ctx.withSchemaStack("patternProperties", entry.key), valueName, valueSchema, parentPackage)

        val mapType = ParameterizedTypeName.get(Types.MAP, Types.STRING, valueResolved.typeName)
        return ResolvedType(mapType, valueResolved.typeSpec)
    }

    private fun resolveOneOf(
        ctx: JsonSchemaContext,
        name: String,
        oneOf: ArrayNode,
        parentPackage: String,
        parentNode: ObjectNode,
        preserveScalarUnions: Boolean = false,
    ): ResolvedType {
        // Filter out null type elements — they just mean the value is nullable
        val indexedElements =
            oneOf
                .toList()
                .withIndex()
                .filterNot { isMetadataOnlySchemaElement(it.value) }
                .filter {
                    !(it.value.has("type") && it.value.get("type").asString() == "null")
                }
        val elements = indexedElements.map { it.value }

        // If only one element remains after filtering nulls, just resolve it
        if (indexedElements.size == 1) {
            val (originalIndex, element) = indexedElements.first()
            return resolveType(ctx.withSchemaStack("oneOf", originalIndex.toString()), name, element, parentPackage)
        }

        if (indexedElements.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        // If this is a validation-only oneOf (e.g., required-field sets), just resolve the parent as object
        if (isValidationOnlyOneOf(elements)) {
            // This is a validation constraint, not a type union
            return ResolvedType(Types.OBJECT)
        }

        // Build union variants
        val variantCandidates =
            indexedElements.mapIndexed { filteredIndex, indexedElement ->
                val element = indexedElement.value
                val elementCtx = ctx.withSchemaStack("oneOf", indexedElement.index.toString())
                val resolved =
                    if (element.has($$"$ref")) {
                        resolveType(elementCtx, name, element, parentPackage)
                    } else if (element.has("type") && element.get("type").asString() == "object" && element.has("properties")) {
                        val variantName = "${name}Variant$filteredIndex"
                        resolveType(elementCtx, variantName, element, parentPackage)
                    } else if (element.has("type")) {
                        resolveType(elementCtx, name, element, parentPackage)
                    } else {
                        resolveType(elementCtx, "${name}Variant$filteredIndex", element, parentPackage)
                    }
                resolved.typeName to generatedSchemaRef(elementCtx)
            }
        val variants = buildUnionVariants(variantCandidates)

        if (shouldCollapseSimpleOneOf(variants, preserveScalarUnions)) {
            return ResolvedType(Types.OBJECT)
        }

        // If there's only one non-simple variant left, just return it directly
        if (variants.size == 1) {
            return ResolvedType(variants[0].typeName)
        }

        val description = parentNode.get("description")?.asString()
        val unionSpec =
            UnionGenerator.generate(
                name,
                variants,
                description,
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = unionSpec
        return ResolvedType(className, unionSpec)
    }

    private fun shouldCollapseSimpleOneOf(
        variants: List<UnionGenerator.VariantSpec>,
        preserveScalarUnions: Boolean,
    ): Boolean {
        if (preserveScalarUnions) {
            return false
        }
        if (!variants.all { isSimpleTypeName(it.typeName) }) {
            return false
        }

        val uniqueSimpleTypes = variants.map { it.typeName.toString() }.distinct()
        val hasBoolean = variants.any { it.typeName == Types.BOOLEAN }
        val hasString = variants.any { it.typeName == Types.STRING }
        val hasNumeric =
            variants.any { variant ->
                variant.typeName == Types.INTEGER ||
                    variant.typeName == Types.LONG ||
                    variant.typeName == Types.BIG_DECIMAL
            }
        if (uniqueSimpleTypes.size == 2 && hasString && (hasBoolean || hasNumeric)) {
            return false
        }

        return true
    }

    private fun isValidationOnlyOneOf(oneOf: ArrayNode): Boolean = isValidationOnlyOneOf(oneOf.toList())

    private fun isValidationOnlyOneOf(elements: List<JsonNode>): Boolean {
        if (elements.isEmpty()) {
            return false
        }
        return elements.all { element ->
            element.isObject &&
                element.has("required") &&
                element.get("required").isArray &&
                element.size() == 1
        }
    }

    private fun isMetadataOnlySchemaElement(element: JsonNode): Boolean {
        if (!element.isObject) {
            return false
        }
        val informativeKeys =
            setOf(
                "\$ref",
                "type",
                "properties",
                "patternProperties",
                "items",
                "additionalItems",
                "additionalProperties",
                "oneOf",
                "anyOf",
                "allOf",
                "enum",
                "const",
                "required",
                "not",
                "if",
                "then",
                "else",
                "dependencies",
                "minItems",
                "maxItems",
                "minLength",
                "maxLength",
                "pattern",
                "minimum",
                "maximum",
                "exclusiveMinimum",
                "exclusiveMaximum",
                "format",
            )
        return element.properties().none { (key, _) -> key in informativeKeys }
    }

    private fun isScalarOnlyOneOf(oneOf: ArrayNode): Boolean =
        isScalarOnlyOneOf(
            oneOf.toList().filter {
                !(it.has("type") && it.get("type").asString() == "null")
            },
        )

    private fun isScalarOnlyOneOf(elements: List<JsonNode>): Boolean {
        if (elements.isEmpty()) {
            return false
        }
        return elements.all(::isScalarTypeOneOfElement)
    }

    private fun isScalarTypeOneOfElement(element: JsonNode): Boolean {
        if (!element.isObject) {
            return false
        }
        val typeNode = element.get("type")
        if (typeNode == null || !typeNode.isString) {
            return false
        }
        return when (typeNode.asString()) {
            "string",
            "boolean",
            "number",
            "integer",
            -> true

            else -> false
        }
    }

    private fun resolveAnyOf(
        ctx: JsonSchemaContext,
        name: String,
        anyOf: ArrayNode,
        parentPackage: String,
        parentNode: ObjectNode,
    ): ResolvedType {
        val indexedElements =
            anyOf
                .toList()
                .withIndex()
                .filterNot { isMetadataOnlySchemaElement(it.value) }
                .filter {
                    !(it.value.has("type") && it.value.get("type").asString() == "null")
                }
        if (indexedElements.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        if (indexedElements.size == 1) {
            val (originalIndex, element) = indexedElements.first()
            return resolveType(ctx.withSchemaStack("anyOf", originalIndex.toString()), name, element, parentPackage)
        }

        val resolvedElements =
            indexedElements.mapIndexed { filteredIndex, indexedElement ->
                val element = indexedElement.value
                val elementCtx = ctx.withSchemaStack("anyOf", indexedElement.index.toString())
                val resolved =
                    when {
                        element.has("\$ref") -> {
                            resolveType(elementCtx, name, element, parentPackage)
                        }

                        element.has("type") &&
                            element.get("type").asString() == "object" &&
                            element.has("properties") -> {
                            resolveType(elementCtx, "${name}Variant$filteredIndex", element, parentPackage)
                        }

                        element.has("type") -> {
                            resolveType(elementCtx, name, element, parentPackage)
                        }

                        else -> {
                            resolveType(elementCtx, "${name}Variant$filteredIndex", element, parentPackage)
                        }
                    }
                element to resolved.typeName
            }
        val resolvedTypes = resolvedElements.map { it.second }.distinct()
        if (resolvedTypes.size == 1) {
            return ResolvedType(resolvedTypes.first())
        }

        // Keep typing for validation-style anyOf (e.g., string + enum-of-string), but
        // preserve polymorphism for mixed-shape anyOf (e.g., runs-on string|array|object).
        if (resolvedElements.all { (element, type) -> isStringLikeAnyOfElement(ctx, element, type) }) {
            return ResolvedType(Types.STRING)
        }

        val variants =
            buildUnionVariants(
                resolvedElements.mapIndexed { filteredIndex, (_, typeName) ->
                    val originalIndex = indexedElements[filteredIndex].index
                    val elementCtx = ctx.withSchemaStack("anyOf", originalIndex.toString())
                    typeName to generatedSchemaRef(elementCtx)
                },
            )

        if (variants.size == 1) {
            return ResolvedType(variants[0].typeName)
        }

        val description = parentNode.get("description")?.asString()
        val unionSpec =
            UnionGenerator.generate(
                name,
                variants,
                description,
                mode = "ANY_OF",
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = unionSpec
        return ResolvedType(className, unionSpec)
    }

    private fun resolveAllOf(
        ctx: JsonSchemaContext,
        name: String,
        allOf: ArrayNode,
        parentPackage: String,
        parentNode: ObjectNode,
    ): ResolvedType {
        val mergedProps = mutableListOf<ObjectGenerator.PropertySpec>()

        fun mergeProperty(
            propName: String,
            propSchema: JsonNode,
            propCtx: JsonSchemaContext,
        ) {
            val propResolved = resolveType(propCtx, "${name}${propName.pascalCase()}", propSchema, parentPackage)
            val propDescription = propSchema.get("description")?.asString()
            val existingIndex = mergedProps.indexOfFirst { it.jsonName == propName }
            if (existingIndex < 0) {
                mergedProps.add(
                    ObjectGenerator.PropertySpec(
                        propName,
                        propResolved.typeName,
                        propDescription,
                        generatedSchemaRef(propCtx),
                    ),
                )
            } else {
                val existing = mergedProps[existingIndex]
                val mergedType =
                    mergeDuplicatePropertyType(
                        ctx,
                        name,
                        propName,
                        existing.typeName,
                        propResolved.typeName,
                        parentPackage,
                        existing.description ?: propDescription,
                    )
                mergedProps[existingIndex] =
                    ObjectGenerator.PropertySpec(
                        propName,
                        mergedType,
                        existing.description ?: propDescription,
                        existing.schemaRef,
                    )
            }
        }

        val parentProps = parentNode.get("properties")
        if (parentProps != null && parentProps.isObject) {
            (parentProps as ObjectNode).properties().forEach { (propName, propSchema) ->
                mergeProperty(propName, propSchema, ctx.withSchemaStack("properties", propName))
            }
        }
        val parentConditionalProps = mutableListOf<Pair<ObjectNode, List<String>>>()
        gatherConditionalProperties(parentNode, parentConditionalProps)
        parentConditionalProps.forEach { (propsNode, pathPrefix) ->
            propsNode.properties().forEach { (propName, propSchema) ->
                mergeProperty(propName, propSchema, ctx.withSchemaStack(*pathPrefix.toTypedArray(), propName))
            }
        }

        allOf.forEachIndexed { index, element ->
            if (element is ObjectNode) {
                val props = element.get("properties")
                if (props != null && props.isObject) {
                    (props as ObjectNode).properties().forEach { (propName, propSchema) ->
                        mergeProperty(
                            propName,
                            propSchema,
                            ctx.withSchemaStack("allOf", index.toString(), "properties", propName),
                        )
                    }
                }

                listOf("then", "else").forEach { branch ->
                    val branchNode = element.get(branch)
                    if (branchNode != null && branchNode.isObject) {
                        val branchProps = branchNode.get("properties")
                        if (branchProps != null && branchProps.isObject) {
                            (branchProps as ObjectNode).properties().forEach { (propName, propSchema) ->
                                mergeProperty(
                                    propName,
                                    propSchema,
                                    ctx.withSchemaStack("allOf", index.toString(), branch, "properties", propName),
                                )
                            }
                        }
                    }
                }
            }

            if (element.has("\$ref")) {
                // Resolve the ref and keep it if allOf only wraps this single ref.
                val resolved = resolveType(ctx.withSchemaStack("allOf", index.toString()), name, element, parentPackage)
                if (mergedProps.isEmpty() && allOf.size() == 1) {
                    return resolved
                }
            }
        }

        if (mergedProps.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        val description = parentNode.get("description")?.asString()
        val spec =
            ObjectGenerator.generate(
                name,
                mergedProps,
                description,
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
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
        val spec =
            EnumGenerator.generate(
                name,
                values,
                description,
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = spec
        return ResolvedType(className, spec)
    }

    private fun resolveTypeArrayUnion(
        ctx: JsonSchemaContext,
        name: String,
        typeArray: ArrayNode,
        node: ObjectNode,
        parentPackage: String,
    ): ResolvedType {
        val schemaTypes =
            typeArray
                .mapNotNull { typeNode -> if (typeNode.isString) typeNode.asString() else null }
                .filter { schemaType -> schemaType != "null" }
                .distinct()

        if (schemaTypes.isEmpty()) {
            return ResolvedType(Types.OBJECT)
        }

        if (schemaTypes.size == 1) {
            return resolveByType(ctx, name, schemaTypes.first(), node, parentPackage)
        }

        val variantCandidates =
            schemaTypes.mapIndexed { index, schemaType ->
                val variantCtx = ctx.withSchemaStack("type", index.toString())
                val variantName =
                    when (schemaType) {
                        "array",
                        "object",
                        -> "${name}Variant$index"

                        else -> name
                    }
                val resolved = resolveByType(variantCtx, variantName, schemaType, node, parentPackage)
                resolved.typeName to generatedSchemaRef(variantCtx)
            }
        val variants = buildUnionVariants(variantCandidates)

        if (variants.size == 1) {
            return ResolvedType(variants.first().typeName)
        }

        val description = node.get("description")?.asString()
        val unionSpec =
            UnionGenerator.generate(
                name,
                variants,
                description,
                mode = "ONE_OF",
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = unionSpec
        return ResolvedType(className, unionSpec)
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
        val itemSchema = extractArrayItemSchema(node)
        if (itemSchema != null) {
            val itemName = name.removeSuffix("s").removeSuffix("S").ifEmpty { "${name}Item" }
            val itemResolved =
                if (itemSchema is ObjectNode) {
                    val itemOneOf = itemSchema.get("oneOf")
                    if (itemOneOf != null && itemOneOf.isArray && isScalarOnlyOneOf(itemOneOf as ArrayNode)) {
                        resolveOneOf(
                            ctx.withSchemaStack("items"),
                            itemName,
                            itemOneOf,
                            parentPackage,
                            itemSchema,
                            preserveScalarUnions = true,
                        )
                    } else {
                        resolveType(ctx.withSchemaStack("items"), itemName, itemSchema, parentPackage)
                    }
                } else {
                    resolveType(ctx.withSchemaStack("items"), itemName, itemSchema, parentPackage)
                }
            val listType = ParameterizedTypeName.get(Types.LIST, itemResolved.typeName)
            return ResolvedType(listType, itemResolved.typeSpec)
        }
        return ResolvedType(ParameterizedTypeName.get(Types.LIST, Types.OBJECT))
    }

    private fun extractArrayItemSchema(node: ObjectNode): JsonNode? {
        val directItems = node.get("items")
        if (directItems != null) {
            return normalizeItemsNode(directItems, node.get("additionalItems"))
        }

        val anyOf = node.get("anyOf")
        if (anyOf != null && anyOf.isArray) {
            anyOf.forEach { element ->
                if (element is ObjectNode) {
                    val normalized = normalizeItemsNode(element.get("items"), element.get("additionalItems"))
                    if (normalized != null) {
                        return normalized
                    }
                }
            }
        }

        return null
    }

    private fun normalizeItemsNode(
        itemsNode: JsonNode?,
        additionalItemsNode: JsonNode?,
    ): JsonNode? {
        if (itemsNode == null) {
            return if (additionalItemsNode != null && additionalItemsNode.isObject) additionalItemsNode else null
        }
        if (itemsNode.isArray) {
            val tupleItems = itemsNode as ArrayNode
            if (tupleItems.size() > 0) {
                return tupleItems[0]
            }
            return if (additionalItemsNode != null && additionalItemsNode.isObject) additionalItemsNode else null
        }
        return itemsNode
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
                val valueType = resolveType(ctx.withSchemaStack("additionalProperties"), "${name}Value", additionalProps, parentPackage)
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
        val allPropertyNodes = mutableListOf((properties as ObjectNode) to listOf("properties"))
        gatherConditionalProperties(node, allPropertyNodes)

        allPropertyNodes.forEach { (propsNode, pathPrefix) ->
            propsNode.properties().forEach { (propName, propSchema) ->
                val propTypeName = propName.pascalCase()
                val propCtx = ctx.withSchemaStack(*pathPrefix.toTypedArray(), propName)
                val propResolved = resolveType(propCtx, "${name}$propTypeName", propSchema, parentPackage)
                val propDescription = propSchema.get("description")?.asString()
                val existingIndex = propSpecs.indexOfFirst { it.jsonName == propName }
                if (existingIndex < 0) {
                    propSpecs.add(
                        ObjectGenerator.PropertySpec(
                            propName,
                            propResolved.typeName,
                            propDescription,
                            generatedSchemaRef(propCtx),
                        ),
                    )
                } else {
                    val existing = propSpecs[existingIndex]
                    val mergedType =
                        mergeDuplicatePropertyType(
                            ctx,
                            name,
                            propName,
                            existing.typeName,
                            propResolved.typeName,
                            parentPackage,
                            existing.description ?: propDescription,
                        )
                    propSpecs[existingIndex] =
                        ObjectGenerator.PropertySpec(
                            propName,
                            mergedType,
                            existing.description ?: propDescription,
                            existing.schemaRef,
                        )
                }
            }
        }

        val description = node.get("description")?.asString()
        val spec =
            ObjectGenerator.generate(
                name,
                propSpecs,
                description,
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
        val className = ClassName.get(parentPackage, name)
        ctx.generatedTypes[className.toString()] = spec
        return ResolvedType(className, spec)
    }

    /**
     * Gathers properties from if/then/else branches and adds their property nodes to the list.
     */
    private fun gatherConditionalProperties(
        node: ObjectNode,
        propertyNodes: MutableList<Pair<ObjectNode, List<String>>>,
    ) {
        listOf("then", "else").forEach { branch ->
            val branchNode = node.get(branch)
            if (branchNode != null && branchNode.isObject) {
                val branchProps = branchNode.get("properties")
                if (branchProps != null && branchProps.isObject) {
                    val branchPropsObj = branchProps as ObjectNode
                    // For each property in the branch, check if it has actual schema content
                    propertyNodes.add(branchPropsObj to listOf(branch, "properties"))
                }
            }
        }
    }

    private fun mergeDuplicatePropertyType(
        ctx: JsonSchemaContext,
        ownerTypeName: String,
        propertyName: String,
        existingType: TypeName,
        newType: TypeName,
        parentPackage: String,
        description: String?,
    ): TypeName {
        if (existingType.toString() == newType.toString()) {
            return existingType
        }
        if (existingType == Types.OBJECT) {
            return newType
        }
        if (newType == Types.OBJECT) {
            return existingType
        }
        if (
            existingType is ParameterizedTypeName &&
            newType is ParameterizedTypeName &&
            existingType.rawType() == newType.rawType()
        ) {
            return choosePreferredParameterizedType(ctx, existingType, newType)
        }

        val unionSimpleName = "${ownerTypeName}${propertyName.pascalCase()}"
        val unionClass = ClassName.get(parentPackage, unionSimpleName)
        val existingUnionSpec = ctx.generatedTypes[unionClass.toString()]

        val candidateTypes = mutableListOf<TypeName>()
        if (existingType == unionClass && existingUnionSpec != null) {
            existingUnionSpec.fieldSpecs().forEach { fieldSpec -> candidateTypes.add(fieldSpec.type()) }
        } else {
            candidateTypes.add(existingType)
        }
        candidateTypes.add(newType)

        val variants = buildUnionVariants(candidateTypes, generatedSchemaRef(ctx))
        if (variants.size == 1) {
            return variants.first().typeName
        }

        val unionSpec =
            UnionGenerator.generate(
                unionSimpleName,
                variants,
                description,
                mode = "ONE_OF",
                schemaRef = generatedSchemaRef(ctx),
                sourceFile = generatedSourceFile(ctx),
            )
        ctx.generatedTypes[unionClass.toString()] = unionSpec
        return unionClass
    }

    private fun choosePreferredParameterizedType(
        ctx: JsonSchemaContext,
        existingType: ParameterizedTypeName,
        newType: ParameterizedTypeName,
    ): TypeName {
        if (existingType.rawType() != Types.MAP || newType.rawType() != Types.MAP) {
            return existingType
        }
        if (existingType.typeArguments().size < 2 || newType.typeArguments().size < 2) {
            return existingType
        }

        val existingValueType = existingType.typeArguments()[1]
        val newValueType = newType.typeArguments()[1]
        if (existingValueType !is ClassName || newValueType !is ClassName) {
            return existingType
        }

        val existingSpec = ctx.generatedTypes[existingValueType.toString()]
        val newSpec = ctx.generatedTypes[newValueType.toString()]
        if (existingSpec == null || newSpec == null) {
            return existingType
        }

        return if (newSpec.fieldSpecs().size > existingSpec.fieldSpecs().size) newType else existingType
    }

    private fun buildUnionVariants(
        typeNames: List<TypeName>,
        schemaRef: String,
    ): List<UnionGenerator.VariantSpec> = buildUnionVariants(typeNames.map { it to schemaRef })

    private fun buildUnionVariants(candidates: List<Pair<TypeName, String>>): List<UnionGenerator.VariantSpec> {
        val variants = mutableListOf<UnionGenerator.VariantSpec>()
        val seenTypeNames = mutableSetOf<String>()
        val usedFieldNameCounts = mutableMapOf<String, Int>()

        candidates.forEach { (typeName, schemaRef) ->
            val typeKey = typeName.toString()
            if (!seenTypeNames.add(typeKey)) {
                return@forEach
            }

            val baseFieldName = typeNameToFieldName(typeName)
            val nextCount = usedFieldNameCounts.getOrDefault(baseFieldName, 0) + 1
            usedFieldNameCounts[baseFieldName] = nextCount
            val fieldName =
                if (nextCount == 1) {
                    baseFieldName
                } else {
                    "${baseFieldName}Variant$nextCount"
                }
            variants.add(UnionGenerator.VariantSpec(fieldName, typeName, schemaRef))
        }

        return variants
    }

    private fun generatedSchemaRef(ctx: JsonSchemaContext): String = ctx.getSchemaStackRef()

    private fun generatedSourceFile(ctx: JsonSchemaContext): String = ctx.sourceFile

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

    private fun isStringLikeAnyOfElement(
        ctx: JsonSchemaContext,
        element: JsonNode,
        typeName: TypeName,
    ): Boolean {
        if (typeName == Types.STRING) {
            return true
        }

        if (!element.isObject) {
            return false
        }

        val obj = element as ObjectNode
        val typeNode = obj.get("type")
        if (typeNode != null && typeNode.isString && typeNode.asString() == "string") {
            return true
        }
        if (obj.has("enum")) {
            return typeNode == null || (typeNode.isString && typeNode.asString() == "string")
        }

        val ref = obj.get("\$ref")
        if (ref != null) {
            val defName = ref.asString().substringAfterLast("/")
            val definitions = ctx.rootSchema.get("definitions")
            if (definitions != null && definitions.isObject) {
                val refNode = definitions.get(defName)
                if (refNode != null) {
                    return isStringLikeAnyOfElement(ctx, refNode, typeName)
                }
            }
        }

        return false
    }

    data class ResolvedType(
        val typeName: TypeName,
        val typeSpec: TypeSpec? = null,
    )
}