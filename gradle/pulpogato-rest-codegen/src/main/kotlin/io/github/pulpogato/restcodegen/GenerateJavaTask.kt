package io.github.pulpogato.restcodegen

import com.diffplug.spotless.glue.pjf.PalantirJavaFormatFormatterFunc
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
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
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

    @get:OutputDirectory
    val testResourcesDir: Provider<File>
        get() = testDir.map { File(it.parentFile, "resources") }

    @TaskAction
    fun generate() {
        mainDir.get().mkdirs()
        val schemaFile = schema.get()
        val packageNamePrefix = packageName.get()
        val main = mainDir.get()
        val test = testDir.get()

        val swaggerSpec = schemaFile.readText()

        // Check for additions.schema.json in the module's resources directory
        val resourcesDir = project.projectDir.resolve("src/main/resources")
        val schemaAddsFile = resourcesDir.resolve("additions.schema.json")
        val addedProperties = mutableMapOf<String, Set<String>>()
        val mergedSpec =
            if (schemaAddsFile.exists()) {
                mergeSchemaAdditions(swaggerSpec, schemaAddsFile.readText(), addedProperties)
            } else {
                swaggerSpec
            }

        val parseOptions = ParseOptions()
        val result = OpenAPIParser().readContents(mergedSpec, listOf(), parseOptions)

        val openAPI = result.openAPI
        val version = projectName.get().replace("pulpogato-rest-", "")

        val context = Context(openAPI, version, emptyList(), addedProperties)
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
        val mapper = ObjectMapper()
        val schemas = mutableMapOf("schema.json" to mapper.readTree(swaggerSpec))

        // Add additions schema if it exists
        if (schemaAddsFile.exists()) {
            val additionsJson = mapper.readTree(schemaAddsFile.readText())
            schemas["additions.schema.json"] = additionsJson
        }

        JsonRefValidator(0).validate(schemas, javaFiles + testJavaFiles)
    }

    private fun getJavaFiles(dir: File): List<File> =
        dir
            .walk()
            .filter { it.isFile }
            .filter { it.toPath().extension == "java" }
            .toList()

    private fun mergeSchemaAdditions(
        swaggerSpec: String,
        schemaAddsJson: String,
        addedProperties: MutableMap<String, Set<String>>,
    ): String {
        val objectMapper = ObjectMapper()
        val schema = objectMapper.readTree(swaggerSpec) as ObjectNode
        val schemaAdds = objectMapper.readTree(schemaAddsJson)

        val additions = schemaAdds.get("components")?.get("schemas")
        if (additions != null && additions.isObject) {
            additions.properties().forEach { (schemaName, schemaAddition) ->
                val properties = schemaAddition.get("properties")
                if (properties != null && properties.isObject) {
                    val targetSchema = schema.at("/components/schemas/$schemaName")
                    if (targetSchema.isMissingNode) {
                        project.logger.warn("Schema '$schemaName' not found in OpenAPI spec, skipping additions")
                    } else {
                        val targetProperties =
                            (targetSchema as ObjectNode)
                                .get("properties") as ObjectNode

                        val propertyNames = mutableSetOf<String>()
                        properties.properties().forEach { (propertyName, propertySpec) ->
                            targetProperties.putIfAbsent(propertyName, propertySpec)
                            propertyNames.add(propertyName)
                            project.logger.info("Added property '$propertyName' to schema '$schemaName'")
                        }
                        addedProperties[schemaName] = propertyNames
                    }
                }
            }
        }

        return objectMapper.writeValueAsString(schema)
    }
}