package io.github.pulpogato.restcodegen

import com.diffplug.spotless.glue.pjf.PalantirJavaFormatFormatterFunc
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.extension

open class GenerateJavaTask : DefaultTask() {
    @InputFile lateinit var schema: Provider<File>

    @Input lateinit var packageName: Provider<String>

    @OutputDirectory lateinit var mainDir: Provider<File>

    @OutputDirectory lateinit var testDir: Provider<File>

    @TaskAction
    fun generate() {
        project.file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen").mkdirs()
        Main().process(schema.get(), mainDir.get(), packageName.get(), testDir.get())

        // Format generated Java code
        val javaFiles = getJavaFiles(mainDir.get())
        val testJavaFiles = getJavaFiles(testDir.get())
        val formatter = PalantirJavaFormatFormatterFunc("PALANTIR", true)
        (javaFiles + testJavaFiles).forEach { f ->
            val formatted = formatter.apply(f.readText())
            f.writeText(formatted)
        }
    }

    private fun getJavaFiles(dir: File): List<File> {
        return dir.walk()
            .filter { it.isFile }
            .filter { it.toPath().extension == "java" }
            .toList()
    }
}