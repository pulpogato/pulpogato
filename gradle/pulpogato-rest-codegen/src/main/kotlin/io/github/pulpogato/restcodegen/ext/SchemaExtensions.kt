package io.github.pulpogato.restcodegen.ext

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations.deserializerAnnotation
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.jsonIncludeNonNull
import io.github.pulpogato.restcodegen.Annotations.jsonProperty
import io.github.pulpogato.restcodegen.Annotations.jsonValue
import io.github.pulpogato.restcodegen.Annotations.lombok
import io.github.pulpogato.restcodegen.Annotations.serializerAnnotation
import io.github.pulpogato.restcodegen.Annotations.singleValueAsArray
import io.github.pulpogato.restcodegen.Annotations.superBuilder
import io.github.pulpogato.restcodegen.Annotations.typeGenerated
import io.github.pulpogato.restcodegen.Context
import io.github.pulpogato.restcodegen.MarkdownHelper
import io.github.pulpogato.restcodegen.Types
import io.swagger.v3.oas.models.media.Schema
import javax.lang.model.element.Modifier

fun Map.Entry<String, Schema<*>>.className() = key.pascalCase()

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
            .addAnnotation(lombok("Getter"))
            .addAnnotation(lombok("Setter"))
            .addAnnotation(lombok("EqualsAndHashCode"))
            .addModifiers(Modifier.PUBLIC)

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

    // Generate manual getters, setters, toString, and constructors for the fields
    val builtType = theType.build()

    addToStringMethod(builtType, className, theType)

    val settableFields = getSettableFields(fields, className)
    val gettableFields = getGettableFields(fields, className)
    val deserializer = buildDeserializer(className, fancyObjectType, settableFields)
    val serializer = buildSerializer(className, fancyObjectType, gettableFields)

    theType
        .addType(deserializer)
        .addType(serializer)
        .addAnnotation(deserializerAnnotation(className, deserializer))
        .addAnnotation(serializerAnnotation(className, serializer))
        .addAnnotation(superBuilder())
        .addAnnotation(lombok("NoArgsConstructor"))
        .addAnnotation(lombok("AllArgsConstructor"))

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
    val rad = referenceAndDefinition(context, keyValuePair, "", classRef)!!
    rad.let {
        if (rad.second != null) {
            val builder = rad.second!!.toBuilder()
            if (rad.first is ClassName) {
                val parentStack = context.schemaStack.dropLast(2).toTypedArray()
                addProperties(context.withSchemaStack(*parentStack), entry, rad.first as ClassName, builder)
            }
            theType.addType(builder.addModifiers(Modifier.STATIC).build())
        }
        val fieldSpec = buildFieldSpec(context, keyValuePair, classRef)
        theType.addField(fieldSpec)
        val first =
            when {
                fieldSpec.type() is ParameterizedTypeName -> (fieldSpec.type() as ParameterizedTypeName).rawType()
                else -> fieldSpec.type()
            }
        fields.add(Pair(first, fieldSpec.name().pascalCase()))
    }
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
): List<CodeBlock> =
    fields.map { (type, name) ->
        CodeBlock.of(
            $$"new $T<>($T.class, $T::get$$name)",
            pulpogatoClass("FancySerializer", "GettableField"),
            type.withoutAnnotations(),
            ClassName.get("", className),
        )
    }

private fun pulpogatoClass(
    simpleName: String,
    vararg simpleNames: String,
): ClassName = ClassName.get("io.github.pulpogato.common", simpleName, *simpleNames)

private fun getSettableFields(
    fields: ArrayList<Pair<TypeName, String>>,
    className: String?,
): List<CodeBlock> =
    fields.map { (type, name) ->
        CodeBlock.of(
            $$"new $T<>($T.class, $T::set$$name)",
            pulpogatoClass("FancyDeserializer", "SettableField"),
            type.withoutAnnotations(),
            ClassName.get("", className),
        )
    }

private fun buildSerializer(
    className: String,
    fancyObjectType: String,
    gettableFields: List<CodeBlock>,
): TypeSpec =
    TypeSpec
        .classBuilder("${className}Serializer")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .superclass(ParameterizedTypeName.get(pulpogatoClass("FancySerializer"), ClassName.get("", className)))
        .addMethod(
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(
                    $$"""super($T.class, $T.$${fancyObjectType.trainCase()}, $T.of(
                        |    $L
                        |))
                    """.trimMargin(),
                    ClassName.get("", className),
                    pulpogatoClass("Mode"),
                    Types.LIST,
                    CodeBlock.join(gettableFields, ",\n    "),
                ).build(),
        ).build()

private fun buildDeserializer(
    className: String,
    fancyObjectType: String,
    settableFields: List<CodeBlock>,
): TypeSpec =
    TypeSpec
        .classBuilder("${className}Deserializer")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .superclass(ParameterizedTypeName.get(pulpogatoClass("FancyDeserializer"), ClassName.get("", className)))
        .addMethod(
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
                    pulpogatoClass("Mode"),
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
            .addAnnotation(lombok("Getter"))
            .addAnnotation(lombok("Setter"))
            .addAnnotation(lombok("EqualsAndHashCode"))
            .addAnnotation(superBuilder())
            .addAnnotation(lombok("NoArgsConstructor"))
            .addAnnotation(lombok("AllArgsConstructor"))
            .addAnnotation(jsonIncludeNonNull())

    schemaJavadoc(entry).let {
        if (it.isNotBlank()) {
            builder.addJavadoc($$"$L", it)
        }
    }

    addProperties(context, entry, nameRef, builder)

    // Generate manual getters, setters, and toString for the properties
    val builtClass = builder.build()
    addToStringMethod(builtClass, name, builder)
    return builder.build()
}

private fun addToStringMethod(
    builtClass: TypeSpec,
    name: String,
    builder: TypeSpec.Builder,
) {
    if (builtClass.fieldSpecs().isNotEmpty()) {
        val toStringStatement =
            CodeBlock
                .builder()
                .add($$"return \"/* $L */ \" + new $T(this, $T.JSON_STYLE)", name, Types.TO_STRING_BUILDER, Types.TO_STRING_STYLE)

        builtClass.fieldSpecs().forEach { field ->
            toStringStatement.add($$"\n    .append($S, $N)", field.name(), field.name())
        }

        toStringStatement.add("\n    .toString()")

        builder.addMethod(
            MethodSpec
                .methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(String::class.java)
                .addStatement(toStringStatement.build())
                .build(),
        )
    }
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
        referenceAndDefinition(context.withSchemaStack("properties", p.key), p, "", nameRef)?.let { (d, s) ->

            s?.let {
                if (!knownSubTypes.contains(it.name())) {
                    classBuilder.addType(it.toBuilder().addModifiers(Modifier.STATIC).build())
                }
            }
            if (!knownFields.contains(p.key.unkeywordize().camelCase())) {
                val builder =
                    FieldSpec
                        .builder(d, p.key.unkeywordize().camelCase(), Modifier.PRIVATE)
                        .addAnnotation(jsonProperty(p.key))
                        .addAnnotation(generated(0, context.withSchemaStack("properties", p.key)))

                schemaJavadoc(p).let {
                    if (it.isNotBlank()) {
                        builder.addJavadoc($$"$L", it)
                    }
                }

                classBuilder.addField(builder.build())
            }
        }
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
            .addAnnotation(lombok("Getter"))
            .addAnnotation(lombok("RequiredArgsConstructor"))
            .addAnnotation(lombok("ToString"))
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
                enumClassName,
                ClassName.get(String::class.java),
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