package io.github.pulpogato.restcodegen

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Keeps release metadata independently cacheable from the immutable API schema. */
@CacheableTask
abstract class GenerateHeadersTask : DefaultTask() {
    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val apiVersion: Property<String>

    @get:OutputFile
    abstract val headersFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val output = headersFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """# Pulpogato Headers Properties
pulpogato.version=${projectVersion.get()}
github.api.version=${apiVersion.get()}
""",
        )
    }
}