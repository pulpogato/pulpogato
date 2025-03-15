package io.github.pulpogato.restcodegen

import io.github.pulpogato.restcodegen.ext.referenceAndDefinition
import com.palantir.javapoet.JavaFile
import io.swagger.v3.oas.models.OpenAPI
import java.io.File

object SchemasBuilder {
    fun buildSchemas(
        openAPI: OpenAPI,
        outputDir: File,
        packageName: String,
    ) {
        openAPI.components.schemas.forEach { entry ->
            val (_, definition) =
                Context.withSchemaStack("#", "components", "schemas", entry.key) {
                    entry.referenceAndDefinition("", null)!!
                }
            definition?.let {
                JavaFile.builder(packageName, it).build().writeTo(outputDir)
            }
        }
    }
}