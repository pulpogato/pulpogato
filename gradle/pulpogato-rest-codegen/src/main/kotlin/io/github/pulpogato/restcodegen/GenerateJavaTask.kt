package io.github.pulpogato.restcodegen

import com.diffplug.spotless.glue.pjf.PalantirJavaFormatFormatterFunc
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.extension
import kotlin.io.readText

@CacheableTask
open class GenerateJavaTask : DefaultTask() {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
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
        val version = projectName.get().replace("pulpogato-rest-", "")

        val context = Context(openAPI, version, emptyList())
        val enumConverters = mutableSetOf<com.palantir.javapoet.ClassName>()
        PathsBuilder().buildApis(context, main, test, "$packageNamePrefix.rest.api", enumConverters)
        WebhooksBuilder().buildWebhooks(context, main, test, "$packageNamePrefix.rest", "$packageNamePrefix.rest.webhooks")
        SchemasBuilder().buildSchemas(context, main, "$packageNamePrefix.rest.schemas", enumConverters)
        EnumConvertersBuilder().buildEnumConverters(context, main, "$packageNamePrefix.rest.api", enumConverters)

        // Format generated Java code
        val javaFiles = getJavaFiles(main)
        val testJavaFiles = getJavaFiles(test)
        val formatter = PalantirJavaFormatFormatterFunc("PALANTIR", true)
        (javaFiles + testJavaFiles).parallelStream().forEach { f ->
            val formatted = formatter.apply(f.readText())
            f.writeText(formatted)
        }

        // Validate JSON references
        val json = ObjectMapper().readTree(swaggerSpec)
        JsonRefValidator(0).validate(json, javaFiles + testJavaFiles)
    }

    private fun getJavaFiles(dir: File): List<File> =
        dir
            .walk()
            .filter { it.isFile }
            .filter { it.toPath().extension == "java" }
            .toList()
}