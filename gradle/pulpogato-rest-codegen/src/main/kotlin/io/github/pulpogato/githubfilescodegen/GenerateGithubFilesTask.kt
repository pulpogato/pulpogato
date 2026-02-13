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
        if (outDir.exists()) {
            outDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "java" }
                .forEach { it.delete() }
        }
        outDir.mkdirs()

        val mapper = ObjectMapper()
        val mapping = schemaPackageMapping.get()
        val basePackage = packageName.get()
        val schemas = mutableMapOf<String, ObjectNode>()

        schemaFiles.forEach { schemaFile ->
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

            // Pre-register all definitions to break cycles
            val definitions = rootSchema.get("definitions")
            if (definitions != null && definitions.isObject) {
                definitions.properties().forEach { (defName, _) ->
                    val className = ClassName.get(targetPackage, defName.pascalCase())
                    ctx.definitionRegistry[defName] = className
                }
            }

            // First pass: resolve all definitions to populate type aliases and generated types
            if (definitions != null && definitions.isObject) {
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

            // Second pass: resolve the root type (will use cached definitions)
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

            // Write all generated types
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

        // Format generated Java code
        val javaFiles = collectJavaFiles(outDir)
        formatJavaFiles(javaFiles, logger, continueOnError = true)

        // Validate all @Generated schema references against their source schema files.
        JsonRefValidator(0).validate(schemas, javaFiles)

        logger.lifecycle("Generated ${javaFiles.size} Java files")
    }
}