package io.github.pulpogato.githubfilescodegen

import com.palantir.javaformat.java.Formatter
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.JavaFile
import io.github.pulpogato.restcodegen.ext.pascalCase
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
import kotlin.io.path.extension

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
        outDir.mkdirs()

        val mapper = ObjectMapper()
        val mapping = schemaPackageMapping.get()
        val basePackage = packageName.get()

        schemaFiles.forEach { schemaFile ->
            val subPackage = mapping[schemaFile.name] ?: schemaFile.nameWithoutExtension.pascalCase().lowercase()
            val targetPackage = "$basePackage.$subPackage"

            logger.lifecycle("Generating types from ${schemaFile.name} into $targetPackage")

            val rootSchema = mapper.readTree(schemaFile) as ObjectNode
            val ctx =
                JsonSchemaContext(
                    rootSchema = rootSchema,
                    packageName = targetPackage,
                    schemaPath = schemaFile.absolutePath,
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
                            ctx,
                            "#/definitions/$defName",
                            targetPackage,
                        )
                    }
                }
            }

            // Second pass: resolve the root type (will use cached definitions)
            val rootClassName = schemaFile.nameWithoutExtension.pascalCase()
            val rootResolved = JsonSchemaTypeResolver.resolveType(ctx, rootClassName, rootSchema, targetPackage)

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
        val formatter = Formatter.create()
        val javaFiles =
            outDir
                .walk()
                .filter { it.isFile }
                .filter { it.toPath().extension == "java" }
                .toList()

        javaFiles.parallelStream().forEach { f ->
            try {
                val formatted = formatter.formatSource(f.readText())
                f.writeText(formatted)
            } catch (e: Exception) {
                logger.warn("Failed to format ${f.name}: ${e.message}")
            }
        }

        logger.lifecycle("Generated ${javaFiles.size} Java files")
    }
}