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

@CacheableTask
abstract class DownloadSchemaTask : DefaultTask() {
    @get:Input
    abstract val apiVersion: Property<String>

    @get:Input
    abstract val projectVariant: Property<String>

    @get:OutputFile
    abstract val schemaFile: RegularFileProperty

    @TaskAction
    fun download() {
        val variant = projectVariant.get()
        val version = apiVersion.get()
        val outputFile = schemaFile.get().asFile

        val url = buildSchemaUrl(variant, version)
        logger.info("Downloading schema from: $url")

        outputFile.parentFile.mkdirs()

        val schemaBytes = URI(url).toURL().readBytes()
        outputFile.writeBytes(schemaBytes)

        val sha256 = calculateSha256(schemaBytes)
        project.extensions.extraProperties["github.api.sha256"] = sha256
        logger.info("Downloaded schema with SHA256: $sha256")

        if (!outputFile.exists()) {
            throw GradleException("Failed to download schema from $url")
        }
    }

    private fun buildSchemaUrl(
        variant: String,
        version: String,
    ): String {
        val path = if (variant == "fpt") "api.github.com" else variant
        return "https://github.com/github/rest-api-description/raw/$version/descriptions-next/$path/$path.json"
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}