package io.github.pulpogato.restcodegen.ext

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
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
) = oneOf.size == 2 &&
    oneOf.first().types == setOf(type) &&
    oneOf.last().types == setOf("array") &&
    oneOf.last().items.types == setOf(type)

fun typesAre(
    oneOf: List<Schema<Any>>,
    vararg types: String,
) = types.toSet() == oneOf.flatMap { it.types ?: listOf() }.toSet()

fun Map.Entry<String, Schema<*>>.referenceAndDefinition(
    prefix: String,
    parentClass: ClassName?,
    isArray: Boolean = false,
): Pair<TypeName, TypeSpec?>? {
    val types = value.types?.filterNotNull()?.filter { it != "null" }
    val anyOf = value.anyOf?.filterNotNull()?.filter { it.types != setOf("null") }
    val oneOf = value.oneOf?.filterNotNull()
    val allOf = value.allOf?.filterNotNull()

    return when {
        key == "empty-object" -> Pair(Types.EMPTY_OBJECT, null)
        value.`$ref` != null -> buildReferenceAndDefinitionFromRef()

        anyOf != null && anyOf.size == 1 -> {
            val anyOfValue = anyOf.first()
            mapOf(key to anyOfValue).entries.first().referenceAndDefinition("", null)!!
        }

        oneOf != null && isSingleOrArray(oneOf, "string") -> Pair(Types.LIST_OF_STRINGS.annotated(typeGenerated(), singleValueAsArray()), null)

        oneOf != null && typesAre(oneOf, "string", "integer") -> Pair(Types.STRING_OR_INTEGER.annotated(typeGenerated()), null)
        anyOf != null ->
            buildType("${prefix}${this.className()}", parentClass) {
                buildFancyObject(anyOf, "anyOf", it)
            }

        oneOf != null ->
            buildType("${prefix}${this.className()}", parentClass) {
                buildFancyObject(oneOf, "oneOf", it)
            }

        allOf != null ->
            buildType("${prefix}${this.className()}", parentClass) {
                buildFancyObject(allOf, "allOf", it)
            }

        types == null && value.properties != null ->
            mapOf(key to value.also { it.types = mutableSetOf("object") }).entries.first()
                .referenceAndDefinition("", parentClass)!!

        types == null && value.properties != null && value.properties.isEmpty() && value.additionalProperties == false ->
            Pair(Types.VOID.annotated(typeGenerated()), null)

        types == null && value.properties != null && value.properties.isNotEmpty() ->
            buildType("${prefix}${this.className()}", parentClass) { buildSimpleObject(isArray, it) }

        types == null -> Pair(Types.OBJECT.annotated(typeGenerated()), null)

        types.isEmpty() -> null

        types.size == 1 ->
            when (types.first()) {
                "string" -> buildReferenceAndDefinitionFromString(prefix, parentClass)
                "integer" -> buildReferenceAndDefinitionFromInteger()
                "boolean" -> Pair(Types.BOOLEAN, null)
                "number" -> buildReferenceAndDefinitionFromNumber()
                "array" -> buildReferenceAndDefinitionFromArray(parentClass)
                "object" -> buildReferenceAndDefinitionFromObject(parentClass, prefix, isArray)
                else -> throw RuntimeException("Unknown type for $key, stack: ${Context.getSchemaStackRef()}")
            }

        types.toSet() == setOf("string", "integer") -> Pair(Types.STRING_OR_INTEGER, null)
        types.toSet() == setOf("string", "object") -> Pair(Types.STRING_OR_OBJECT, null)
        types.toSet() == setOf("string", "object", "integer") -> Pair(Types.STRING_OBJECT_OR_INTEGER, null)
        else -> Pair(Types.TODO, null)
    }
}

