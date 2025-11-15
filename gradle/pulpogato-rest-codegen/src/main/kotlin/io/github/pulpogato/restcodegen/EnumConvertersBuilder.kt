package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.lombok
import java.io.File
import javax.lang.model.element.Modifier

class EnumConvertersBuilder {
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
                .addAnnotation(lombok("Getter"))
                .addAnnotation(lombok("RequiredArgsConstructor"))
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
                        ).add(")")
                        .build(),
                ).build()

        enumConvertersClass.addField(convertersField)

        // Write the class to file
        JavaFile.builder(apiPackageName, enumConvertersClass.build()).build().writeTo(mainDir)
    }
}