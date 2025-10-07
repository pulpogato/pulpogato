package io.github.pulpogato.issues

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class IssueStatusesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("checkIssueStatuses", CheckIssueStatusesTask::class.java) {
            inputFile.set(target.file("src/main/resources/IgnoredTests.yml"))
            group = "verification"
            description = "Checks the status of ignored tests issues."
        }
    }
}