private fun Map.Entry<String, Schema<*>>.buildReferenceAndDefinitionFromObject(
    parentClass: ClassName?,
    prefix: String,
    isArray: Boolean,
): Pair<TypeName, TypeSpec?> =
    when {
        value.additionalProperties != null && (value.properties == null || value.properties.isEmpty()) -> {
            val additionalProperties = value.additionalProperties
            if (additionalProperties is Schema<*>) {
                mapOf(key to additionalProperties).entries.first().referenceAndDefinition("", parentClass)!!
                    .let { Pair(ParameterizedTypeName.get(Types.MAP, Types.STRING, it.first), it.second) }
            } else {
                val message = additionalProperties.javaClass
                println(message)
                Pair(Types.TODO, null)
            }
        }

        value.properties != null && value.properties.isNotEmpty() ->
            buildType("${prefix}${this.className()}", parentClass) {
                buildSimpleObject(isArray, it)
            }

        else -> Pair(Types.MAP_STRING_OBJECT.annotated(typeGenerated()), null)
    }

private fun Map.Entry<String, Schema<*>>.buildReferenceAndDefinitionFromArray(parentClass: ClassName?): Pair<TypeName, TypeSpec?>? =
    Context.withSchemaStack("items") { mapOf(key to value.items).entries.first().referenceAndDefinition("", parentClass, isArray = true) }
        ?.let {
            val oldTypeGenerated =
                it.first.annotations()
                    .filter { spec -> (spec.type() as ClassName).simpleName() == "TypeGenerated" }
            val otherAnnotations =
                it.first.annotations()
                    .filter { spec -> (spec.type() as ClassName).simpleName() != "TypeGenerated" }

            Pair(
                ParameterizedTypeName.get(
                    Types.LIST,
                    it.first.withoutAnnotations()
                        .annotated(oldTypeGenerated),
                )
                    .annotated(otherAnnotations.distinct()).annotated(typeGenerated()),
                it.second,
            )
        }

private fun Map.Entry<String, Schema<*>>.buildReferenceAndDefinitionFromNumber(): Pair<TypeName, TypeSpec?> =
    when (value.format) {
        "double" -> Pair(Types.DOUBLE.annotated(typeGenerated()), null)
        "float" -> Pair(Types.FLOAT.annotated(typeGenerated()), null)
        else -> Pair(Types.BIG_DECIMAL.annotated(typeGenerated()), null)
    }

private fun Map.Entry<String, Schema<*>>.buildReferenceAndDefinitionFromInteger(): Pair<TypeName, TypeSpec?> =
    when (value.format) {
        "int32" -> Pair(Types.INTEGER.annotated(typeGenerated()), null)
        "timestamp" -> Pair(Types.EPOCH_TIME.annotated(typeGenerated()), null)
        else -> Pair(Types.LONG.annotated(typeGenerated()), null)
    }

private fun Map.Entry<String, Schema<*>>.buildReferenceAndDefinitionFromString(
    prefix: String,
    parentClass: ClassName?,
): Pair<TypeName, TypeSpec?> =
    when {
        value.enum != null -> buildType("${prefix}${this.className()}", parentClass) { buildEnum(it) }

        value.format == null -> Pair(Types.STRING, null)
        else ->
            when (value.format) {
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

                else -> throw RuntimeException("Unknown string type for $key, stack: ${Context.getSchemaStackRef()}")
            }
    }

