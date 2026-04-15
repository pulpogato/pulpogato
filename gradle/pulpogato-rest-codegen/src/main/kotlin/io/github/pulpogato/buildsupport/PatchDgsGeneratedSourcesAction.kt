package io.github.pulpogato.buildsupport

import org.gradle.api.Action
import org.gradle.api.Task
import java.io.File
import java.io.Serializable

class PatchDgsGeneratedSourcesAction(
    private val generatedSourcesDir: File,
) : Action<Task>,
    Serializable {
    override fun execute(task: Task) {
        if (!generatedSourcesDir.exists()) {
            return
        }

        generatedSourcesDir
            .walkTopDown()
            .filter { it.isFile && it.name == "DgsConstants.java" }
            .forEach { it.delete() }
    }
}