package io.github.pulpogato.restcodegen.ext

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import com.palantir.javapoet.TypeVariableName
import com.palantir.javapoet.WildcardTypeName
import io.github.pulpogato.restcodegen.Annotations
import io.github.pulpogato.restcodegen.Annotations.deserializerAnnotationForJackson2
import io.github.pulpogato.restcodegen.Annotations.deserializerAnnotationForJackson3
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.jsonIncludeAlways
import io.github.pulpogato.restcodegen.Annotations.jsonIncludeNonEmpty
import io.github.pulpogato.restcodegen.Annotations.jsonIncludeNonNull
import io.github.pulpogato.restcodegen.Annotations.jsonProperty
import io.github.pulpogato.restcodegen.Annotations.jsonValue
import io.github.pulpogato.restcodegen.Annotations.nonNull
import io.github.pulpogato.restcodegen.Annotations.nullableOptionalDeserializer
import io.github.pulpogato.restcodegen.Annotations.nullableOptionalSerializer
import io.github.pulpogato.restcodegen.Annotations.serializerAnnotationForJackson2
import io.github.pulpogato.restcodegen.Annotations.serializerAnnotationForJackson3
import io.github.pulpogato.restcodegen.Annotations.singleValueAsArray
import io.github.pulpogato.restcodegen.Annotations.typeGenerated
import io.github.pulpogato.restcodegen.Context
import io.github.pulpogato.restcodegen.MarkdownHelper
import io.github.pulpogato.restcodegen.Types
import io.swagger.v3.oas.models.media.Schema
import javax.lang.model.element.Modifier

private const val PACKAGE_PULPOGATO_COMMON = "io.github.pulpogato.common"

fun Map.Entry<String, Schema<*>>.className() = key.pascalCase()

/**
 * Checks if the given TypeName represents a simple type (primitive, string, or date/time)
 * that should NOT be wrapped in NullableOptional.
 */
private fun isSimpleType(typeName: TypeName): Boolean {
    // For annotated types, we need to check the base type
    // The toString() of an annotated type includes annotations like "java.lang. @Annotation String"
    // We need to remove both the annotation and any extra spacing/dots
    val typeString =
        typeName
            .toString()
            .replace(Regex("""\s+@\w+(\.\w+)*\([^)]*\)"""), "") // Remove @package.Annotation(args)
            .replace(Regex("""\s+@\w+(\.\w+)*"""), "") // Remove @package.Annotation
            .replace(Regex("""\.\s+"""), ".") // Fix "java.lang. String" -> "java.lang.String"
            .trim()

    return when {
        typeString == "java.lang.String" -> true
        typeString == "java.lang.Boolean" || typeString == "boolean" -> true
        typeString == "java.lang.Integer" || typeString == "int" -> true
        typeString == "java.lang.Long" || typeString == "long" -> true
        typeString == "java.lang.Double" || typeString == "double" -> true
        typeString == "java.lang.Float" || typeString == "float" -> true
        typeString == "java.math.BigDecimal" -> true
        typeString.startsWith("java.time.") -> true
        typeString.startsWith("java.net.URI") -> true
        else -> false
    }
}

fun isSingleOrArray(
    oneOf: List<Schema<Any>>,
    type: String,
) = isSingleOrArrayOfSameType(oneOf) && oneOf.first().types == setOf(type)

fun isSingleOrArrayOfSameType(oneOf: List<Schema<Any>>) =
    oneOf.size == 2 &&
        oneOf.last().types == setOf("array") &&
        oneOf.last().items.types == oneOf.first().types

fun typesAre(
    oneOf: List<Schema<Any>>,
    vararg types: String,
) = types.toSet() == oneOf.flatMap { it.types ?: listOf() }.toSet()

fun isAnyOfOnlyForValidation(
    anyOf: List<Schema<Any>>,
    parentSchema: Schema<*>,
): Boolean {
    if (parentSchema.properties == null || parentSchema.properties.isEmpty()) {
        return false
    }

    return anyOf.all { subSchema ->
        (subSchema.properties == null || subSchema.properties.isEmpty()) &&
            subSchema.`$ref` == null &&
            (subSchema.types == null || subSchema.types.isEmpty()) &&
            (subSchema.required != null || subSchema.additionalProperties == null)
    }
}

fun referenceAndDefinition(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    prefix: String,
    parentClass: ClassName?,
): Pair<TypeName, TypeSpec?>? {
    val types =
        entry.value.types
            ?.filterNotNull()
            ?.filter { it != "null" }
    val anyOf =
        entry.value.anyOf
            ?.filterNotNull()
            ?.filter { it.types != setOf("null") }
    val oneOf = entry.value.oneOf?.filterNotNull()
    val allOf = entry.value.allOf?.filterNotNull()

    return when {
        entry.key == "empty-object" -> Pair(Types.EMPTY_OBJECT, null)
        entry.value.`$ref` != null -> buildReferenceAndDefinitionFromRef(context, entry)

        anyOf != null && anyOf.size == 1 -> {
            val anyOfValue = anyOf.first()
            referenceAndDefinition(context, mapOf(entry.key to anyOfValue).entries.first(), "", null)!!
        }

        oneOf != null &&
            isSingleOrArray(
                oneOf,
                "string",
            ) -> Pair(ParameterizedTypeName.get(Types.SINGULAR_OR_PLURAL, Types.STRING).annotated(typeGenerated(), singleValueAsArray()), null)

        oneOf != null &&
            typesAre(
                oneOf,
                "string",
                "integer",
            ) && oneOf.any { it.format == "date-time" } -> Pair(Types.OFFSET_DATE_TIME.annotated(typeGenerated()), null)

        oneOf != null && typesAre(oneOf, "string", "integer") -> Pair(Types.STRING_OR_INTEGER.annotated(typeGenerated()), null)
        anyOf != null && typesAre(anyOf, "string", "integer") -> Pair(Types.STRING_OR_INTEGER.annotated(typeGenerated()), null)
        anyOf != null && isAnyOfOnlyForValidation(anyOf, entry.value) ->
            buildType("${prefix}${entry.className()}", parentClass) { buildSimpleObject(context, entry, it) }
        anyOf != null ->
            buildType("${prefix}${entry.className()}", parentClass) {
                buildFancyObject(context, entry, anyOf, "anyOf", it)
            }

        oneOf != null ->
            buildType("${prefix}${entry.className()}", parentClass) {
                buildFancyObject(context, entry, oneOf, "oneOf", it)
            }

        allOf != null ->
            buildType("${prefix}${entry.className()}", parentClass) {
                buildFancyObject(context, entry, allOf, "allOf", it)
            }

        types == null && entry.value.properties != null ->
            referenceAndDefinition(context, mapOf(entry.key to entry.value.also { it.types = mutableSetOf("object") }).entries.first(), "", parentClass)!!

        types == null && entry.value.properties != null && entry.value.properties.isEmpty() && entry.value.additionalProperties == false ->
            Pair(Types.VOID.annotated(typeGenerated()), null)

        types == null && entry.value.properties != null && entry.value.properties.isNotEmpty() ->
            buildType("${prefix}${entry.className()}", parentClass) { buildSimpleObject(context, entry, it) }

        types == null -> Pair(Types.OBJECT.annotated(typeGenerated()), null)

        types.isEmpty() -> Pair(Types.OBJECT.annotated(typeGenerated()), null)

        types.size == 1 ->
            when (types.first()) {
                "string" -> buildReferenceAndDefinitionFromString(context, entry, prefix, parentClass)
                "integer" -> buildReferenceAndDefinitionFromInteger(entry)
                "boolean" -> Pair(Types.BOOLEAN, null)
                "number" -> buildReferenceAndDefinitionFromNumber(entry)
                "array" -> buildReferenceAndDefinitionFromArray(context, entry, parentClass)
                "object" -> buildReferenceAndDefinitionFromObject(context, entry, parentClass, prefix)
                else -> throw RuntimeException("Unknown type for ${entry.key}, stack: ${context.getSchemaStackRef()}")
            }

        types.toSet() == setOf("string", "integer") -> Pair(Types.STRING_OR_INTEGER, null)
        else -> Pair(Types.TODO, null)
    }
}

