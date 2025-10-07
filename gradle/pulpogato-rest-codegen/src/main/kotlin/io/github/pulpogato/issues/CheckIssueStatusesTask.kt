package io.github.pulpogato.issues

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CheckIssueStatusesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @TaskAction
    fun checkStatuses() {
        val file = inputFile.get().asFile
        val statuses = getIssueStatuses(file)
        val notOpen = statuses.count { it.state != "OPEN" }

        println("Checking issue statuses in $file")
        println(statuses.groupBy { it.state }.mapValues { it.value.size })
        statuses
            .filter { it.state != "OPEN" }
            .forEach { println(it) }
        if (notOpen > 0) {
            throw GradleException("$notOpen issues report as not open. Please check $file")
        }
    }

    private fun getIssueStatuses(file: File): List<IssueStatus> {
        val regex = ".+\"(https://github.com/github/rest-api-description/issues/\\d+)\"".toRegex()
        val objectMapper = ObjectMapper()
        return file
            .readLines()
            .asSequence()
            .filter { it.matches(regex) }
            .mapNotNull { regex.matchEntire(it)?.groupValues?.get(1) }
            .distinct()
            .map { ProcessBuilder("gh", "issue", "view", it, "--json", "state,url,number").start() }
            .onEach { it.waitFor() }
            .mapNotNull { objectMapper.readValue(it.inputReader(), IssueStatus::class.java) }
            .toList()
    }

    internal class IssueStatus {
        var number: Int? = null
        var state: String? = null
        var url: String? = null

        override fun toString(): String = "IssueStatus(number=$number, state=$state, url=$url)"
    }
}