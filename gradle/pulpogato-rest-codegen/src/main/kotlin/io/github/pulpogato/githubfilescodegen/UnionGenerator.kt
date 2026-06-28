package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations
import io.github.pulpogato.restcodegen.MarkdownHelper
import io.github.pulpogato.restcodegen.Types
import javax.lang.model.element.Modifier

object UnionGenerator {
    private val LOMBOK_DATA = ClassName.get("lombok", "Data")
    private val LOMBOK_BUILDER = ClassName.get("lombok", "Builder")
    private val LOMBOK_NO_ARGS = ClassName.get("lombok", "NoArgsConstructor")
    private val LOMBOK_ALL_ARGS = ClassName.get("lombok", "AllArgsConstructor")

    private val MODE = ClassName.get(Types.COMMON_PACKAGE, "Mode")

    private fun fancyDeser(jacksonVersion: Int) = ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}LenientFancyDeserializer")

    private fun fancySer(jacksonVersion: Int) = ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}FancySerializer")

    private fun settableField() = ClassName.get("${Types.COMMON_PACKAGE}.jackson", "LenientFancyDeserializerSupport", "SettableField")

    private fun gettableField(jacksonVersion: Int) =
        ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}FancySerializer", "GettableField")

    /**
     * Generates a union wrapper class with lenient Jackson deserializers and standard serializers.
     *
     * @param className The class name for the union type
     * @param variants List of (fieldName, typeName) pairs for each variant
     * @param description Optional Javadoc
     * @param mode ONE_OF or ANY_OF
     * @return A TypeSpec for the union class
     */
    fun generate(
        className: String,
        variants: List<VariantSpec>,
        description: String?,
        mode: String = "ONE_OF",
        schemaRef: String,
        sourceFile: String,
    ): TypeSpec {
        val thisClass = ClassName.bestGuess(className)

        val deserializer3 = buildSerde(3, thisClass, variants, mode, deserializer = true)
        val serializer3 = buildSerde(3, thisClass, variants, mode, deserializer = false)
        val deserializer2 = buildSerde(2, thisClass, variants, mode, deserializer = true)
        val serializer2 = buildSerde(2, thisClass, variants, mode, deserializer = false)

        val builder =
            TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Annotations.generatedForGithubFiles(schemaRef, sourceFile))
                .addAnnotation(AnnotationSpec.builder(LOMBOK_DATA).build())
                .addAnnotation(AnnotationSpec.builder(LOMBOK_BUILDER).build())
                .addAnnotation(AnnotationSpec.builder(LOMBOK_NO_ARGS).build())
                .addAnnotation(AnnotationSpec.builder(LOMBOK_ALL_ARGS).build())
                .addAnnotation(Annotations.deserializerAnnotationForJackson3(className, deserializer3))
                .addAnnotation(Annotations.serializerAnnotationForJackson3(className, serializer3))
                .addAnnotation(Annotations.deserializerAnnotationForJackson2(className, deserializer2))
                .addAnnotation(Annotations.serializerAnnotationForJackson2(className, serializer2))

        if (!description.isNullOrBlank()) {
            builder.addJavadoc($$"$L", MarkdownHelper.mdToHtml(description))
        }

        // One field per variant
        variants.forEach { variant ->
            builder.addField(
                FieldSpec
                    .builder(variant.typeName, variant.fieldName, Modifier.PRIVATE)
                    .addAnnotation(Annotations.generatedForGithubFiles(variant.schemaRef, sourceFile))
                    .build(),
            )
        }

        builder.addType(deserializer3)
        builder.addType(serializer3)
        builder.addType(deserializer2)
        builder.addType(serializer2)

        return builder.build()
    }

    /**
     * Builds a nested Jackson deserializer or serializer for the union class. The two share the same
     * shape; they differ only in the superclass, the constructor's accessor wiring, and the fact that
     * deserializers also receive a `::new` factory so they can construct the union.
     */
    private fun buildSerde(
        jacksonVersion: Int,
        thisClass: ClassName,
        variants: List<VariantSpec>,
        mode: String,
        deserializer: Boolean,
    ): TypeSpec {
        val suffix = if (deserializer) "Deserializer" else "Serializer"
        val typeName = "${thisClass.simpleName()}Jackson$jacksonVersion$suffix"
        val superClass =
            ParameterizedTypeName.get(
                if (deserializer) fancyDeser(jacksonVersion) else fancySer(jacksonVersion),
                thisClass,
            )

        val formatParts = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (deserializer) {
            formatParts.add($$"super($T.class, $T::new, $T.$L, $T.of(")
            args.add(thisClass)
            args.add(thisClass)
        } else {
            formatParts.add($$"super($T.class, $T.$L, $T.of(")
            args.add(thisClass)
        }
        args.add(MODE)
        args.add(mode)
        args.add(ClassName.get("java.util", "List"))

        variants.forEachIndexed { index, v ->
            if (index > 0) {
                formatParts.add(", ")
            }
            val accessor = (if (deserializer) "set" else "get") + v.fieldName.replaceFirstChar { it.uppercaseChar() }
            formatParts.add($$"new $T<>($T.class, $T::$$accessor)")
            args.add(if (deserializer) settableField() else gettableField(jacksonVersion))
            args.add(rawType(v.typeName))
            args.add(thisClass)
        }

        formatParts.add("))")

        val constructorBuilder =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement(formatParts.joinToString(""), *args.toTypedArray())

        return TypeSpec
            .classBuilder(typeName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .superclass(superClass)
            .addMethod(constructorBuilder.build())
            .build()
    }

    /**
     * Extracts the raw type from a TypeName, for use in `.class` literals
     * where parameterized types are not allowed.
     */
    private fun rawType(typeName: TypeName): TypeName =
        when (typeName) {
            is ParameterizedTypeName -> typeName.rawType()
            else -> typeName
        }

    data class VariantSpec(
        val fieldName: String,
        val typeName: TypeName,
        val schemaRef: String,
    )
}