private fun Map.Entry<String, Schema<*>>.buildReferenceAndDefinitionFromRef(): Pair<TypeName, TypeSpec?> {
    val schemaName = value.`$ref`.replace("#/components/schemas/", "")
    val entries = Context.instance.get().openAPI.components.schemas.filter { (k, _) -> k == schemaName }.entries
    val schema = entries.first()
    return schema.referenceAndDefinition("", null)!!.copy(second = null)
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

private fun Map.Entry<String, Schema<*>>.buildFancyObject(
    subSchemas: List<Schema<Any>>,
    type: String,
    classRef: ClassName,
): TypeSpec {
    val className = classRef.simpleName()
    return Context.withSchemaStack(type) {
        val theType =
            TypeSpec.classBuilder(className)
                .addJavadoc(schemaJavadoc())
                .addAnnotation(generated(0))
                .addAnnotation(lombok("Data"))
                .addModifiers(Modifier.PUBLIC)

        val fields = ArrayList<Pair<TypeName, String>>()

        subSchemas
            .mapIndexed { index, it ->
                var newKey = key + index
                if (it.`$ref` != null) {
                    newKey = it.`$ref`.replace("#/components/schemas/", "")
                }
                newKey to it
            }
            .forEachIndexed { index, (newKey, it) ->
                val keyValuePair = mapOf(newKey to it).entries.first()
                Context.withSchemaStack("$index") {
                    val rad = keyValuePair.referenceAndDefinition("", classRef)!!
                    rad.let {
                        if (rad.second != null) {
                            val builder = rad.second!!.toBuilder()
                            if (rad.first is ClassName) {
                                addProperties(false, rad.first as ClassName, builder)
                            }
                            theType.addType(builder.addModifiers(Modifier.STATIC).build())
                        }
                        val fieldSpec = buildFieldSpec(keyValuePair, classRef)
                        theType.addField(fieldSpec)
                        if (fieldSpec.type() is ParameterizedTypeName) {
                            fields.add(Pair((fieldSpec.type() as ParameterizedTypeName).rawType(), fieldSpec.name().pascalCase()))
                        } else {
                            fields.add(Pair(fieldSpec.type(), fieldSpec.name().pascalCase()))
                        }
                    }
                }
            }

        val settableFields = getSettableFields(fields, className)
        val gettableFields = getGettableFields(fields, className)
        val deserializer = buildDeserializer(className, type, settableFields)
        val serializer = buildSerializer(className, type, gettableFields)

        theType.addType(deserializer)
            .addType(serializer)
            .addAnnotation(deserializerAnnotation(className, deserializer))
            .addAnnotation(serializerAnnotation(className, serializer))
            .addAnnotation(lombok("Builder"))
            .addAnnotation(lombok("NoArgsConstructor"))
            .addAnnotation(lombok("AllArgsConstructor"))

        theType.build()
    }
}

private fun buildFieldSpec(
    keyValuePair: Map.Entry<String, Schema<Any>>,
    classRef: ClassName,
): FieldSpec =
    FieldSpec
        .builder(
            keyValuePair.referenceAndDefinition("", classRef)!!.first,
            keyValuePair.key.unkeywordize().camelCase(),
            Modifier.PRIVATE,
        )
        .addJavadoc(keyValuePair.schemaJavadoc().split("\n").dropLastWhile { it.isEmpty() }.joinToString("\n"))
        .addAnnotation(generated(0))
        .addAnnotation(jsonProperty(keyValuePair.key))
        .build()

private fun getGettableFields(
    fields: ArrayList<Pair<TypeName, String>>,
    className: String?,
): List<CodeBlock> =
    fields.map { (type, name) ->
        CodeBlock.of(
            "new \$T<>(\$T.class, \$T::get$name)",
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
            "new \$T<>(\$T.class, \$T::set$name)",
            pulpogatoClass("FancyDeserializer", "SettableField"),
            type.withoutAnnotations(),
            ClassName.get("", className),
        )
    }

private fun buildSerializer(
    className: String?,
    type: String,
    gettableFields: List<CodeBlock>,
): TypeSpec =
    TypeSpec.classBuilder("${className}Serializer")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .superclass(ParameterizedTypeName.get(pulpogatoClass("FancySerializer"), ClassName.get("", className)))
        .addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(
                    """super(${"$"}T.class, ${"$"}T.${type.trainCase()}, ${"$"}T.of(
                        |    ${"$"}L
                        |))
                    """.trimMargin(),
                    ClassName.get("", className),
                    pulpogatoClass("Mode"),
                    Types.LIST,
                    CodeBlock.join(gettableFields, ",\n    "),
                )
                .build(),
        )
        .build()

