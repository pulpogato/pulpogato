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

    private fun fancyDeser(jacksonVersion: Int) = ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}FancyDeserializer")

    private fun fancySer(jacksonVersion: Int) = ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}FancySerializer")

    private fun settableField(jacksonVersion: Int) =
        ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}FancyDeserializer", "SettableField")

    private fun gettableField(jacksonVersion: Int) =
        ClassName.get("${Types.COMMON_PACKAGE}.jackson", "Jackson${jacksonVersion}FancySerializer", "GettableField")

    /**
     * Generates a union wrapper class with Jackson2FancyDeserializer/Serializer.
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
    ): TypeSpec {
        val thisClass = ClassName.bestGuess(className)

        val deserializer3 = buildDeserializer(3, thisClass, variants, mode)
        val serializer3 = buildSerializer(3, thisClass, variants, mode)
        val deserializer2 = buildDeserializer(2, thisClass, variants, mode)
        val serializer2 = buildSerializer(2, thisClass, variants, mode)

        val builder =
            TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
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
                    .build(),
            )
        }

        builder.addType(deserializer3)
        builder.addType(serializer3)
        builder.addType(deserializer2)
        builder.addType(serializer2)

        return builder.build()
    }

    private fun buildDeserializer(
        jacksonVersion: Int,
        thisClass: ClassName,
        variants: List<VariantSpec>,
        mode: String,
    ): TypeSpec {
        val deserializerName = "${thisClass.simpleName()}Jackson${jacksonVersion}Deserializer"
        val superClass = ParameterizedTypeName.get(fancyDeser(jacksonVersion), thisClass)

        val constructorBuilder =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)

        val formatParts = mutableListOf<String>()
        val args = mutableListOf<Any>()

        formatParts.add($$"super($T.class, $T::new, $T.$L, $T.of(")
        args.add(thisClass)
        args.add(thisClass)
        args.add(MODE)
        args.add(mode)
        args.add(ClassName.get(java.util.List::class.java))

        variants.forEachIndexed { index, v ->
            if (index > 0) {
                formatParts.add(", ")
            }
            formatParts.add($$"new $T<>($T.class, $T::set$${v.fieldName.replaceFirstChar { it.uppercaseChar() }})")
            args.add(settableField(jacksonVersion))
            args.add(rawType(v.typeName))
            args.add(thisClass)
        }

        formatParts.add("))")

        constructorBuilder.addStatement(formatParts.joinToString(""), *args.toTypedArray())

        return TypeSpec
            .classBuilder(deserializerName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .superclass(superClass)
            .addMethod(constructorBuilder.build())
            .build()
    }

    private fun buildSerializer(
        jacksonVersion: Int,
        thisClass: ClassName,
        variants: List<VariantSpec>,
        mode: String,
    ): TypeSpec {
        val serializerName = "${thisClass.simpleName()}Jackson${jacksonVersion}Serializer"
        val superClass = ParameterizedTypeName.get(fancySer(jacksonVersion), thisClass)

        val constructorBuilder =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)

        val formatParts = mutableListOf<String>()
        val args = mutableListOf<Any>()

        formatParts.add($$"super($T.class, $T.$L, $T.of(")
        args.add(thisClass)
        args.add(MODE)
        args.add(mode)
        args.add(ClassName.get(java.util.List::class.java))

        variants.forEachIndexed { index, v ->
            if (index > 0) {
                formatParts.add(", ")
            }
            formatParts.add($$"new $T<>($T.class, $T::get$${v.fieldName.replaceFirstChar { it.uppercaseChar() }})")
            args.add(gettableField(jacksonVersion))
            args.add(rawType(v.typeName))
            args.add(thisClass)
        }

        formatParts.add("))")

        constructorBuilder.addStatement(formatParts.joinToString(""), *args.toTypedArray())

        return TypeSpec
            .classBuilder(serializerName)
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
    )
}