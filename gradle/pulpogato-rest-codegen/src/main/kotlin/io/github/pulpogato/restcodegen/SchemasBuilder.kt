package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.JavaFile
import io.github.pulpogato.restcodegen.ext.referenceAndDefinition
import java.io.File

class SchemasBuilder {
    fun buildSchemas(
        context: Context,
        mainDir: File,
        packageName: String,
        enumConverters: MutableSet<ClassName>,
    ) {
        val openAPI = context.openAPI
        openAPI.components.schemas.forEach { entry ->
            val (typeName, definition) =
                referenceAndDefinition(context.withSchemaStack("#", "components", "schemas", entry.key), entry, "", null)!!
            definition?.let {
                JavaFile.builder(packageName, it).build().writeTo(mainDir)

                // If this is an enum, add its converter to the set
                if (it.enumConstants().isNotEmpty() && typeName is ClassName) {
                    val converterClassName = typeName.nestedClass("${typeName.simpleName()}Converter")
                    enumConverters.add(converterClassName)
                }
            }
        }
    }
}