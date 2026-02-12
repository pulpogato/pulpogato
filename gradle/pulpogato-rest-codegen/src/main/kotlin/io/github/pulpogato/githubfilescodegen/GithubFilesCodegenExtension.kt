package io.github.pulpogato.githubfilescodegen

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring github-files JSON Schema code generation.
 */
open class GithubFilesCodegenExtension(
    project: Project,
) {
    /** JSON Schema files to process. */
    val schemaFiles: ConfigurableFileCollection = project.objects.fileCollection()

    /** Base Java package name for generated types. */
    val packageName: Property<String> = project.objects.property(String::class.java)

    /** Output directory for generated Java sources. */
    val outputDir: Property<String> =
        project.objects.property(String::class.java).convention(
            project.layout.buildDirectory
                .dir("generated-src/main/java")
                .map { it.asFile.absolutePath },
        )

    /**
     * Mapping from schema filename to sub-package name.
     * E.g., "github-action.json" → "actions"
     */
    val schemaPackageMapping: MapProperty<String, String> =
        project.objects.mapProperty(String::class.java, String::class.java)
}