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

    /**
     * Output directory for generated Java sources.
     *
     * Defaults to a "codegen-src" dir rather than "generated-src" because Sonar's Gradle plugin
     * unconditionally drops any sonar.sources/sonar.tests entry whose path contains "build/generated".
     */
    val outputDir: Property<String> =
        project.objects.property(String::class.java).convention(
            project.layout.buildDirectory
                .dir("codegen-src/main/java")
                .map { it.asFile.absolutePath },
        )

    /**
     * Mapping from schema filename to sub-package name.
     * E.g., "github-action.json" → "actions"
     */
    val schemaPackageMapping: MapProperty<String, String> =
        project.objects.mapProperty(String::class.java, String::class.java)
}