private fun buildReferenceAndDefinitionFromObject(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    parentClass: ClassName?,
    prefix: String,
): Pair<TypeName, TypeSpec?> =
    when {
        entry.value.additionalProperties != null && (entry.value.properties == null || entry.value.properties.isEmpty()) -> {
            val additionalProperties = entry.value.additionalProperties
            if (additionalProperties is Schema<*>) {
                referenceAndDefinition(
                    context.withSchemaStack("additionalProperties"),
                    mapOf(entry.key to additionalProperties).entries.first(),
                    "",
                    parentClass,
                )!!
                    .let { Pair(ParameterizedTypeName.get(Types.MAP, Types.STRING, it.first), it.second) }
            } else {
                val message = additionalProperties.javaClass
                println(message)
                Pair(Types.TODO, null)
            }
        }

        entry.value.properties != null && entry.value.properties.isNotEmpty() ->
            buildType("${prefix}${entry.className()}", parentClass) {
                buildSimpleObject(context, entry, it)
            }

        else -> Pair(Types.MAP_STRING_OBJECT.annotated(typeGenerated()), null)
    }

private fun buildReferenceAndDefinitionFromArray(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    parentClass: ClassName?,
): Pair<TypeName, TypeSpec?>? {
    val context1 = context.withSchemaStack("items")
    return referenceAndDefinition(context1, mapOf(entry.key to entry.value.items).entries.first(), "", parentClass)
        ?.let {
            val oldTypeGenerated =
                it.first
                    .annotations()
                    .filter { spec -> (spec.type() as ClassName).simpleName() == "TypeGenerated" }
            val otherAnnotations =
                it.first
                    .annotations()
                    .filter { spec -> (spec.type() as ClassName).simpleName() != "TypeGenerated" }

            Pair(
                ParameterizedTypeName
                    .get(
                        Types.LIST,
                        it.first
                            .withoutAnnotations()
                            .annotated(oldTypeGenerated),
                    ).annotated(otherAnnotations.distinct())
                    .annotated(typeGenerated()),
                it.second,
            )
        }
}

private fun buildReferenceAndDefinitionFromNumber(entry: Map.Entry<String, Schema<*>>): Pair<TypeName, TypeSpec?> =
    when (entry.value.format) {
        "double" -> Pair(Types.DOUBLE.annotated(typeGenerated()), null)
        "float" -> Pair(Types.FLOAT.annotated(typeGenerated()), null)
        else -> Pair(Types.BIG_DECIMAL.annotated(typeGenerated()), null)
    }

private fun buildReferenceAndDefinitionFromInteger(entry: Map.Entry<String, Schema<*>>): Pair<TypeName, TypeSpec?> =
    when (entry.value.format) {
        "int32" -> Pair(Types.INTEGER.annotated(typeGenerated()), null)
        "timestamp" -> Pair(Types.EPOCH_TIME.annotated(typeGenerated()), null)
        else -> Pair(Types.LONG.annotated(typeGenerated()), null)
    }

private fun buildReferenceAndDefinitionFromString(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    prefix: String,
    parentClass: ClassName?,
): Pair<TypeName, TypeSpec?> =
    when {
        entry.value.enum != null -> buildType("${prefix}${entry.className()}", parentClass) { buildEnum(context, entry, it) }

        entry.value.format == null -> Pair(Types.STRING, null)
        else ->
            when (entry.value.format) {
                "uri" -> Pair(Types.URI.annotated(typeGenerated()), null)
                "uuid" -> Pair(Types.UUID.annotated(typeGenerated()), null)
                "date" -> Pair(Types.LOCAL_DATE.annotated(typeGenerated()), null)
                "date-time" -> Pair(Types.OFFSET_DATE_TIME.annotated(typeGenerated()), null)
                "binary" -> Pair(Types.BYTE_ARRAY.annotated(typeGenerated()), null)
                "email", "hostname", "ip/cidr", "uri-template", "repo.nwo", "ssh-key", "ssh-key fingerprint" ->
                    Pair(
                        Types.STRING.annotated(typeGenerated()),
                        null,
                    )

                else -> throw RuntimeException("Unknown string type for ${entry.key}, stack: ${context.getSchemaStackRef()}")
            }
    }

private fun buildReferenceAndDefinitionFromRef(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
): Pair<TypeName, TypeSpec?> {
    val schemaName = entry.value.`$ref`.replace("#/components/schemas/", "")
    val entries =
        context.openAPI.components.schemas
            .filter { (k, _) -> k == schemaName }
            .entries
    if (entries.isEmpty()) {
        throw RuntimeException("Could not find schema for ref \"${entry.value.`$ref`}\", stack: \"${context.getSchemaStackRef()}\"")
    }
    val schema = entries.first()
    return referenceAndDefinition(context.withSchemaStack("#", "components", "schemas", schemaName), schema, "", null)!!.copy(second = null)
}

