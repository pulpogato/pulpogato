package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations.generated
import java.io.File
import javax.lang.model.element.Modifier

class EnumConvertersBuilder {
    /**
     * Builds the EnumConverters class containing all enum converter instances.
     *
     * This method creates a Java class named "EnumConverters" that contains a
     * list of all enum converters provided.
     * It also includes a generated annotation for tracking the source.
     *
     * If the enumConverters set is empty, this method returns early without
     * generating any class.
     *
     * @param context The context containing OpenAPI specification and schema information
     * @param mainDir The directory where the generated Java file will be written
     * @param apiPackageName The package name for the generated class
     * @param enumConverters A set of [ClassName] objects representing the enum converter classes to include
     */
    fun buildEnumConverters(
        context: Context,
        mainDir: File,
        apiPackageName: String,
        enumConverters: Set<ClassName>,
    ) {
        if (enumConverters.isEmpty()) {
            return
        }

        val converters = enumConverters.toList().sortedBy { it.toString() }

        // Build the EnumConverters class
        val enumConvertersClass =
            TypeSpec
                .classBuilder("EnumConverters")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(generated(0, context.withSchemaStack("#", "components", "schemas")))
                .addJavadoc($$"$L", "Registry containing all enum converters for Spring Boot configuration.")

        // Add a list field containing all converters
        val converterType =
            ParameterizedTypeName.get(
                ClassName.get("org.springframework.core.convert.converter", "Converter"),
                com.palantir.javapoet.WildcardTypeName
                    .subtypeOf(Object::class.java),
                ClassName.get(String::class.java),
            )

        val listType =
            ParameterizedTypeName.get(
                ClassName.get("java.util", "List"),
                converterType,
            )

        val converterInstances =
            converters.map { converter ->
                com.palantir.javapoet.CodeBlock
                    .of($$"new $T()", converter)
            }

        val convertersField =
            FieldSpec
                .builder(listType, "converters", Modifier.PRIVATE, Modifier.FINAL)
                .initializer(
                    com.palantir.javapoet.CodeBlock
                        .builder()
                        .add($$"$T.of(\n", ClassName.get("java.util", "List"))
                        .add(
                            $$"    $L\n",
                            com.palantir.javapoet.CodeBlock
                                .join(converterInstances, ",\n    "),
                        ).add($$")")
                        .build(),
                ).build()

        enumConvertersClass.addField(convertersField)

        // Add explicit no-args constructor
        enumConvertersClass.addMethod(
            com.palantir.javapoet.MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .build(),
        )

        // Add explicit getConverters() method
        enumConvertersClass.addMethod(
            com.palantir.javapoet.MethodSpec
                .methodBuilder("getConverters")
                .addModifiers(Modifier.PUBLIC)
                .returns(listType)
                .addStatement("return this.converters")
                .build(),
        )

        // Write the class to file
        JavaFile.builder(apiPackageName, enumConvertersClass.build()).build().writeTo(mainDir)
    }
}