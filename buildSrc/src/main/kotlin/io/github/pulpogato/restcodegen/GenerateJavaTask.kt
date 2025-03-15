package io.github.pulpogato.restcodegen

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class GenerateJavaTask : DefaultTask() {
    @InputFile lateinit var schema: Provider<File>
    @Input lateinit var packageName: Provider<String>
    @OutputDirectory lateinit var mainDir: Provider<File>
    @OutputDirectory lateinit var testDir: Provider<File>

    @TaskAction
    fun generate() {
        project.file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen").mkdirs()
        Main().process(schema.get(), mainDir.get(), packageName.get(), testDir.get())
    }
}
