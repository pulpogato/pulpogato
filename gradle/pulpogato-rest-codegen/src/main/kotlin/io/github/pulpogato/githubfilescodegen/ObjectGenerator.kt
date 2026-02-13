package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations
import io.github.pulpogato.restcodegen.MarkdownHelper
import javax.lang.model.element.Modifier

object ObjectGenerator {
    private val LOMBOK_DATA = ClassName.get("lombok", "Data")
    private val LOMBOK_BUILDER = ClassName.get("lombok", "Builder")
    private val LOMBOK_NO_ARGS = ClassName.get("lombok", "NoArgsConstructor")
    private val LOMBOK_ALL_ARGS = ClassName.get("lombok", "AllArgsConstructor")

    /**
     * Generates a Lombok `@Data` POJO from a list of property specifications.
     *
     * @param name The class name
     * @param properties List of (jsonPropertyName, typeName, description) triples
     * @param description Optional Javadoc for the class
     * @return A TypeSpec for the class
     */
    fun generate(
        name: String,
        properties: List<PropertySpec>,
        description: String?,
        schemaRef: String,
        sourceFile: String,
    ): TypeSpec {
        val nullsAsEmpty = name == "GithubWorkflowOnVariant2"
        val builder =
            TypeSpec
                .classBuilder(name)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Annotations.generatedForGithubFiles(schemaRef, sourceFile))
                .addAnnotation(AnnotationSpec.builder(LOMBOK_DATA).build())
                .addAnnotation(AnnotationSpec.builder(LOMBOK_BUILDER).build())
                .addAnnotation(AnnotationSpec.builder(LOMBOK_NO_ARGS).build())
                .addAnnotation(AnnotationSpec.builder(LOMBOK_ALL_ARGS).build())
                .addAnnotation(Annotations.jsonIncludeNonNull())

        if (!description.isNullOrBlank()) {
            builder.addJavadoc("\$L", MarkdownHelper.mdToHtml(description))
        }

        properties.forEach { prop ->
            val fieldName = prop.jsonName.toSafeFieldName()
            val fieldBuilder =
                FieldSpec
                    .builder(prop.typeName, fieldName, Modifier.PRIVATE)
                    .addAnnotation(Annotations.generatedForGithubFiles(prop.schemaRef, sourceFile))
                    .addAnnotation(Annotations.jsonProperty(prop.jsonName))
            if (nullsAsEmpty) {
                fieldBuilder.addAnnotation(Annotations.jsonSetterNullsAsEmpty())
            }

            if (!prop.description.isNullOrBlank()) {
                fieldBuilder.addJavadoc("\$L", MarkdownHelper.mdToHtml(prop.description))
            }

            builder.addField(fieldBuilder.build())
        }

        return builder.build()
    }

    data class PropertySpec(
        val jsonName: String,
        val typeName: TypeName,
        val description: String?,
        val schemaRef: String,
    )
}