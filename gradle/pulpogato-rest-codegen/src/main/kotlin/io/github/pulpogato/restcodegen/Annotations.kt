package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.Nulls
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Types.COMMON_PACKAGE
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import java.util.stream.Stream

object Annotations {
    /*
     Jackson Annotations
     */
    fun jsonValue(): AnnotationSpec = AnnotationSpec.builder(ClassName.get(JsonValue::class.java)).build()

    fun jsonProperty(property: String): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonProperty::class.java))
            .addMember("value", $$"$S", property)
            .build()

    fun jsonSetterNullsAsEmpty(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonSetter::class.java))
            .addMember("nulls", $$"$T.$L", ClassName.get(Nulls::class.java), Nulls.AS_EMPTY.name)
            .build()

    fun serializerAnnotationForJackson3(
        className: String,
        serializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonSerialize::class.java))
            .addMember("using", "$className.${serializer.name()}.class")
            .build()

    fun deserializerAnnotationForJackson3(
        className: String,
        deserializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonDeserialize::class.java))
            .addMember("using", "$className.${deserializer.name()}.class")
            .build()

    fun serializerAnnotationForJackson2(
        className: String,
        serializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(com.fasterxml.jackson.databind.annotation.JsonSerialize::class.java))
            .addMember("using", "$className.${serializer.name()}.class")
            .build()

    fun deserializerAnnotationForJackson2(
        className: String,
        deserializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(com.fasterxml.jackson.databind.annotation.JsonDeserialize::class.java))
            .addMember("using", "$className.${deserializer.name()}.class")
            .build()

    fun jsonFormat(
        shape: JsonFormat.Shape,
        pattern: String? = null,
    ): AnnotationSpec {
        val spec =
            AnnotationSpec
                .builder(ClassName.get(JsonFormat::class.java))
                .addMember("shape", $$"$T.$L", JsonFormat.Shape::class.java, shape)
        if (pattern != null) {
            spec.addMember("pattern", $$"$S", pattern)
        }
        return spec.build()
    }

    fun singleValueAsArray(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonFormat::class.java))
            .addMember("with", $$"$T.$L", ClassName.get(JsonFormat.Feature::class.java), JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build()

    fun jsonIncludeNonNull(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonInclude::class.java))
            .addMember("value", $$"$T.$L", ClassName.get(JsonInclude.Include::class.java), JsonInclude.Include.NON_NULL)
            .build()

    fun jsonIncludeAlways(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonInclude::class.java))
            .addMember("value", $$"$T.$L", ClassName.get(JsonInclude.Include::class.java), JsonInclude.Include.ALWAYS)
            .build()

    fun jsonIncludeNonEmpty(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonInclude::class.java))
            .addMember("value", $$"$T.$L", ClassName.get(JsonInclude.Include::class.java), JsonInclude.Include.NON_EMPTY)
            .build()

    fun nullableOptionalSerializer(): List<AnnotationSpec> =
        nullableOptionalJacksonAnnotationPair(
            JsonSerialize::class.java,
            com.fasterxml.jackson.databind.annotation.JsonSerialize::class.java,
            "Serializer",
        )

    fun nullableOptionalDeserializer(): List<AnnotationSpec> =
        nullableOptionalJacksonAnnotationPair(
            JsonDeserialize::class.java,
            com.fasterxml.jackson.databind.annotation.JsonDeserialize::class.java,
            "Deserializer",
        )

    private fun nullableOptionalJacksonAnnotationPair(
        jackson3Annotation: Class<out Annotation>,
        jackson2Annotation: Class<out Annotation>,
        suffix: String,
    ): List<AnnotationSpec> =
        listOf(
            AnnotationSpec
                .builder(ClassName.get(jackson3Annotation))
                .addMember("using", $$"$T.class", ClassName.get("$COMMON_PACKAGE.jackson", "NullableOptionalJackson3$suffix"))
                .build(),
            AnnotationSpec
                .builder(ClassName.get(jackson2Annotation))
                .addMember("using", $$"$T.class", ClassName.get("$COMMON_PACKAGE.jackson", "NullableOptionalJackson2$suffix"))
                .build(),
        )

    /*
     GH Annotations
     */
    fun generated(
        offset: Int,
        context: Context,
        sourceFile: String = "schema.json",
    ): AnnotationSpec {
        val builder =
            AnnotationSpec
                .builder(ClassName.get("$COMMON_PACKAGE.annotations", "Generated"))
                .addMember("ghVersion", $$"$S", context.version)
        val schemaRef = context.getSchemaStackRef()
        if (schemaRef.isNotEmpty()) {
            builder.addMember("schemaRef", $$"$S", schemaRef)
        } else {
            throw IllegalArgumentException("SchemaRef is empty")
        }
        builder.addMember("codeRef", $$"$S", codeRef(offset))
        if (sourceFile != "schema.json") {
            builder.addMember("sourceFile", $$"$S", sourceFile)
        }
        return builder.build()
    }

    fun typeGenerated(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("$COMMON_PACKAGE.annotations", "TypeGenerated"))
            .addMember("codeRef", $$"$S", codeRef(0))
            .build()

    fun generatedForGithubFiles(
        schemaRef: String,
        sourceFile: String,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("$COMMON_PACKAGE.annotations", "Generated"))
            .addMember("schemaRef", $$"$S", schemaRef)
            .addMember("codeRef", $$"$S", codeRef(0))
            .addMember("sourceFile", $$"$S", sourceFile)
            .build()

    private fun codeRef(offset: Int): String {
        val walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        val frames = walker.walk { frames: Stream<StackWalker.StackFrame> -> frames.toList() }
        val frame = frames[2 + offset]
        return "${frame.fileName}:${frame.lineNumber}"
    }

    /*
     Test Annotations
     */
    fun testExtension(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("io.github.pulpogato.test", "CompositeTestExtension"))
            .build()

    /*
     JSpecify Annotations
     */
    fun nonNull(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("org.jspecify.annotations", "NonNull"))
            .build()

    fun nullable(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("org.jspecify.annotations", "Nullable"))
            .build()

    fun deprecated(since: String): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("java.lang", "Deprecated"))
            .addMember("forRemoval", "false")
            .addMember("since", $$"$S", since)
            .build()
}