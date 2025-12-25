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

/**
 * Gradle task to generate Java classes from OpenAPI schema.
 *
 * This task reads an OpenAPI schema file, processes it to generate Java classes
 * for APIs, webhooks, and schemas. It supports schema additions through an optional
 * additions.schema.json file and formats the generated code using Palantir Java Format.
 * It also validates JSON references in the generated code.
 */
@CacheableTask
open class GenerateJavaTask : DefaultTask() {
    /**
     * The OpenAPI schema file to generate Java classes from.
     *
     * This should be a JSON or YAML file containing the OpenAPI specification.
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    lateinit var schema: Provider<File>

    /**
     * The base package name for the generated Java classes.
     *
     * Generated classes will be placed in sub-packages under this base package.
     */
    @Input
    lateinit var packageName: Provider<String>

    /**
     * The main source directory where generated Java files will be placed.
     *
     * This is typically the 'src/main/java' directory of the project.
     */
    @OutputDirectory
    lateinit var mainDir: Provider<File>

    /**
     * The test source directory where generated test Java files will be placed.
     *
     * This is typically the 'src/test/java' directory of the project.
     */
    @OutputDirectory
    lateinit var testDir: Provider<File>

    /**
     * The name of the project for version determination.
     *
     * This is automatically set to the project name and used to determine
     * the version string for the generated code.
     */
    @Input
    val projectName: Property<String> = project.objects.property(String::class.java)

    init {
        projectName.set(project.name)
    }

    /**
     * Executes the generation of Java classes from the OpenAPI schema.
     *
     * This method performs the following steps:
     * 1. Reads and parses the OpenAPI schema
     * 2. Checks for and merges optional schema additions
     * 3. Generates API classes, webhook classes, and schema classes
     * 4. Creates an enum converters registry
     * 5. Formats the generated Java code
     * 6. Validates JSON references in the generated code
     */
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

    /**
     * Retrieves all Java files in the specified directory and its subdirectories.
     *
     * @param dir The directory to search for Java files
     * @return A list of all Java files found in the directory and its subdirectories
     */
    private fun getJavaFiles(dir: File): List<File> =
        dir
            .walk()
            .filter { it.isFile }
            .filter { it.toPath().extension == "java" }
            .toList()

    /**
     * Merges schema additions from an additions.schema.json file into the main schema.
     *
     * This method looks for additional schema definitions in the additions file
     * and merges them into the main OpenAPI specification. It also tracks which
     * additional properties were added so they can be properly handled during
     * code generation.
     *
     * @param swaggerSpec The original OpenAPI specification as a JSON string
     * @param schemaAddsJson The additions schema as a JSON string
     * @param addedProperties A mutable map to store information about which properties were added from the additions
     * @return The merged OpenAPI specification as a JSON string
     */
    private fun mergeSchemaAdditions(
        swaggerSpec: String,
        schemaAddsJson: String,
        addedProperties: MutableMap<String, Set<String>>,
    ): String {
        val objectMapper = ObjectMapper()
        val schema = objectMapper.readTree(swaggerSpec) as ObjectNode
        val schemaAdds = objectMapper.readTree(schemaAddsJson)

        val additions = schemaAdds["components"]?.get("schemas")
        if (additions != null && additions.isObject) {
            additions.properties().forEach { (schemaName, schemaAddition) ->
                val properties = schemaAddition["properties"]
                if (properties != null && properties.isObject) {
                    val targetSchema = schema.at("/components/schemas/$schemaName")
                    if (targetSchema.isMissingNode) {
                        project.logger.warn("Schema '$schemaName' not found in OpenAPI spec, skipping additions")
                    } else {
                        val targetProperties =
                            (targetSchema as ObjectNode)["properties"] as ObjectNode

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