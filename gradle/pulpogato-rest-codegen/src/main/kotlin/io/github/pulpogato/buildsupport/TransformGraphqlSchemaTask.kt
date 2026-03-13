package io.github.pulpogato.buildsupport

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class TransformGraphqlSchemaTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputSchema: RegularFileProperty

    @get:OutputFile
    abstract val outputSchema: RegularFileProperty

    @TaskAction
    fun transform() {
        val source = inputSchema.get().asFile
        val target = outputSchema.get().asFile
        val transformed =
            source
                .readLines()
                .joinToString(separator = "\n") { currentLine ->
                    currentLine
                        .replace(Regex("<(https?:.+?)>")) { match ->
                            "<a href=\"${match.groupValues[1]}\">${match.groupValues[1]}</a>"
                        }.replace("< ", "&lt; ")
                        .replace("> ", "&gt; ")
                        .replace("<= ", "&lt;= ")
                        .replace(">= ", "&gt;= ")
                        .replace("Query implements Node", "Query")
                } + "\n"

        target.parentFile.mkdirs()
        target.writeText(transformed)
    }
}