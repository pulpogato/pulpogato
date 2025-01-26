package codegen

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import java.io.File

class Main {
    fun process(
        schema: File,
        mainDir: File,
        packageName: String,
        testDir: File,
    ) {
        val parseOptions = ParseOptions()

        val swaggerSpec = schema.readText()

        val result = OpenAPIParser().readContents(swaggerSpec, listOf(), parseOptions)
        val openAPI = result.openAPI
        Context.instance.get().openAPI = openAPI
        PathsBuilder.buildApis(openAPI, mainDir, "$packageName.rest.api", testDir)
        WebhooksBuilder.buildWebhooks(openAPI, mainDir, "$packageName.rest", "$packageName.rest.webhooks", testDir)
        SchemasBuilder.buildSchemas(openAPI, mainDir, "$packageName.rest.schemas")

        val json = ObjectMapper().readTree(swaggerSpec)
        JsonRefValidator.validate(
            json,
            listOf(
                mainDir,
                testDir,
            ),
        )
    }
}