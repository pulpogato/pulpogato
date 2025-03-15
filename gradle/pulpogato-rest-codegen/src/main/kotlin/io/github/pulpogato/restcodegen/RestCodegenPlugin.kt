package io.github.pulpogato.restcodegen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.provider.DefaultProvider

@Suppress("unused")
class RestCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create("codegen", RestCodegenExtension::class.java, target)
        val generateJava = target.tasks.register("generateJava", GenerateJavaTask::class.java)
        generateJava.configure {
            schema = DefaultProvider { -> extension.schema.get().asFile }
            packageName = DefaultProvider { -> extension.packageName.get() }
            mainDir = DefaultProvider { -> extension.mainDir.get().asFile }
            testDir = DefaultProvider { -> extension.testDir.get().asFile }
        }
    }
}