private fun buildType(
    className: String,
    parentClass: ClassName?,
    typeSpecProvider: (refName: ClassName) -> TypeSpec,
): Pair<ClassName, TypeSpec> {
    val refName =
        when {
            parentClass == null -> ClassName.get("io.github.pulpogato.rest.schemas", className)
            parentClass.simpleName() == className -> parentClass.nestedClass("${className}Inner")
            parentClass.simpleName() == "Updated" && className == "Changes" -> parentClass.nestedClass("Changes2")
            else -> parentClass.nestedClass(className)
        }

    val definition = typeSpecProvider(refName)
    return Pair(refName, definition)
}

/**
 * Extracted common logic for adding standard methods, getters/setters, and builder pattern to a TypeSpec.Builder
 */
private fun addStandardMethodsAndBuilderLogic(
    builder: TypeSpec.Builder,
    fieldSpecs: List<FieldSpec>,
    classRef: ClassName,
) {
    // Generate all methods
    fieldSpecs.forEach { field ->
        val javadoc = extractJavadoc(field)
        builder
            .addMethod(generateGetter(field, javadoc))
            .addMethod(generateSetter(field, javadoc))
    }

    builder
        .addMethod(generateEquals())
        .addMethod(generateHashCode())
        .addMethod(generateToString())
        .addMethod(generateNoArgsConstructor())

    if (fieldSpecs.isNotEmpty()) {
        builder.addMethod(generateAllArgsConstructor(classRef, fieldSpecs))
    }

    // Add builder pattern
    builder
        .addType(generateBuilderClass(classRef, fieldSpecs))
        .addType(generateBuilderImplClass(classRef))
        .addMethod(generateBuilderFactoryMethod(classRef))
        .addMethod(generateToBuilderMethod(classRef))
}

private fun buildFancyObject(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    subSchemas: List<Schema<Any>>,
    fancyObjectType: String,
    classRef: ClassName,
): TypeSpec {
    val className = classRef.simpleName()
    val theType =
        TypeSpec
            .classBuilder(className)
            .addAnnotation(generated(0, context.withSchemaStack(fancyObjectType)))
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ClassName.get(PACKAGE_PULPOGATO_COMMON, "PulpogatoType"))

    schemaJavadoc(entry).let {
        if (it.isNotBlank()) {
            theType.addJavadoc($$"$L", it)
        }
    }

    val fields = ArrayList<Pair<TypeName, String>>()

    subSchemas
        .mapIndexed { index, it ->
            var newKey = entry.key + index
            if (it.`$ref` != null) {
                newKey = it.`$ref`.replace("#/components/schemas/", "")
            }
            newKey to it
        }.forEachIndexed { index, (newKey, subSchema) ->
            processSubSchema(context.withSchemaStack(fancyObjectType, "$index"), entry, newKey, subSchema, classRef, theType, fields)
        }

    // Get built type to access actual field specs for method generation
    val builtType = theType.build()
    val fieldSpecs = builtType.fieldSpecs()

    addStandardMethodsAndBuilderLogic(theType, fieldSpecs, classRef)

    // Add toCode method (existing logic)
    addToCodeMethod(builtType, theType, classRef)

    // Add custom serializers/deserializers (KEEP THIS)
    val deserializer3 = buildDeserializer(className, fancyObjectType, getSettableFields(fields, className, 3), 3)
    val serializer3 = buildSerializer(className, fancyObjectType, getGettableFields(fields, className, 3), 3)
    val deserializer2 = buildDeserializer(className, fancyObjectType, getSettableFields(fields, className, 2), 2)
    val serializer2 = buildSerializer(className, fancyObjectType, getGettableFields(fields, className, 2), 2)

    theType
        .addType(deserializer3)
        .addType(serializer3)
        .addAnnotation(deserializerAnnotationForJackson3(className, deserializer3))
        .addAnnotation(serializerAnnotationForJackson3(className, serializer3))
        .addType(deserializer2)
        .addType(serializer2)
        .addAnnotation(deserializerAnnotationForJackson2(className, deserializer2))
        .addAnnotation(serializerAnnotationForJackson2(className, serializer2))

    return theType.build()
}

private fun processSubSchema(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    newKey: String,
    subSchema: Schema<Any>,
    classRef: ClassName,
    theType: TypeSpec.Builder,
    fields: ArrayList<Pair<TypeName, String>>,
) {
    val keyValuePair = mapOf(newKey to subSchema).entries.first()
    val rad = referenceAndDefinition(context, keyValuePair, "", classRef) ?: return

    processNestedType(context, entry, keyValuePair, rad, theType)
    addFieldToType(context, keyValuePair, classRef, theType, fields)
}

private fun processNestedType(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    keyValuePair: Map.Entry<String, Schema<Any>>,
    rad: Pair<TypeName, TypeSpec?>,
    theType: TypeSpec.Builder,
) {
    rad.second?.let { original ->
        val builder = copyTypeSpecToBuilder(original)

        if (rad.first is ClassName) {
            val parentStack = context.schemaStack.dropLast(2).toTypedArray()
            addPropertiesFromSchemas(context, entry, keyValuePair, rad.first as ClassName, builder, parentStack)
            val builtWithAllProperties = builder.addModifiers(Modifier.STATIC).build()
            handleToCodeMethods(original, builtWithAllProperties, rad.first as ClassName, theType)
        } else {
            theType.addType(builder.addModifiers(Modifier.STATIC).build())
        }
    }
}

private fun copyTypeSpecToBuilder(
    original: TypeSpec,
    methodFilter: (MethodSpec) -> Boolean = { true },
): TypeSpec.Builder = copyTypeSpecToBuilderWithFilteredTypes(original, { true }, methodFilter)

/**
 * Determines if a method should be kept (not filtered out) - returns true to keep the method
 */
private fun shouldKeepMethod(method: MethodSpec): Boolean =
    when {
        method.isConstructor -> false
        method.name() in setOf("toString", "toCode", "equals", "hashCode", "toBuilder", "builder") -> false
        method.name().startsWith("get") || method.name().startsWith("set") -> false
        else -> true
    }

/**
 * Creates a TypeSpec.Builder from an existing TypeSpec, with option to filter typeSpecs (nested classes)
 */
