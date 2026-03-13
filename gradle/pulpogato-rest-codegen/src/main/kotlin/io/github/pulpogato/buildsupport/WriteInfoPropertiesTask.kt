package io.github.pulpogato.buildsupport

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

@CacheableTask
abstract class WriteInfoPropertiesTask : DefaultTask() {
    @get:Input
    abstract val staticEntries: MapProperty<String, String>

    @get:Input
    abstract val checksumEntriesByFilename: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val checksumFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeProperties() {
        val staticValues = staticEntries.getOrElse(emptyMap())
        val checksumKeys = checksumEntriesByFilename.getOrElse(emptyMap())
        val checksumValues =
            checksumFiles.files
                .sortedBy { it.name }
                .associate { file ->
                    val propertyName =
                        checksumKeys[file.name]
                            ?: error("No manifest property configured for checksum file '${file.name}'")
                    propertyName to calculateSha256(file.readBytes())
                }

        val serialized =
            (staticValues + checksumValues)
                .toSortedMap()
                .entries
                .joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" }

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(serialized)
        }
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}