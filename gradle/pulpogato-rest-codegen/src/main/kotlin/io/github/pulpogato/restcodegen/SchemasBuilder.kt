package io.github.pulpogato.restcodegen

import com.palantir.javapoet.JavaFile
import io.github.pulpogato.restcodegen.ext.referenceAndDefinition
import java.io.File

class SchemasBuilder {
    fun buildSchemas(
        outputDir: File,
        packageName: String,
    ) {
        val openAPI = Context.instance.get().openAPI
        openAPI.components.schemas.forEach { entry ->
            val (_, definition) =
                Context.withSchemaStack("#", "components", "schemas", entry.key) {
                    referenceAndDefinition(entry, "", null)!!
                }
            definition?.let {
                JavaFile.builder(packageName, it).build().writeTo(outputDir)
            }
        }
    }
}