private fun copyTypeSpecToBuilderWithFilteredTypes(
    original: TypeSpec,
    typeSpecFilter: (TypeSpec) -> Boolean = { true },
    methodFilter: (MethodSpec) -> Boolean = { true },
): TypeSpec.Builder {
    val builder = TypeSpec.classBuilder(original.name())
    original.annotations().forEach { builder.addAnnotation(it) }
    original.modifiers().forEach { builder.addModifiers(it) }
    original.superinterfaces().forEach { builder.addSuperinterface(it) }
    original.fieldSpecs().forEach { builder.addField(it) }
    original.typeSpecs().filter(typeSpecFilter).forEach { builder.addType(it) }
    original.methodSpecs().filter(methodFilter).forEach { builder.addMethod(it) }
    if (!original.javadoc().isEmpty) {
        builder.addJavadoc(original.javadoc())
    }
    return builder
}

private fun addPropertiesFromSchemas(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    keyValuePair: Map.Entry<String, Schema<Any>>,
    className: ClassName,
    builder: TypeSpec.Builder,
    parentStack: Array<String>,
) {
    if (entry.value.properties != null && entry.value.properties.isNotEmpty()) {
        addProperties(context.withSchemaStack(*parentStack), entry, className, builder)
    }
    addProperties(context.withSchemaStack(*parentStack), keyValuePair, className, builder)
}

private fun handleToCodeMethods(
    original: TypeSpec,
    builtWithAllProperties: TypeSpec,
    className: ClassName,
    theType: TypeSpec.Builder,
) {
    val hasToCodeMethod = original.methodSpecs().any { it.name() == "toCode" }
    val hasNewFields = builtWithAllProperties.fieldSpecs().size > original.fieldSpecs().size

    when {
        hasToCodeMethod && hasNewFields -> rebuildWithUpdatedMethods(builtWithAllProperties, className, theType)
        !hasToCodeMethod -> addMethodsToNewType(builtWithAllProperties, className, theType)
        else -> {
            // Even if toCode doesn't need updating, the Builder might need regenerating if fields were added
            theType.addType(builtWithAllProperties)
        }
    }
}

private fun rebuildWithUpdatedMethods(
    builtWithAllProperties: TypeSpec,
    className: ClassName,
    theType: TypeSpec.Builder,
) {
    // Remove old generated methods and Builder to regenerate them with all fields
    val builderWithoutGeneratedMethods =
        copyTypeSpecToBuilder(builtWithAllProperties) { method ->
            // Keep only methods that are NOT auto-generated (like enum methods, custom logic)
            // Exclude: constructors, getters, setters, equals, hashCode, toString, toCode, builder methods, canEqual
            shouldKeepMethod(method)
        }

    // Use helper function to copy TypeSpec with filtered types
    val builderWithoutOldBuilder =
        copyTypeSpecToBuilderWithFilteredTypes(
            builtWithAllProperties,
            typeSpecFilter = { !it.name().endsWith("Builder") && !it.name().endsWith("BuilderImpl") },
            methodFilter = { method ->
                // Keep only methods that are NOT auto-generated (like enum methods, custom logic)
                // Exclude: constructors, getters, setters, equals, hashCode, toString, toCode, builder methods, canEqual
                shouldKeepMethod(method)
            },
        ).apply {
            // Add the custom methods from the temporary builder
            builderWithoutGeneratedMethods.build().methodSpecs().forEach { addMethod(it) }
        }

    // Now regenerate all methods with the complete field list
    val allFields = builtWithAllProperties.fieldSpecs()

    addStandardMethodsAndBuilderLogic(builderWithoutOldBuilder, allFields, className)

    // Add toCode method
    val rebuilt = builderWithoutOldBuilder.build()
    addToCodeMethod(rebuilt, builderWithoutOldBuilder, className)

    theType.addType(builderWithoutOldBuilder.build())
}

private fun addMethodsToNewType(
    builtWithAllProperties: TypeSpec,
    className: ClassName,
    theType: TypeSpec.Builder,
) {
    val builder = builtWithAllProperties.toBuilder()
    addToCodeMethod(builtWithAllProperties, builder, className)
    theType.addType(builder.build())
}

private fun addFieldToType(
    context: Context,
    keyValuePair: Map.Entry<String, Schema<Any>>,
    classRef: ClassName,
    theType: TypeSpec.Builder,
    fields: ArrayList<Pair<TypeName, String>>,
) {
    val fieldSpec = buildFieldSpec(context, keyValuePair, classRef)
    theType.addField(fieldSpec)
    val first =
        when {
            fieldSpec.type() is ParameterizedTypeName -> (fieldSpec.type() as ParameterizedTypeName).rawType()
            else -> fieldSpec.type()
        }
    fields.add(Pair(first, fieldSpec.name().pascalCase()))
}

private fun buildFieldSpec(
    context: Context,
    keyValuePair: Map.Entry<String, Schema<Any>>,
    classRef: ClassName,
): FieldSpec =
    FieldSpec
        .builder(
            referenceAndDefinition(context, keyValuePair, "", classRef)!!.first,
            keyValuePair.key.unkeywordize().camelCase(),
            Modifier.PRIVATE,
        ).addJavadoc($$"$L", schemaJavadoc(keyValuePair).split("\n").dropLastWhile { it.isEmpty() }.joinToString("\n"))
        .addAnnotation(generated(0, context))
        .addAnnotation(jsonProperty(keyValuePair.key))
        .build()

private fun getGettableFields(
    fields: ArrayList<Pair<TypeName, String>>,
    className: String,
    jacksonVersion: Int,
): List<CodeBlock> =
    fields.map { (type, name) ->
        CodeBlock.of(
            $$"new $T<>($T.class, $T::get$$name)",
            ClassName.get("$PACKAGE_PULPOGATO_COMMON.jackson", "Jackson${jacksonVersion}FancySerializer", "GettableField"),
            type.withoutAnnotations(),
            ClassName.get("", className),
        )
    }

private fun getSettableFields(
    fields: ArrayList<Pair<TypeName, String>>,
    className: String?,
    jacksonVersion: Int,
): List<CodeBlock> =
    fields.map { (type, name) ->
        CodeBlock.of(
            $$"new $T<>($T.class, $T::set$$name)",
            ClassName.get("$PACKAGE_PULPOGATO_COMMON.jackson", "Jackson${jacksonVersion}FancyDeserializer", "SettableField"),
            type.withoutAnnotations(),
            ClassName.get("", className),
        )
    }

