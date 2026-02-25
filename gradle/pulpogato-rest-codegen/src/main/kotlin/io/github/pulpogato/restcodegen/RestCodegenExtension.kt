package io.github.pulpogato.restcodegen

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring REST API code generation.
 *
 * This extension provides configuration properties for the REST code generation plugin,
 * allowing users to specify input schemas, output directories, package names, and other
 * generation parameters through the Gradle build configuration.
 *
 * @property project The Gradle project this extension is associated with
 */
open class RestCodegenExtension(
    project: Project,
) {
    /**
     * The OpenAPI schema file to use as input for code generation.
     *
     * This property should point to the OpenAPI specification file (in JSON or YAML format)
     * that defines the REST API to be generated.
     */
    var schema: RegularFileProperty = project.objects.fileProperty()

    /**
     * The target Java package name for generated code.
     *
     * All generated classes will be placed in this package and its sub-packages.
     * The package name should follow Java naming conventions.
     */
    var packageName: Property<String> = project.objects.property(String::class.java)

    /**
     * The directory where generated main source files will be written.
     *
     * Generated API interfaces, models, and other main code will be placed in this directory.
     * The directory will be created if it doesn't exist.
     */
    var mainDir: RegularFileProperty = project.objects.fileProperty()

    /**
     * The directory where generated test files will be written.
     *
     * Generated test classes and test resources will be placed in this directory.
     * The directory will be created if it doesn't exist.
     */
    var testDir: RegularFileProperty = project.objects.fileProperty()

    /**
     * The commit hash from which the API is being generated.
     *
     * This property is used for version tracking and may be used in generated code
     * for documentation or version-specific configurations.
     */
    var apiCommit: Property<String> = project.objects.property(String::class.java)

    /**
     * The API Version from GitHub.
     */
    var apiVersion: Property<String> = project.objects.property(String::class.java)

    /**
     * The project variant to use for code generation.
     *
     * This allows for different configurations of code generation within the same project,
     * such as different API versions or feature sets.
     */
    var projectVariant: Property<String> = project.objects.property(String::class.java)

    /**
     * The GitHub repository path for the REST API description schemas.
     *
     * This property specifies the repository path (e.g., `"github/rest-api-description"`)
     * from which OpenAPI schemas will be downloaded.
     */
    var apiRepository: Property<String> = project.objects.property(String::class.java)

    /**
     * The project version for the headers properties file.
     *
     * This property specifies the version to write to the headers file.
     */
    var projectVersion: Property<String> = project.objects.property(String::class.java)
}