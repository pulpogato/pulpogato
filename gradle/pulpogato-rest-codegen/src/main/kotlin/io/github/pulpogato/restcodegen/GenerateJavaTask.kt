package io.github.pulpogato.restcodegen

import com.diffplug.spotless.glue.pjf.PalantirJavaFormatFormatterFunc
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.extension
import kotlin.io.readText

open class GenerateJavaTask : DefaultTask() {
    @InputFile
    lateinit var schema: Provider<File>

    @Input
    lateinit var packageName: Provider<String>

    @OutputDirectory
    lateinit var mainDir: Provider<File>

    @OutputDirectory
    lateinit var testDir: Provider<File>

    @Input
    val projectName: Property<String> = project.objects.property(String::class.java)

    init {
        projectName.set(project.name)
    }

    @TaskAction
    fun generate() {
        mainDir.get().mkdirs()
        val schemaFile = schema.get()
        val packageNamePrefix = packageName.get()
        val main = mainDir.get()
        val test = testDir.get()

        val swaggerSpec = schemaFile.readText()

        val parseOptions = ParseOptions()
        val result = OpenAPIParser().readContents(swaggerSpec, listOf(), parseOptions)

        val openAPI = result.openAPI
        Context.instance.get().openAPI = openAPI
        Context.instance.get().version = projectName.get().replace("pulpogato-rest-", "")
        PathsBuilder().buildApis(main, "$packageNamePrefix.rest.api", test)
        WebhooksBuilder().buildWebhooks(main, "$packageNamePrefix.rest", "$packageNamePrefix.rest.webhooks", test)
        SchemasBuilder().buildSchemas(main, "$packageNamePrefix.rest.schemas")

        // Format generated Java code
        val javaFiles = getJavaFiles(main)
        val testJavaFiles = getJavaFiles(test)
        val formatter = PalantirJavaFormatFormatterFunc("PALANTIR", true)
        (javaFiles + testJavaFiles).forEach { f ->
            val formatted = formatter.apply(f.readText())
            f.writeText(formatted)
        }

        // Validate JSON references
        val json = ObjectMapper().readTree(swaggerSpec)
        JsonRefValidator(142).validate(json, listOf(main, test))
    }

    private fun getJavaFiles(dir: File): List<File> {
        return dir.walk()
            .filter { it.isFile }
            .filter { it.toPath().extension == "java" }
            .toList()
    }
}