private fun buildSerializer(
    className: String,
    fancyObjectType: String,
    gettableFields: List<CodeBlock>,
    jacksonVersion: Int,
): TypeSpec =
    TypeSpec
        .classBuilder("${className}Jackson${jacksonVersion}Serializer")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .superclass(
            ParameterizedTypeName.get(
                ClassName.get("$PACKAGE_PULPOGATO_COMMON.jackson", "Jackson${jacksonVersion}FancySerializer"),
                ClassName.get("", className),
            ),
        ).addMethod(
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(
                    $$"""super($T.class, $T.$${fancyObjectType.trainCase()}, $T.of(
                        |    $L
                        |))
                    """.trimMargin(),
                    ClassName.get("", className),
                    ClassName.get(PACKAGE_PULPOGATO_COMMON, "Mode"),
                    Types.LIST,
                    CodeBlock.join(gettableFields, ",\n    "),
                ).build(),
        ).build()

private fun buildDeserializer(
    className: String,
    fancyObjectType: String,
    settableFields: List<CodeBlock>,
    jacksonVersion: Int,
): TypeSpec =
    TypeSpec
        .classBuilder("${className}Jackson${jacksonVersion}Deserializer")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .superclass(
            ParameterizedTypeName.get(
                ClassName.get("$PACKAGE_PULPOGATO_COMMON.jackson", "Jackson${jacksonVersion}FancyDeserializer"),
                ClassName.get("", className),
            ),
        ).addMethod(
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(
                    $$"""super($T.class, $T::new, $T.$${fancyObjectType.trainCase()}, $T.of(
                        |    $L
                        |))
                    """.trimMargin(),
                    ClassName.get("", className),
                    ClassName.get("", className),
                    ClassName.get(PACKAGE_PULPOGATO_COMMON, "Mode"),
                    Types.LIST,
                    CodeBlock.join(settableFields, ",\n    "),
                ).build(),
        ).build()

private fun buildSimpleObject(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    nameRef: ClassName,
): TypeSpec {
    val name = nameRef.simpleName()

    val builder =
        TypeSpec
            .classBuilder(name)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated(0, context))
            .addAnnotation(jsonIncludeNonNull())
            .addSuperinterface(ClassName.get(PACKAGE_PULPOGATO_COMMON, "PulpogatoType"))

    schemaJavadoc(entry).let {
        if (it.isNotBlank()) {
            builder.addJavadoc($$"$L", it)
        }
    }

    addProperties(context, entry, nameRef, builder)

    // Get built class to access fields for method generation
    val builtClass = builder.build()
    val fields = builtClass.fieldSpecs()

    addStandardMethodsAndBuilderLogic(builder, fields, nameRef)

    // Add toCode method (existing logic)
    addToCodeMethod(builtClass, builder, nameRef)

    return builder.build()
}

private fun addToCodeMethod(
    builtClass: TypeSpec,
    builder: TypeSpec.Builder,
    nameRef: ClassName,
) {
    if (builtClass.fieldSpecs().isNotEmpty()) {
        val toCodeStatement =
            CodeBlock
                .builder()
                .add($$"return new $T($S)", Types.CODE_BUILDER, nameRef.canonicalName())

        builtClass.fieldSpecs().forEach { field ->
            toCodeStatement.add($$"\n    .addProperty($S, $N)", field.name(), field.name())
        }

        toCodeStatement.add("\n    .build()")

        builder
            .addMethod(
                MethodSpec
                    .methodBuilder("toCode")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String::class.java)
                    .addStatement(toCodeStatement.build())
                    .build(),
            )
    }
}

/**
 * Extracts Javadoc text from a FieldSpec.
 * Returns empty string if field has no Javadoc.
 */
private fun extractJavadoc(field: FieldSpec): String {
    val javadoc = field.javadoc()
    return if (!javadoc.isEmpty) {
        javadoc.toString()
    } else {
        ""
    }
}

/**
 * Generates a getter method for a field.
 * Uses getFieldName() for all field types including booleans.
 */
private fun generateGetter(
    field: FieldSpec,
    javadoc: String = "",
): MethodSpec {
    val fieldName = field.name()
    val methodName = "get${fieldName.pascalCase()}"

    val builder =
        MethodSpec
            .methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(field.type())
            .addStatement($$"return this.$N", fieldName)

    // Add Javadoc if present
    if (javadoc.isNotBlank()) {
        builder.addJavadoc(javadoc)
    }

    return builder.build()
}

/**
 * Generates a setter method for a field.
 */
private fun generateSetter(
    field: FieldSpec,
    javadoc: String = "",
): MethodSpec {
    val fieldName = field.name()
    val methodName = "set${fieldName.pascalCase()}"
    val fieldType = field.type()

    // Check if this is a NullableOptional field by checking the string representation
    val typeString = fieldType.toString()
    val isNullableOptional = typeString.startsWith("io.github.pulpogato.common.NullableOptional<")

    val builder =
        MethodSpec
            .methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(
                ParameterSpec
                    .builder(fieldType, fieldName)
                    .apply {
                        if (isNullableOptional) {
                            addAnnotation(nonNull())
                        }
                    }.build(),
            ).addStatement($$"this.$N = $N", fieldName, fieldName)

    // Add Javadoc if present
    if (javadoc.isNotBlank()) {
        builder.addJavadoc(javadoc)
    }

    return builder.build()
}

/**
 * Generates no-argument constructor.
 */
private fun generateNoArgsConstructor(): MethodSpec =
    MethodSpec
        .constructorBuilder()
        .addModifiers(Modifier.PUBLIC)
        .build()

/**
 * Generates constructor with all fields as parameters.
 */
private fun generateAllArgsConstructor(
    className: ClassName,
    fields: List<FieldSpec>,
): MethodSpec {
    val builderClassName = className.nestedClass("${className.simpleName()}Builder")
    val wildcardBuilder =
        ParameterizedTypeName.get(
            builderClassName,
            WildcardTypeName.subtypeOf(Types.OBJECT),
            WildcardTypeName.subtypeOf(Types.OBJECT),
        )

    val builder =
        MethodSpec
            .constructorBuilder()
            .addModifiers(Modifier.PROTECTED)
            .addParameter(wildcardBuilder, "b")

    fields.forEach { field ->
        builder.addStatement($$"this.$N = b.$N", field.name(), field.name())
    }

    return builder.build()
}

/**
 * Generates toString() method with Lombok-style formatting.
 */
private fun generateToString(): MethodSpec =
    MethodSpec
        .methodBuilder("toString")
        .addModifiers(Modifier.PUBLIC)
        .returns(String::class.java)
        .addStatement($$"return $T.reflectionToString(this)", ClassName.get("org.apache.commons.lang3.builder", "ToStringBuilder"))
        .build()

