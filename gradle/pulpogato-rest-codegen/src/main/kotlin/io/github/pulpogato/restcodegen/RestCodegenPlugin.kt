package io.github.pulpogato.restcodegen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.provider.DefaultProvider

@Suppress("unused")
class RestCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("codegen", RestCodegenExtension::class.java, target)

        val downloadSchema = target.tasks.register("downloadSchema", DownloadSchemaTask::class.java)
        downloadSchema.configure {
            apiVersion.set(extension.apiVersion)
            projectVariant.set(extension.projectVariant)
            schemaFile.set(target.layout.buildDirectory.file("generated/resources/main/schema.json"))
        }

        val generateJava = target.tasks.register("generateJava", GenerateJavaTask::class.java)
        generateJava.configure {
            dependsOn(downloadSchema)
            schema =
                DefaultProvider {
                    downloadSchema
                        .get()
                        .schemaFile
                        .get()
                        .asFile
                }
            packageName = DefaultProvider { extension.packageName.get() }
            mainDir = DefaultProvider { extension.mainDir.get().asFile }
            testDir = DefaultProvider { extension.testDir.get().asFile }
        }
    }
}