package io.github.pulpogato.issues

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import tools.jackson.databind.ObjectMapper
import java.io.File

/**
 * Gradle task to check the status of GitHub issues related to ignored tests.
 * This task reads an input file containing GitHub issue URLs, fetches their current status
 * from GitHub, and verifies that all issues are still in the OPEN state.
 *
 * If any issues are found to be closed or not open, the task will throw a GradleException.
 */
abstract class CheckIssueStatusesTask : DefaultTask() {
    /**
     * The input file containing GitHub issue URLs to check.
     * This file should contain lines with GitHub issue URLs that need to be checked for status.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    /**
     * Schema files to scan for issue URLs.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaFiles: ConfigurableFileCollection

    /**
     * Executes the task to check GitHub issues statuses.
     * Reads the input file, fetches issue statuses from GitHub, and verifies that all issues are still open.
     * If any issues are not open, this method will throw a GradleException.
     *
     * @throws GradleException if any of the checked issues are not in OPEN state
     */
    @TaskAction
    fun checkStatuses() {
        val file = inputFile.get().asFile
        val statuses = getIssueStatuses(file, schemaFiles.files)
        val notOpen = statuses.count { it.state != "OPEN" }

        println("Checking issue statuses in $file")
        println("Scanning ${schemaFiles.files.size} schema files for issue references")
        println(statuses.groupBy { it.state }.mapValues { it.value.size })
        statuses
            .filter { it.state != "OPEN" }
            .forEach { println(it) }
        if (notOpen > 0) {
            throw GradleException("$notOpen issues report as not open. Please check $file")
        }
    }

    /**
     * Fetches issue statuses from GitHub for the issue URLs found in the input file.
     * Extracts GitHub issue URLs from the input file using a regex pattern, then uses
     * the GitHub CLI to fetch the status of each issue.
     *
     * @param file The input file containing GitHub issue URLs
     * @param schemaFiles Schema files to scan for additional GitHub issue URLs
     * @return A list of IssueStatus objects representing the current status of each issue
     */
    private fun getIssueStatuses(
        file: File,
        schemaFiles: Set<File>,
    ): List<IssueStatus> {
        val regex = "https://github.com/github/rest-api-description/issues/\\d+".toRegex()
        val objectMapper = ObjectMapper()
        val urls =
            sequenceOf(file)
                .plus(schemaFiles.asSequence())
                .map { it.readText() }
                .flatMap { regex.findAll(it).map { match -> match.value } }
                .distinct()
                .toList()

        return urls
            .asSequence()
            .map { url -> Pair(url, ProcessBuilder("gh", "issue", "view", url, "--json", "state,url,number").start()) }
            .mapNotNull { (url, process) ->
                if (process.waitFor() == 0) {
                    val jsonOutput = process.inputReader().readText()
                    if (jsonOutput.isNotBlank()) {
                        objectMapper.readValue(jsonOutput, IssueStatus::class.java)
                    } else {
                        IssueStatus(number = -1, state = "MISSING", url = url)
                    }
                } else {
                    IssueStatus(number = -1, state = "MISSING", url = url)
                }
            }.toList()
    }
}