/**
 * Generates equals() method with proper null and type checking (Lombok style).
 */
private fun generateEquals(): MethodSpec =
    MethodSpec
        .methodBuilder("equals")
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BOOLEAN)
        .addParameter(ParameterSpec.builder(Types.OBJECT, "o").build())
        .addStatement($$"return $T.reflectionEquals(this, o, false)", ClassName.get("org.apache.commons.lang3.builder", "EqualsBuilder"))
        .build()

/**
 * Generates hashCode() method using PRIME and result pattern (Lombok style).
 */
private fun generateHashCode(): MethodSpec =
    MethodSpec
        .methodBuilder("hashCode")
        .addModifiers(Modifier.PUBLIC)
        .returns(
            TypeName.INT,
        ).addStatement($$"return $T.reflectionHashCode(this, false)", ClassName.get("org.apache.commons.lang3.builder", "HashCodeBuilder"))
        .build()

/**
 * Generates $fillValuesFrom method for SuperBuilder pattern.
 */
private fun generateFillValuesFromMethod(
    bTypeVar: TypeVariableName,
    cTypeVar: TypeVariableName,
): MethodSpec =
    MethodSpec
        .methodBuilder($$"$fillValuesFrom")
        .addModifiers(Modifier.PROTECTED)
        .returns(bTypeVar)
        .addParameter(cTypeVar, "instance")
        .addStatement($$$"$$fillValuesFromInstanceIntoBuilder(instance, this)")
        .addStatement($$"return ($T)this.self()", bTypeVar)
        .build()

/**
 * Generates $fillValuesFromInstanceIntoBuilder static helper method.
 */
private fun generateFillValuesFromInstanceIntoBuilderMethod(
    className: ClassName,
    builderName: String,
    fields: List<FieldSpec>,
): MethodSpec {
    val wildcardBuilder =
        ParameterizedTypeName.get(
            className.nestedClass(builderName),
            WildcardTypeName.subtypeOf(Types.OBJECT),
            WildcardTypeName.subtypeOf(Types.OBJECT),
        )

    val builder =
        MethodSpec
            .methodBuilder($$"$fillValuesFromInstanceIntoBuilder")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(TypeName.VOID)
            .addParameter(className, "instance")
            .addParameter(wildcardBuilder, "b")

    fields.forEach { field ->
        builder.addStatement($$"b.$N(instance.$N)", field.name(), field.name())
    }

    return builder.build()
}

/**
 * Generates fluent setter method(s) for abstract builder with @JsonProperty annotation.
 * For NullableOptional fields, generates two methods:
 * 1. Main method accepting NullableOptional<T> with @NonNull annotation
 * 2. Convenience method accepting T with @Nullable annotation
 * Returns (B)this.self() for type-safe chaining.
 */
