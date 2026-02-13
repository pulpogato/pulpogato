package io.github.pulpogato.restcodegen

import com.palantir.javaformat.java.Formatter
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
 * for APIs, webhooks, and schemas. It supports schema additions through optional
 * `*.schema.json` files and formats the generated code using Palantir Java Format.
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

        // Check for *.schema.json in pulpogato-common and the module's resources directory
        val commonResourcesDir = project.rootProject.projectDir.resolve("pulpogato-common/src/main/resources")
        val moduleResourcesDir = project.projectDir.resolve("src/main/resources")
        val schemaAdditionsFiles = findSchemaFiles(commonResourcesDir) + findSchemaFiles(moduleResourcesDir)

        val addedProperties = mutableMapOf<String, MutableMap<String, String>>()
        var mergedSpec = swaggerSpec
        schemaAdditionsFiles.forEach { schemaAddsFile ->
            mergedSpec = mergeSchemaAdditions(mergedSpec, schemaAddsFile.readText(), schemaAddsFile.name, addedProperties)
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
        val formatter = Formatter.create()
        try {
            (javaFiles + testJavaFiles).parallelStream().forEach { f ->
                val formatted = formatter.formatSource(f.readText())
                f.writeText(formatted)
            }
        } catch (e: IllegalAccessError) {
            logger.warn("Failed to format Java files: ${e.message}")
        }

        // Validate JSON references using the merged spec (includes additions)
        val mapper = ObjectMapper()
        val schemas = mutableMapOf("schema.json" to mapper.readTree(mergedSpec))

        // Add additions schemas if they exist (for refs that explicitly reference them)
        schemaAdditionsFiles.forEach { schemaAddsFile ->
            val additionsJson = mapper.readTree(schemaAddsFile.readText())
            schemas[schemaAddsFile.name] = additionsJson
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
     * Finds all schema addition files (*.schema.json) in the specified directory.
     *
     * @param dir The directory to search for schema files
     * @return A list of schema files sorted by name, or empty list if directory doesn't exist
     */
    private fun findSchemaFiles(dir: File): List<File> =
        if (dir.exists()) {
            dir
                .listFiles { _, name -> name.endsWith(".schema.json") }
                ?.sortedBy { it.name }
                ?.toList() ?: emptyList()
        } else {
            emptyList()
        }

    /**
     * Merges schema additions from an addition file into the main schema.
     *
     * This method looks for additional schema definitions in the additions file
     * and merges them into the main OpenAPI specification. It supports both:
     * - Adding new properties to existing schemas
     * - Adding entirely new schemas that don't exist in the main spec
     *
     * It also tracks which additional properties were added so they can be
     * properly handled during code generation.
     *
     * @param swaggerSpec The original OpenAPI specification as a JSON string
     * @param schemaAddsJson The additions schema as a JSON string
     * @param addedProperties A mutable map to store information about which properties were added from the additions
     * @return The merged OpenAPI specification as a JSON string
     */
    private fun mergeSchemaAdditions(
        swaggerSpec: String,
        schemaAddsJson: String,
        sourceFileName: String,
        addedProperties: MutableMap<String, MutableMap<String, String>>,
    ): String {
        val objectMapper = ObjectMapper()
        val schema = objectMapper.readTree(swaggerSpec) as ObjectNode
        val schemaAdds = objectMapper.readTree(schemaAddsJson)

        val additions = schemaAdds["components"]?.get("schemas")
        if (additions != null && additions.isObject) {
            additions.properties().forEach { (schemaName, schemaAddition) ->
                val targetSchema = schema.at("/components/schemas/$schemaName")
                if (targetSchema.isMissingNode) {
                    // Schema doesn't exist - add it as a new schema
                    val schemasNode = schema.at("/components/schemas") as ObjectNode
                    schemasNode.set(schemaName, schemaAddition)
                    project.logger.info("Added new schema '$schemaName' from '$sourceFileName'")
                } else {
                    // Schema exists - merge properties
                    val properties = schemaAddition["properties"]
                    if (properties != null && properties.isObject) {
                        val targetProperties =
                            (targetSchema as ObjectNode)["properties"] as ObjectNode

                        val propertyNames = addedProperties.getOrPut(schemaName) { mutableMapOf() }
                        properties.properties().forEach { (propertyName, propertySpec) ->
                            targetProperties.putIfAbsent(propertyName, propertySpec)
                            propertyNames[propertyName] = sourceFileName
                            project.logger.info("Added property '$propertyName' to schema '$schemaName' from '$sourceFileName'")
                        }
                    }
                }
            }
        }

        return objectMapper.writeValueAsString(schema)
    }
}