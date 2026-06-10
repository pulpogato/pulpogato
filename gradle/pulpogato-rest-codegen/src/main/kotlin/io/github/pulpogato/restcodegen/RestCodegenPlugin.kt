package io.github.pulpogato.restcodegen

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class RestCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("codegen", RestCodegenExtension::class.java, target)

        val downloadSchema = target.tasks.register("downloadSchema", DownloadSchemaTask::class.java)
        downloadSchema.configure {
            apiCommit.set(extension.apiCommit)
            apiVersion.set(extension.apiVersion)
            projectVariant.set(extension.projectVariant)
            apiRepository.set(extension.apiRepository)
            projectVersion.set(extension.projectVersion)
            schemaFile.set(target.layout.buildDirectory.file("generated-src/main/resources/github.schema.json"))
        }

        val generateJava = target.tasks.register("generateJava", GenerateJavaTask::class.java)
        generateJava.configure {
            dependsOn(downloadSchema)
            schema = downloadSchema.flatMap { it.schemaFile }.map { it.asFile }
            packageName = target.provider { extension.packageName.get() }
            mainDir = target.provider { extension.mainDir.get().asFile }
            testDir = target.provider { extension.testDir.get().asFile }
            testResourcesDir =
                target.provider {
                    extension.testDir
                        .get()
                        .asFile.parentFile
                        .resolve("resources")
                }
            rootProjectDir.set(target.rootProject.projectDir)
            projectDir.set(target.projectDir)
            commonResourcesDir.set(
                target.provider {
                    val dir = target.rootProject.projectDir.resolve("pulpogato-common/src/main/resources")
                    if (dir.exists()) dir else null
                },
            )
            moduleResourcesDir.set(
                target.provider {
                    val dir = target.projectDir.resolve("src/main/resources")
                    if (dir.exists()) dir else null
                },
            )
        }
    }
}