private fun generateAbstractBuilderSetter(
    field: FieldSpec,
    javadoc: String = "",
    bTypeVar: TypeVariableName,
): List<MethodSpec> {
    val fieldName = field.name()
    val fieldType = field.type()

    // Check if this is a NullableOptional field by checking the string representation
    val typeString = fieldType.toString()
    val isNullableOptional = typeString.startsWith("io.github.pulpogato.common.NullableOptional<")

    val methods = mutableListOf<MethodSpec>()

    // Generate main builder method (always)
    val mainMethodBuilder =
        MethodSpec
            .methodBuilder(fieldName)
            .addModifiers(Modifier.PUBLIC)
            .returns(bTypeVar)
            .addParameter(
                ParameterSpec
                    .builder(fieldType, fieldName)
                    .apply {
                        if (isNullableOptional) {
                            addAnnotation(nonNull())
                        }
                    }.build(),
            ).addStatement($$"this.$N = $N", fieldName, fieldName)
            .addStatement($$"return ($T)this.self()", bTypeVar)

    // Add @JsonProperty annotation
    val jsonPropertyAnnotation =
        field.annotations().find {
            it.type().toString().contains("JsonProperty")
        }
    if (jsonPropertyAnnotation != null) {
        mainMethodBuilder.addAnnotation(jsonPropertyAnnotation)
    }

    // Add Javadoc if present
    if (javadoc.isNotBlank()) {
        mainMethodBuilder.addJavadoc(javadoc)
    }

    methods.add(mainMethodBuilder.build())

    // Generate convenience method for NullableOptional fields
    if (isNullableOptional && fieldType is ParameterizedTypeName) {
        // Use reflection to access the private typeArguments field
        // This is necessary because Palantir's JavaPoet 0.9.0 doesn't expose a public API for this
        try {
            val typeArgumentsField = ParameterizedTypeName::class.java.getDeclaredField("typeArguments")
            typeArgumentsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val typeArguments = typeArgumentsField.get(fieldType) as List<TypeName>
            val unwrappedType = typeArguments[0]

            val convenienceMethodBuilder =
                MethodSpec
                    .methodBuilder(fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(bTypeVar)
                    .addParameter(
                        ParameterSpec
                            .builder(unwrappedType, fieldName)
                            .addAnnotation(Annotations.nullable())
                            .build(),
                    ).addStatement(
                        $$"return this.$N($T.ofNullable($N))",
                        fieldName,
                        Types.NULLABLE_OPTIONAL,
                        fieldName,
                    ).addAnnotation(Annotations.deprecated("1.2.0"))

            // Add Javadoc for convenience method
            if (javadoc.isNotBlank()) {
                convenienceMethodBuilder.addJavadoc(
                    javadoc + "\n\n<p>Convenience method that wraps the value in NullableOptional automatically.\n" +
                        "Pass null to explicitly set the field to null in JSON.</p>\n\n" +
                        "@deprecated Use {@link #$fieldName(NullableOptional)} instead\n",
                )
            } else {
                convenienceMethodBuilder.addJavadoc(
                    "Convenience method that wraps the value in NullableOptional automatically.\n" +
                        "Pass null to explicitly set the field to null in JSON.\n\n" +
                        "@param $fieldName the value to set (null will be converted to NullableOptional.ofNull())\n" +
                        "@return this builder for method chaining\n" +
                        "@deprecated Use {@link #$fieldName(NullableOptional)} instead\n",
                )
            }

            methods.add(convenienceMethodBuilder.build())
        } catch (_: Exception) {
            // If reflection fails, skip generating the convenience method
            // This is a fallback to ensure code generation doesn't fail completely
        }
    }

    return methods
}

/**
 * Generates abstract nested static Builder class with generic type parameters (SuperBuilder pattern).
 */
private fun generateBuilderClass(
    className: ClassName,
    fields: List<FieldSpec>,
): TypeSpec {
    val builderName = "${className.simpleName()}Builder"
    val cTypeVar = TypeVariableName.get("C", className)
    val bTypeVar = TypeVariableName.get("B")

    // B extends ClassNameBuilder<C, B>
    val bBound =
        ParameterizedTypeName.get(
            className.nestedClass(builderName),
            cTypeVar,
            bTypeVar,
        )

    val builder =
        TypeSpec
            .classBuilder(builderName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.STATIC)
            .addTypeVariable(cTypeVar)
            .addTypeVariable(TypeVariableName.get("B", bBound))

    // Add fields to builder (same as parent, but non-final)
    fields.forEach { field ->
        val builderField =
            FieldSpec
                .builder(field.type(), field.name(), Modifier.PRIVATE)
                .build()
        builder.addField(builderField)
    }

    // Check for fields with @Builder.Default annotation (initializers)
    val fieldsWithDefaults =
        fields.filter { field ->
            field.annotations().any { annotation ->
                annotation.type().toString().contains("Builder.Default")
            }
        }

    // Add constructor if there are default fields
    if (fieldsWithDefaults.isNotEmpty()) {
        val constructorBuilder =
            MethodSpec
                .constructorBuilder()

        fieldsWithDefaults.forEach { field ->
            // Try to extract initializer from annotations
            // For NullableOptional.notSet(), we need to initialize it
            val fieldType = field.type()
            if (fieldType.toString().startsWith("io.github.pulpogato.common.NullableOptional")) {
                constructorBuilder.addStatement(
                    $$"this.$N = $T.notSet()",
                    field.name(),
                    ClassName.get("io.github.pulpogato.common", "NullableOptional"),
                )
            }
        }

        builder.addMethod(constructorBuilder.build())
    }

    // Add $fillValuesFrom method
    builder.addMethod(generateFillValuesFromMethod(bTypeVar, cTypeVar))

    // Add $fillValuesFromInstanceIntoBuilder static helper
    builder.addMethod(generateFillValuesFromInstanceIntoBuilderMethod(className, builderName, fields))

    // Add fluent setter methods with @JsonProperty
    fields.forEach { field ->
        val javadoc = extractJavadoc(field)
        generateAbstractBuilderSetter(field, javadoc, bTypeVar).forEach { method ->
            builder.addMethod(method)
        }
    }

    // Add abstract self() method
    builder.addMethod(
        MethodSpec
            .methodBuilder("self")
            .addModifiers(Modifier.PROTECTED, Modifier.ABSTRACT)
            .returns(bTypeVar)
            .build(),
    )

    // Add abstract build() method
    builder.addMethod(
        MethodSpec
            .methodBuilder("build")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(cTypeVar)
            .build(),
    )

    // Add toString() method
    builder.addMethod(generateToString())

    return builder.build()
}

/**
 * Generates private nested implementation class for SuperBuilder pattern.
 */
private fun generateBuilderImplClass(className: ClassName): TypeSpec {
    val builderName = "${className.simpleName()}Builder"
    val implName = "${className.simpleName()}BuilderImpl"
    val builderClassName = className.nestedClass(builderName)
    val implClassName = className.nestedClass(implName)

    // TopicBuilderImpl extends TopicBuilder<Topic, TopicBuilderImpl>
    val superclass =
        ParameterizedTypeName.get(
            builderClassName,
            className,
            implClassName,
        )

    return TypeSpec
        .classBuilder(implName)
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .superclass(superclass)
        .addMethod(
            MethodSpec
                .methodBuilder("self")
                .addModifiers(Modifier.PROTECTED)
                .returns(implClassName)
                .addStatement("return this")
                .build(),
        ).addMethod(
            MethodSpec
                .methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(className)
                .addStatement($$"return new $T(this)", className)
                .build(),
        ).build()
}

/**
 * Generates static factory method for builder.
 */
private fun generateBuilderFactoryMethod(className: ClassName): MethodSpec =
    MethodSpec
        .methodBuilder("builder")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(createBuilder(className))
        .addStatement($$"return new $T()", className.nestedClass("${className.simpleName()}BuilderImpl"))
        .build()

/**
 * Gets the wildcard builder type for a given class.
 */
private fun createBuilder(className: ClassName): ParameterizedTypeName {
    val builderClassName = className.nestedClass("${className.simpleName()}Builder")
    return ParameterizedTypeName.get(
        builderClassName,
        WildcardTypeName.subtypeOf(Types.OBJECT),
        WildcardTypeName.subtypeOf(Types.OBJECT),
    )
}

/**
 * Generates toBuilder() method for copying instances.
 */
private fun generateToBuilderMethod(className: ClassName): MethodSpec {
    val wildcardBuilder = createBuilder(className)
    val implClassName = className.nestedClass("${className.simpleName()}BuilderImpl")

    return MethodSpec
        .methodBuilder("toBuilder")
        .addModifiers(Modifier.PUBLIC)
        .returns(wildcardBuilder)
        .addStatement($$$"return (new $T()).$$fillValuesFrom(this)", implClassName)
        .build()
}

private fun addProperties(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    nameRef: ClassName,
    classBuilder: TypeSpec.Builder,
) {
    val knownFields = classBuilder.build().fieldSpecs().map { it.name() }
    val knownSubTypes = classBuilder.build().typeSpecs().map { it.name() }

    entry.value.properties?.forEach { p ->
        processProperty(context, p, nameRef, classBuilder, knownFields, knownSubTypes)
    }
}

private fun processProperty(
    context: Context,
    p: Map.Entry<String, Schema<*>>,
    nameRef: ClassName,
    classBuilder: TypeSpec.Builder,
    knownFields: List<String>,
    knownSubTypes: List<String>,
) {
    referenceAndDefinition(context.withSchemaStack("properties", p.key), p, "", nameRef)?.let { (d, s) ->
        processNestedTypeIfPresent(s, d, knownSubTypes, classBuilder)
        addFieldIfNew(context, p, d, knownFields, classBuilder)
    }
}

private fun processNestedTypeIfPresent(
    nestedType: TypeSpec?,
    typeName: TypeName,
    knownSubTypes: List<String>,
    classBuilder: TypeSpec.Builder,
) {
    nestedType?.let {
        if (!knownSubTypes.contains(it.name())) {
            addNestedTypeWithMethods(it, typeName, classBuilder)
        }
    }
}

private fun addNestedTypeWithMethods(
    nestedType: TypeSpec,
    typeName: TypeName,
    classBuilder: TypeSpec.Builder,
) {
    if (nestedType.methodSpecs().none { it.name() == "toCode" } && typeName is ClassName) {
        val builder = nestedType.toBuilder()
        addToCodeMethod(nestedType, builder, typeName)
        classBuilder.addType(builder.addModifiers(Modifier.STATIC).build())
    } else {
        classBuilder.addType(nestedType.toBuilder().addModifiers(Modifier.STATIC).build())
    }
}

private fun addFieldIfNew(
    context: Context,
    p: Map.Entry<String, Schema<*>>,
    typeName: TypeName,
    knownFields: List<String>,
    classBuilder: TypeSpec.Builder,
) {
    val fieldName = p.key.unkeywordize().camelCase()
    if (!knownFields.contains(fieldName)) {
        // Check if this property was added from additions.schema.json
        val schemaName =
            if (context.schemaStack.size >= 4 &&
                context.schemaStack[0] == "#" &&
                context.schemaStack[1] == "components" &&
                context.schemaStack[2] == "schemas"
            ) {
                context.schemaStack[3]
            } else {
                null
            }
        val sourceFile =
            if (schemaName != null && context.isAddedProperty(schemaName, p.key)) {
                "additions.schema.json"
            } else {
                "schema.json"
            }

        // Check if the schema explicitly allows null (has "null" in its types array)
        val types = p.value.types?.filterNotNull() ?: emptyList()
        val hasNull = types.contains("null")
        // Check if this is an object type (not a simple type like String, Integer, etc.)
        val isObjectType = typeName is ClassName && !isSimpleType(typeName)

        // Determine the actual field type and annotations based on nullability
        val actualTypeName: TypeName
        val builder: FieldSpec.Builder

        if (hasNull && isObjectType) {
            // Wrap in NullableOptional for nullable object fields
            actualTypeName = ParameterizedTypeName.get(Types.NULLABLE_OPTIONAL, typeName)
            builder =
                FieldSpec
                    .builder(actualTypeName, fieldName, Modifier.PRIVATE)
                    .addAnnotation(jsonProperty(p.key))
                    .addAnnotation(generated(0, context.withSchemaStack("properties", p.key), sourceFile))
                    .addAnnotations(nullableOptionalSerializer())
                    .addAnnotations(nullableOptionalDeserializer())
                    .addAnnotation(jsonIncludeNonEmpty())
                    .initializer($$"$T.notSet()", Types.NULLABLE_OPTIONAL)
        } else {
            // Keep existing behavior for non-nullable or simple types
            actualTypeName = typeName
            builder =
                FieldSpec
                    .builder(actualTypeName, fieldName, Modifier.PRIVATE)
                    .addAnnotation(jsonProperty(p.key))
                    .addAnnotation(generated(0, context.withSchemaStack("properties", p.key), sourceFile))

            if (hasNull) {
                // Add @JsonInclude(ALWAYS) for simple nullable types
                builder.addAnnotation(jsonIncludeAlways())
            }
        }

        schemaJavadoc(p).let {
            if (it.isNotBlank()) {
                builder.addJavadoc($$"$L", it)
            }
        }

        classBuilder.addField(builder.build())
    }
}

private fun buildEnum(
    context: Context,
    entry: Map.Entry<String, Schema<*>>,
    className: ClassName,
): TypeSpec {
    val builder =
        TypeSpec
            .enumBuilder(className.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated(0, context))
            .addField(
                FieldSpec
                    .builder(String::class.java, "value", Modifier.PRIVATE, Modifier.FINAL)
                    .addAnnotation(jsonValue())
                    .addJavadoc($$"$L", "The value of the enum")
                    .build(),
            )

    schemaJavadoc(entry).let {
        if (it.isNotBlank()) {
            builder.addJavadoc($$"$L", it)
        }
    }

    entry.value.enum
        .map { it?.toString() }
        .forEach {
            val enumValue = it?.unkeywordize()?.trainCase() ?: "NULL"
            val enumName = it ?: "null"
            builder.addEnumConstant(enumValue, TypeSpec.anonymousClassBuilder($$"$S", enumName).build())
        }

    // Add explicit constructor
    builder.addMethod(
        MethodSpec
            .constructorBuilder()
            .addParameter(String::class.java, "value")
            .addStatement("this.value = value")
            .build(),
    )

    // Add explicit getValue() getter
    builder.addMethod(
        MethodSpec
            .methodBuilder("getValue")
            .addModifiers(Modifier.PUBLIC)
            .returns(String::class.java)
            .addStatement("return this.value")
            .build(),
    )

    // Add explicit toString() override
    builder.addMethod(
        MethodSpec
            .methodBuilder("toString")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Types.OVERRIDE)
            .returns(String::class.java)
            .addStatement("return this.value")
            .build(),
    )

    // Add the converter as a nested static class
    val converterClass = buildEnumConverter(className)
    builder.addType(converterClass)

    return builder.build()
}

