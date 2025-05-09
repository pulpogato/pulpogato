package io.github.pulpogato.restcodegen

import com.palantir.javapoet.JavaFile
import io.github.pulpogato.restcodegen.ext.referenceAndDefinition
import java.io.File

class SchemasBuilder {
    fun buildSchemas(
        context: Context,
        outputDir: File,
        packageName: String,
    ) {
        val openAPI = context.openAPI
        openAPI.components.schemas.forEach { entry ->
            val (_, definition) =
                referenceAndDefinition(context.withSchemaStack("#", "components", "schemas", entry.key), entry, "", null)!!
            definition?.let {
                JavaFile.builder(packageName, it).build().writeTo(outputDir)
            }
        }
    }
}