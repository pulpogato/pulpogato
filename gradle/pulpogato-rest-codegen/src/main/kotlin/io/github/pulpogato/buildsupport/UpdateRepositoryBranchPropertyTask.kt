package io.github.pulpogato.buildsupport

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI

@UntrackedTask(because = "Queries a remote branch tip and updates a tracked properties file.")
abstract class UpdateRepositoryBranchPropertyTask : DefaultTask() {
    @get:Input
    abstract val repository: Property<String>

    @get:Input
    abstract val branch: Property<String>

    @get:Input
    abstract val propertyName: Property<String>

    @get:OutputFile
    abstract val propertiesFile: RegularFileProperty

    @get:Internal
    abstract val gitHubToken: Property<String>

    @TaskAction
    fun updateProperty() {
        val repo = repository.get()
        val branchName = branch.get()
        val key = propertyName.get()
        val targetFile = propertiesFile.get().asFile

        val connection =
            "https://api.github.com/repos/$repo/branches/$branchName"
                .let(::URI)
                .toURL()
                .openConnection() as HttpURLConnection

        gitHubToken.orNull?.takeIf { it.isNotBlank() }?.let { token ->
            connection.addRequestProperty("Authorization", "token $token")
        }

        val branchJson = connection.inputStream.bufferedReader().use { it.readText() }
        val sha =
            ObjectMapper()
                .readTree(branchJson)["commit"]["sha"]
                .stringValue()
                .orEmpty()
                .take(7)
        val originalContents = targetFile.readText()
        val updatedContents =
            if (Regex("^$key=.*$", RegexOption.MULTILINE).containsMatchIn(originalContents)) {
                originalContents.replace(Regex("^$key=.*$", RegexOption.MULTILINE), "$key=$sha")
            } else {
                originalContents.trimEnd() + "\n$key=$sha\n"
            }

        targetFile.writeText(updatedContents)

        val previousValue =
            Regex("^$key=(.*)$", RegexOption.MULTILINE)
                .find(originalContents)
                ?.groupValues
                ?.get(1)

        if (previousValue == sha) {
            logger.lifecycle("$key is already up to date")
        } else if (previousValue == null) {
            logger.lifecycle("Added $key=$sha")
        } else {
            logger.lifecycle("Updated $key from $previousValue to $sha")
        }
    }
}