private fun buildEnumConverter(enumClassName: ClassName): TypeSpec {
    val enumSimpleName = enumClassName.simpleName()
    return TypeSpec
        .classBuilder("${enumSimpleName}Converter")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addSuperinterface(
            ParameterizedTypeName.get(
                ClassName.get("org.springframework.core.convert.converter", "Converter"),
                enumClassName.annotated(nonNull()),
                ClassName.get(String::class.java).annotated(nonNull()),
            ),
        ).addMethod(
            MethodSpec
                .methodBuilder("convert")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .addAnnotation(ClassName.get("org.jspecify.annotations", "NonNull"))
                .addParameter(
                    ParameterSpec
                        .builder(enumClassName, "source")
                        .addAnnotation(ClassName.get("org.jspecify.annotations", "NonNull"))
                        .build(),
                ).returns(ClassName.get(String::class.java))
                .addStatement("return source.getValue()")
                .build(),
        ).build()
}

private val javadocExpressionRegex = Regex("\\$\\{(.+)}")

private fun schemaJavadoc(entry: Map.Entry<String, Schema<*>>): String {
    val title = entry.value.title ?: ""
    val description = entry.value.description ?: ""
    val javadoc =
        if (description.contains(title)) {
            MarkdownHelper.mdToHtml(description)
        } else {
            MarkdownHelper.mdToHtml("**$title**\n\n$description")
        }
    return javadoc.replace(javadocExpressionRegex, $$"$1")
}