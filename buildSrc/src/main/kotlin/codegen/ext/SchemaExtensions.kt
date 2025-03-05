package codegen.ext

import codegen.Annotations.allArgsConstructor
import codegen.Annotations.deserializerAnnotation
import codegen.Annotations.generated
import codegen.Annotations.getter
import codegen.Annotations.jsonProperty
import codegen.Annotations.jsonValue
import codegen.Annotations.lombokBuilder
import codegen.Annotations.noArgsConstructor
import codegen.Annotations.requiredArgsConstructor
import codegen.Annotations.serializerAnnotation
import codegen.Annotations.setter
import codegen.Annotations.singleValueAsArray
import codegen.Annotations.typeGenerated
import codegen.Context
import codegen.MarkdownHelper
import codegen.Types
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
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
        value.`$ref` != null -> {
            val schemaName = value.`$ref`.replace("#/components/schemas/", "")
            val entries = Context.instance.get().openAPI.components.schemas.filter { (k, _) -> k == schemaName }.entries
            val schema = entries.first()
            schema.referenceAndDefinition("", null)!!.copy(second = null)
        }

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
                "string" ->
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

                "integer" ->
                    when (value.format) {
                        "int32" -> Pair(Types.INTEGER.annotated(typeGenerated()), null)
                        "timestamp" -> Pair(Types.EPOCH_TIME.annotated(typeGenerated()), null)
                        else -> Pair(Types.LONG.annotated(typeGenerated()), null)
                    }

                "boolean" -> Pair(Types.BOOLEAN, null)
                "number" ->
                    when (value.format) {
                        "double" -> Pair(Types.DOUBLE.annotated(typeGenerated()), null)
                        "float" -> Pair(Types.FLOAT.annotated(typeGenerated()), null)
                        else -> Pair(Types.BIG_DECIMAL.annotated(typeGenerated()), null)
                    }

                "array" ->
                    Context.withSchemaStack("items") { mapOf(key to value.items).entries.first().referenceAndDefinition("", parentClass, isArray = true) }
                        ?.let {
                            val oldTypeGenerated = it.first.annotations()
                                .filter { spec -> (spec.type() as ClassName).simpleName() == "TypeGenerated" }
                            val otherAnnotations = it.first.annotations()
                                .filter { spec -> (spec.type() as ClassName).simpleName() != "TypeGenerated" }

                            Pair(
                                ParameterizedTypeName.get(Types.LIST,
                                    it.first.withoutAnnotations()
                                        .annotated(oldTypeGenerated))
                                    .annotated(otherAnnotations.distinct()).annotated(typeGenerated()),
                                it.second
                            )
                        }

                "object" ->
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

                else -> throw RuntimeException("Unknown type for $key, stack: ${Context.getSchemaStackRef()}")
            }

        types.toSet() == setOf("string", "integer") -> Pair(Types.STRING_OR_INTEGER, null)
        types.toSet() == setOf("string", "object") -> Pair(Types.STRING_OR_OBJECT, null)
        types.toSet() == setOf("string", "object", "integer") -> Pair(Types.STRING_OBJECT_OR_INTEGER, null)
        else -> Pair(Types.TODO, null)
    }
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
                .addAnnotation(getter())
                .addAnnotation(setter())
                .addAnnotation(generated(0))
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
                        val fieldSpec =
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
                        theType.addField(fieldSpec)
                        if (fieldSpec.type() is ParameterizedTypeName) {
                            fields.add(Pair((fieldSpec.type() as ParameterizedTypeName).rawType(), fieldSpec.name().pascalCase()))
                        } else {
                            fields.add(Pair(fieldSpec.type(), fieldSpec.name().pascalCase()))
                        }
                    }

                }
            }

        val settableFields =
            fields.map { (type, name) ->
                CodeBlock.of(
                    "new \$T<>(\$T.class, \$T::set$name)",
                    ClassName.get("io.github.pulpogato.common", "FancyDeserializer", "SettableField"),
                    type.withoutAnnotations(),
                    ClassName.get("", className),
                )
            }

        val gettableFields =
            fields.map { (type, name) ->
                CodeBlock.of(
                    "new \$T<>(\$T.class, \$T::get$name)",
                    ClassName.get("io.github.pulpogato.common", "FancySerializer", "GettableField"),
                    type.withoutAnnotations(),
                    ClassName.get("", className),
                )
            }

        val deserializer =
            TypeSpec.classBuilder("${className}Deserializer")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ParameterizedTypeName.get(ClassName.get("io.github.pulpogato.common", "FancyDeserializer"), ClassName.get("", className)))
                .addMethod(
                    MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement(
                            """super(${"$"}T.class, ${"$"}T::new, ${"$"}T.$type, ${"$"}T.of(
                        |    ${"$"}L
                        |))
                            """.trimMargin(),
                            ClassName.get("", className),
                            ClassName.get("", className),
                            ClassName.get("io.github.pulpogato.common", "Mode"),
                            ClassName.get("java.util", "List"),
                            CodeBlock.join(settableFields, ",\n    "),
                        )
                        .build(),
                )
                .build()

        val serializer =
            TypeSpec.classBuilder("${className}Serializer")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .superclass(ParameterizedTypeName.get(ClassName.get("io.github.pulpogato.common", "FancySerializer"), ClassName.get("", className)))
                .addMethod(
                    MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addStatement(
                            """super(${"$"}T.class, ${"$"}T.$type, ${"$"}T.of(
                        |    ${"$"}L
                        |))
                            """.trimMargin(),
                            ClassName.get("", className),
                            ClassName.get("io.github.pulpogato.common", "Mode"),
                            ClassName.get("java.util", "List"),
                            CodeBlock.join(gettableFields, ",\n    "),
                        )
                        .build(),
                )
                .build()

        theType.addType(deserializer)
            .addType(serializer)
            .addAnnotation(deserializerAnnotation(className, deserializer))
            .addAnnotation(serializerAnnotation(className, serializer))
            .addAnnotation(lombokBuilder())
            .addAnnotation(noArgsConstructor())
            .addAnnotation(allArgsConstructor())

        theType.build()
    }
}

private fun Map.Entry<String, Schema<*>>.buildSimpleObject(
    isArray: Boolean,
    nameRef: ClassName,
): TypeSpec {
    val name = nameRef.simpleName()

    val builder =
        TypeSpec.classBuilder(name)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(getter())
            .addAnnotation(setter())
            .addAnnotation(generated(0))
            .addAnnotation(lombokBuilder())
            .addAnnotation(noArgsConstructor())
            .addAnnotation(allArgsConstructor())

    addProperties(isArray, nameRef, builder)

    return builder.build()
}

private fun Map.Entry<String, Schema<*>>.addProperties(isArray: Boolean, nameRef: ClassName, builder: TypeSpec.Builder) {
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
            .addAnnotation(getter())
            .addAnnotation(generated(0))
            .addAnnotation(requiredArgsConstructor())
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