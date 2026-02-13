package io.github.pulpogato.restcodegen

import com.palantir.javaformat.java.Formatter
import org.gradle.api.logging.Logger
import java.io.File

fun updateSchemaStack(
    schemaStack: List<String>,
    vararg elements: String,
): List<String> {
    val newStack = schemaStack.toMutableList()
    if (elements.isNotEmpty() && elements.first() == "#") {
        newStack.clear()
    }
    newStack.addAll(elements)
    return newStack
}

fun schemaStackRef(schemaStack: List<String>): String = schemaStack.joinToString("/") { it.replace("/", "~1") }

fun collectJavaFiles(vararg directories: File): List<File> =
    directories
        .asSequence()
        .filter { it.exists() }
        .flatMap { dir ->
            dir
                .walk()
                .asSequence()
                .filter { it.isFile && it.extension == "java" }
        }.toList()

fun formatJavaFiles(
    javaFiles: List<File>,
    logger: Logger,
    continueOnError: Boolean = false,
) {
    val formatter = Formatter.create()
    try {
        javaFiles.parallelStream().forEach { file ->
            if (continueOnError) {
                try {
                    formatJavaFile(formatter, file)
                } catch (e: Exception) {
                    logger.warn("Failed to format ${file.name}: ${e.message}")
                }
            } else {
                formatJavaFile(formatter, file)
            }
        }
    } catch (e: IllegalAccessError) {
        logger.warn("Failed to format Java files: ${e.message}")
    }
}

private fun formatJavaFile(
    formatter: Formatter,
    file: File,
) {
    val formatted = formatter.formatSource(file.readText())
    file.writeText(formatted)
}