private fun buildDeserializer(
    className: String?,
    type: String,
    settableFields: List<CodeBlock>,
): TypeSpec =
    TypeSpec.classBuilder("${className}Deserializer")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .superclass(ParameterizedTypeName.get(pulpogatoClass("FancyDeserializer"), ClassName.get("", className)))
        .addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(
                    """super(${"$"}T.class, ${"$"}T::new, ${"$"}T.${type.trainCase()}, ${"$"}T.of(
                        |    ${"$"}L
                        |))
                    """.trimMargin(),
                    ClassName.get("", className),
                    ClassName.get("", className),
                    pulpogatoClass("Mode"),
                    Types.LIST,
                    CodeBlock.join(settableFields, ",\n    "),
                )
                .build(),
        )
        .build()

private fun Map.Entry<String, Schema<*>>.buildSimpleObject(
    isArray: Boolean,
    nameRef: ClassName,
): TypeSpec {
    val name = nameRef.simpleName()

    val builder =
        TypeSpec.classBuilder(name)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated(0))
            .addAnnotation(lombok("Data"))
            .addAnnotation(lombok("Builder"))
            .addAnnotation(lombok("NoArgsConstructor"))
            .addAnnotation(lombok("AllArgsConstructor"))
            .addAnnotation(jsonIncludeNonNull())

    addProperties(isArray, nameRef, builder)

    return builder.build()
}

private fun Map.Entry<String, Schema<*>>.addProperties(
    isArray: Boolean,
    nameRef: ClassName,
    builder: TypeSpec.Builder,
) {
    val knownFields = builder.build().fieldSpecs().map { it.name() }
    val knownSubTypes = builder.build().typeSpecs().map { it.name() }

    value.properties?.forEach { p ->
        val extraStack = if (isArray) arrayOf("properties") else arrayOf("properties", p.key)
        Context.withSchemaStack(*extraStack) {
            p.referenceAndDefinition("", nameRef)?.let { (d, s) ->
                s?.let {
                    if (!knownSubTypes.contains(it.name())) {
                        builder.addType(it.toBuilder().addModifiers(Modifier.STATIC).build())
                    }
                }
                if (!knownFields.contains(p.key.unkeywordize().camelCase())) {
                    builder.addField(
                        FieldSpec.builder(d, p.key.unkeywordize().camelCase(), Modifier.PRIVATE)
                            .addAnnotation(jsonProperty(p.key))
                            .addAnnotation(generated(0))
                            .build(),
                    )
                }
            }
        }
    }
}

private fun Map.Entry<String, Schema<*>>.buildEnum(className: ClassName): TypeSpec {
    val builder =
        TypeSpec.enumBuilder(className.simpleName())
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc(schemaJavadoc())
            .addAnnotation(generated(0))
            .addAnnotation(lombok("Getter"))
            .addAnnotation(lombok("RequiredArgsConstructor"))
            .addAnnotation(lombok("ToString"))
            .addField(
                FieldSpec.builder(String::class.java, "value", Modifier.PRIVATE, Modifier.FINAL)
                    .addAnnotation(jsonValue())
                    .addJavadoc("\$S", "The value of the enum")
                    .build(),
            )
    value.enum
        .map { it?.toString() }
        .forEach {
            val enumValue = it?.unkeywordize()?.trainCase() ?: "NULL"
            val enumName = it ?: "null"
            builder.addEnumConstant(enumValue, TypeSpec.anonymousClassBuilder("\$S", enumName).build())
        }
    return builder.build()
}

private fun Map.Entry<String, Schema<*>>.schemaJavadoc(): String {
    val title = value.title ?: ""
    val description = value.description ?: ""
    val javadoc =
        if (description.contains(title)) {
            MarkdownHelper.mdToHtml(description)
        } else {
            MarkdownHelper.mdToHtml("**$title**\n\n$description")
        }
    return javadoc.replace(Regex("\\$\\{(.+)}"), "\$1")
}