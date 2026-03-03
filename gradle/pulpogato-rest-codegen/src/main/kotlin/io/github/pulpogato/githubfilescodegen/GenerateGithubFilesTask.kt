package io.github.pulpogato.githubfilescodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.JavaFile
import io.github.pulpogato.restcodegen.JsonRefValidator
import io.github.pulpogato.restcodegen.collectJavaFiles
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.github.pulpogato.restcodegen.formatJavaFiles
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.io.File

/**
 * Gradle task that generates Java types from JSON Schema files.
 */
@CacheableTask
open class GenerateGithubFilesTask : DefaultTask() {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val schemaFiles: ConfigurableFileCollection = project.objects.fileCollection()

    @Input
    val packageName: Property<String> = project.objects.property(String::class.java)

    @OutputDirectory
    val outputDir: Property<String> = project.objects.property(String::class.java)

    @Input
    val schemaPackageMapping: MapProperty<String, String> =
        project.objects.mapProperty(String::class.java, String::class.java)

    @TaskAction
    fun generate() {
        val outDir = File(outputDir.get())
        prepareOutputDir(outDir)

        val mapper = ObjectMapper()
        val mapping = schemaPackageMapping.get()
        val basePackage = packageName.get()
        val schemas = mutableMapOf<String, ObjectNode>()

        schemaFiles.forEach { schemaFile ->
            processSchemaFile(schemaFile, mapper, basePackage, mapping, schemas, outDir)
        }

        formatAndValidate(schemas, outDir)
    }

    private fun prepareOutputDir(outDir: File) {
        if (outDir.exists()) {
            outDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .forEach { it.delete() }
        }
        outDir.mkdirs()
    }

    private fun processSchemaFile(
        schemaFile: File,
        mapper: ObjectMapper,
        basePackage: String,
        mapping: Map<String, String>,
        schemas: MutableMap<String, ObjectNode>,
        outDir: File,
    ) {
        val subPackage = mapping[schemaFile.name] ?: schemaFile.nameWithoutExtension.pascalCase().lowercase()
        val targetPackage = "$basePackage.$subPackage"

        logger.lifecycle("Generating types from ${schemaFile.name} into $targetPackage")

        val rootSchema = mapper.readTree(schemaFile) as ObjectNode
        schemas[schemaFile.name] = rootSchema
        val ctx =
            JsonSchemaContext(
                rootSchema = rootSchema,
                sourceFile = schemaFile.name,
                schemaStack = listOf("#"),
            )

        val definitions = rootSchema.get("definitions")
        preRegisterDefinitions(ctx, definitions, targetPackage)
        resolveDefinitions(ctx, definitions, targetPackage)
        resolveRootType(ctx, schemaFile, rootSchema, targetPackage)
        writeGeneratedTypes(ctx, outDir, targetPackage)
    }

    /** Pre-registers all definitions to break cycles before resolving types. */
    private fun preRegisterDefinitions(
        ctx: JsonSchemaContext,
        definitions: JsonNode?,
        targetPackage: String,
    ) {
        if (definitions == null || !definitions.isObject) return
        definitions.properties().forEach { (defName, _) ->
            val className = ClassName.get(targetPackage, defName.pascalCase())
            ctx.definitionRegistry[defName] = className
        }
    }

    /** First pass: resolves all definitions to populate type aliases and generated types. */
    private fun resolveDefinitions(
        ctx: JsonSchemaContext,
        definitions: JsonNode?,
        targetPackage: String,
    ) {
        if (definitions == null || !definitions.isObject) return
        definitions.properties().forEach { (defName, _) ->
            if (defName !in ctx.resolvedDefinitions) {
                JsonSchemaTypeResolver.resolveRef(
                    ctx.withSchemaStack("#", "definitions", defName),
                    "#/definitions/$defName",
                    targetPackage,
                )
            }
        }
    }

    /** Second pass: resolves the root type, using the cached definitions. */
    private fun resolveRootType(
        ctx: JsonSchemaContext,
        schemaFile: File,
        rootSchema: ObjectNode,
        targetPackage: String,
    ) {
        val rootClassName = schemaFile.nameWithoutExtension.pascalCase()
        val rootResolved =
            JsonSchemaTypeResolver.resolveType(
                ctx.withSchemaStack("#"),
                rootClassName,
                rootSchema,
                targetPackage,
            )
        if (rootResolved.typeSpec != null) {
            val rootClass = ClassName.get(targetPackage, rootClassName)
            ctx.generatedTypes[rootClass.toString()] = rootResolved.typeSpec
        }
    }

    private fun writeGeneratedTypes(
        ctx: JsonSchemaContext,
        outDir: File,
        targetPackage: String,
    ) {
        ctx.generatedTypes.forEach { (fqn, typeSpec) ->
            val lastDot = fqn.lastIndexOf('.')
            val pkg = if (lastDot > 0) fqn.substring(0, lastDot) else targetPackage
            val javaFile =
                JavaFile
                    .builder(pkg, typeSpec)
                    .build()
            javaFile.writeTo(outDir)
        }
    }

    private fun formatAndValidate(
        schemas: Map<String, ObjectNode>,
        outDir: File,
    ) {
        val javaFiles = collectJavaFiles(outDir)
        formatJavaFiles(javaFiles, logger, continueOnError = true)
        JsonRefValidator(0).validate(schemas, javaFiles)
        logger.lifecycle("Generated ${javaFiles.size} Java files")
    }
}