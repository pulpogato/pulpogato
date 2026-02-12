package io.github.pulpogato.githubfilescodegen

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class GithubFilesCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("githubFilesCodegen", GithubFilesCodegenExtension::class.java, target)

        val generateTask = target.tasks.register("generateGithubFilesTypes", GenerateGithubFilesTask::class.java)
        generateTask.configure {
            schemaFiles.from(extension.schemaFiles)
            packageName.set(extension.packageName)
            outputDir.set(extension.outputDir)
            schemaPackageMapping.set(extension.schemaPackageMapping)
        }
    }
}