package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations
import io.github.pulpogato.restcodegen.Types
import io.github.pulpogato.restcodegen.ext.trainCase
import javax.lang.model.element.Modifier

object EnumGenerator {
    /**
     * Generates a Java enum from a JSON Schema `enum` array.
     *
     * @param name The enum class name
     * @param values The list of enum string values
     * @param description Optional Javadoc description
     * @return A TypeSpec for the enum
     */
    fun generate(
        name: String,
        values: List<String>,
        description: String?,
        schemaRef: String,
        sourceFile: String,
    ): TypeSpec {
        val builder =
            TypeSpec
                .enumBuilder(name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Annotations.generatedForGithubFiles(schemaRef, sourceFile))

        if (!description.isNullOrBlank()) {
            builder.addJavadoc($$"$L", description)
        }

        // Private value field
        builder.addField(
            FieldSpec
                .builder(Types.STRING, "value", Modifier.PRIVATE, Modifier.FINAL)
                .addAnnotation(Annotations.generatedForGithubFiles(schemaRef, sourceFile))
                .addAnnotation(Annotations.jsonValue())
                .build(),
        )

        // Constructor
        builder.addMethod(
            MethodSpec
                .constructorBuilder()
                .addParameter(Types.STRING, "value")
                .addStatement("this.value = value")
                .build(),
        )

        // Enum constants
        values.forEach { value ->
            val constantName = value.trainCase()
            builder.addEnumConstant(
                constantName,
                TypeSpec
                    .anonymousClassBuilder($$"$S", value)
                    .build(),
            )
        }

        // Static lookup method
        builder.addMethod(
            MethodSpec
                .methodBuilder("forValue")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Types.STRING, "value")
                .returns(ClassName.bestGuess(name))
                .beginControlFlow("for (var e : values())")
                .beginControlFlow("if (e.value.equals(value))")
                .addStatement("return e")
                .endControlFlow()
                .endControlFlow()
                .addStatement($$"throw new $T($S + value)", ClassName.get(IllegalArgumentException::class.java), "Unknown value: ")
                .build(),
        )

        return builder.build()
    }
}