package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.TypeSpec
import java.util.stream.Stream

object Annotations {
    /*
     Lombok Annotations
     */
    fun lombok(name: String): AnnotationSpec = AnnotationSpec.builder(ClassName.get("lombok", name)).build()

    fun superBuilder(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("lombok.experimental", "SuperBuilder"))
            .addMember("toBuilder", "true")
            .build()

    /*
     Jackson Annotations
     */
    fun jsonValue(): AnnotationSpec = AnnotationSpec.builder(ClassName.get(JsonValue::class.java)).build()

    fun jsonProperty(property: String): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonProperty::class.java))
            .addMember("value", "\$S", property)
            .build()

    fun serializerAnnotation(
        className: String,
        serializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonSerialize::class.java))
            .addMember("using", "$className.${serializer.name()}.class")
            .build()

    fun deserializerAnnotation(
        className: String,
        deserializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonDeserialize::class.java))
            .addMember("using", "$className.${deserializer.name()}.class")
            .build()

    fun jsonFormat(
        shape: JsonFormat.Shape,
        pattern: String? = null,
    ): AnnotationSpec {
        val spec =
            AnnotationSpec
                .builder(ClassName.get(JsonFormat::class.java))
                .addMember("shape", "\$T.\$L", JsonFormat.Shape::class.java, shape)
        if (pattern != null) {
            spec.addMember("pattern", "\$S", pattern)
        }
        return spec.build()
    }

    fun singleValueAsArray(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonFormat::class.java))
            .addMember("with", "\$T.\$L", ClassName.get(JsonFormat.Feature::class.java), JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build()

    fun jsonIncludeNonNull(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(JsonInclude::class.java))
            .addMember("value", "\$T.\$L", ClassName.get(JsonInclude.Include::class.java), JsonInclude.Include.NON_NULL)
            .build()

    /*
     GH Annotations
     */
    fun generated(
        offset: Int,
        context: Context,
    ): AnnotationSpec {
        val builder =
            AnnotationSpec
                .builder(ClassName.get("io.github.pulpogato.common", "Generated"))
                .addMember("ghVersion", "\$S", context.version)
        val schemaRef = context.getSchemaStackRef()
        if (schemaRef.isNotEmpty()) {
            builder.addMember("schemaRef", "\$S", schemaRef)
        } else {
            throw IllegalArgumentException("SchemaRef is empty")
        }
        return builder
            .addMember("codeRef", "\$S", codeRef(offset))
            .build()
    }

    fun typeGenerated(): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get("io.github.pulpogato.common", "TypeGenerated"))
            .addMember("codeRef", "\$S", codeRef(0))
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
}