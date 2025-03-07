package codegen

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.TypeSpec
import java.util.stream.Stream

object Annotations {
    /*
     Lombok Annotations
     */
    fun lombok(name: String): AnnotationSpec = AnnotationSpec.builder(ClassName.get("lombok", name)).build()

    /*
     Jackson Annotations
     */
    fun jsonValue(): AnnotationSpec = AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonValue")).build()

    fun jsonProperty(property: String): AnnotationSpec =
        AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonProperty"))
            .addMember("value", "\$S", property)
            .build()

    fun serializerAnnotation(
        className: String,
        serializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonSerialize"))
            .addMember("using", "$className.${serializer.name()}.class")
            .build()

    fun deserializerAnnotation(
        className: String,
        deserializer: TypeSpec,
    ): AnnotationSpec =
        AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonDeserialize"))
            .addMember("using", "$className.${deserializer.name()}.class")
            .build()

    fun jsonFormat(
        shape: String,
        pattern: String? = null,
    ): AnnotationSpec {
        val spec =
            AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonFormat"))
                .addMember("shape", "\$T.$shape", ClassName.get("com.fasterxml.jackson.annotation", "JsonFormat", "Shape"))
        if (pattern != null) {
            spec.addMember("pattern", "\$S", pattern)
        }
        return spec.build()
    }

    fun singleValueAsArray(): AnnotationSpec =
        AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonFormat"))
            .addMember("with", "\$L.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY", ClassName.get("com.fasterxml.jackson.annotation", "JsonFormat"))
            .build()

    fun jsonIncludeNonNull(): AnnotationSpec {
        return AnnotationSpec.builder(ClassName.get("com.fasterxml.jackson.annotation", "JsonInclude"))
            .addMember("value", "\$T.Include.NON_NULL", ClassName.get("com.fasterxml.jackson.annotation", "JsonInclude"))
            .build()
    }

    /*
     GH Annotations
     */
    fun generated(offset: Int): AnnotationSpec =
        AnnotationSpec.builder(ClassName.get("io.github.pulpogato.common", "Generated"))
            .addMember(
                "schemaRef",
                "\$S",
                Context.getSchemaStackRef()
                    .replace("properties/requestBody", "requestBody")
                    .replace(Regex("(oneOf|anyOf|allOf)/properties/.+?(\\d+)"), "$1/$2"),
            )
            .addMember("codeRef", "\$S", codeRef(offset))
            .build()

    /*
     GH Annotations
     */
    fun typeGenerated(): AnnotationSpec =
        AnnotationSpec.builder(ClassName.get("io.github.pulpogato.common", "TypeGenerated"))
            .addMember("codeRef", "\$S", codeRef(0))
            .build()

    private fun codeRef(offset: Int): String {
        val walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        val frames = walker.walk { frames: Stream<StackWalker.StackFrame> -> frames.toList() }
        val frame = frames[2 + offset]
        return "${frame.fileName}:${frame.lineNumber}"
    }
}