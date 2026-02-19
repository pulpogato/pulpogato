package io.github.pulpogato.issues

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle plugin that registers tasks for managing GitHub issues related to ignored tests.
 * This plugin registers two tasks: one to check the status of existing issues and another to create new issues.
 */
@Suppress("unused")
class IssueStatusesPlugin : Plugin<Project> {
    /**
     * Applies the plugin to the target project by registering the necessary tasks.
     *
     * @param target The Gradle project to apply the plugin to
     */
    override fun apply(target: Project) {
        target.tasks.register("checkIssueStatuses", CheckIssueStatusesTask::class.java) {
            inputFile.set(target.file("src/main/resources/IgnoredTests.yml"))
            schemaFiles.from(
                target.rootProject.fileTree(target.rootProject.projectDir) {
                    include("pulpogato-common/src/main/resources/**/*.schema.json")
                    include("pulpogato-rest-*/src/main/resources/**/*.schema.json")
                },
            )
            group = "verification"
            description = "Checks the status of ignored tests issues."
        }
        target.tasks.register("createIssues", CreateIssuesTask::class.java) {
            group = "verification"
            description = "Creates issues for ignored tests."
        }
    }
}