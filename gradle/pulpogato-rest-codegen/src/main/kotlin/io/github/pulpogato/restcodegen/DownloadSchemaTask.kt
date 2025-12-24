package io.github.pulpogato.restcodegen

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.security.MessageDigest

/**
 * A Gradle task to download OpenAPI schema files from a remote source.
 *
 * This task downloads schema files from the GitHub REST API description repository
 * based on the specified API version and project variant. It also calculates and
 * stores the SHA-256 hash of the downloaded schema for verification purposes.
 */
@CacheableTask
abstract class DownloadSchemaTask : DefaultTask() {
    /**
     * The commit from which API schema is downloaded.
     *
     * This property specifies which version of the API schema to retrieve.
     */
    @get:Input
    abstract val apiCommit: Property<String>

    /**
     * The API version to download schema for.
     *
     * This property specifies which version of the API schema to retrieve.
     */
    @get:Input
    abstract val apiVersion: Property<String>

    /**
     * The project variant to download schema for.
     *
     * This property determines which specific variant of the API to use,
     * with special handling for the "fpt" (free, pro, team) variant.
     */
    @get:Input
    abstract val projectVariant: Property<String>

    /**
     * The GitHub repository path for the REST API description schemas.
     *
     * This property specifies the repository path (e.g., `"github/rest-api-description"`)
     * from which OpenAPI schemas will be downloaded.
     */
    @get:Input
    abstract val apiRepository: Property<String>

    /**
     * The output file where the downloaded schema will be saved.
     *
     * This property specifies the destination file for the downloaded schema.
     */
    @get:OutputFile
    abstract val schemaFile: RegularFileProperty

    /**
     * Downloads the schema file from the remote URL.
     *
     * This is the main action method that performs the schema download.
     * It builds the appropriate URL based on the variant and version,
     * downloads the schema, saves it to the output file, calculates
     * the SHA-256 hash, and stores it as a project property.
     *
     * @throws GradleException if the download fails or the output file doesn't exist after download
     */
    @TaskAction
    fun download() {
        val variant = projectVariant.get()
        val commit = apiCommit.get()
        val apiVersion = apiVersion.get()
        val outputFile = schemaFile.get().asFile

        val url = buildSchemaUrl(variant, commit, apiVersion)
        logger.info("Downloading schema from: $url")

        outputFile.parentFile.mkdirs()

        val schemaBytes = URI(url).toURL().readBytes()
        outputFile.writeBytes(schemaBytes)

        // Write the headers properties file alongside the schema
        val propertiesFile = outputFile.parentFile.resolve("pulpogato-headers.properties")
        val projectVersion = project.version.toString()
        propertiesFile.writeText(
            """# Pulpogato Headers Properties
pulpogato.version=$projectVersion
github.api.version=$apiVersion
""",
        )

        val sha256 = calculateSha256(schemaBytes)
        project.extensions.extraProperties["github.api.sha256"] = sha256
        logger.info("Downloaded schema with SHA256: $sha256")

        if (!outputFile.exists()) {
            throw GradleException("Failed to download schema from $url")
        }
    }

    /**
     * Builds the schema URL based on the given variant and version.
     *
     * For the "fpt" variant, the path is set to `"api.github.com"`,
     * otherwise the variant itself is used as the path.
     *
     * @param variant The project variant (e.g., "fpt", or other variant names)
     * @param commit The API version to download
     * @return The complete URL for downloading the schema
     */
    private fun buildSchemaUrl(
        variant: String,
        commit: String,
        apiVersion: String,
    ): String {
        val path = if (variant == "fpt") "api.github.com" else variant
        val repo = apiRepository.get()
        return "https://github.com/$repo/raw/$commit/descriptions-next/$path/$path.$apiVersion.json"
    }

    /**
     * Calculates the SHA-256 hash of the given byte array.
     *
     * This method is used to generate a hash of the downloaded schema
     * file for verification and tracking purposes.
     *
     * @param bytes The byte array to calculate hash for
     * @return The SHA-256 hash as a hexadecimal string
     */
    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}