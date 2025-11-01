package io.github.pulpogato.restcodegen

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class RestCodegenPluginTest {
    @TempDir
    lateinit var tempDir: Path
    private lateinit var project: Project

    @BeforeEach
    fun setup() {
        project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
        project.pluginManager.apply("io.github.pulpogato.rest-codegen")
    }

    @Test
    fun `plugin is applied correctly`() {
        assertThat(project.plugins.hasPlugin("io.github.pulpogato.rest-codegen")).isTrue
    }

    @Test
    fun `extension is created`() {
        val extension = project.extensions.findByType(RestCodegenExtension::class.java)
        assertThat(extension).isNotNull()
    }

    @Test
    fun `generateJava task is created`() {
        val task = project.tasks.findByName("generateJava")
        assertThat(task).isNotNull().isInstanceOf(GenerateJavaTask::class.java)
    }

    @Test
    fun `generateJava task has correct properties`() {
        val task = project.tasks.findByName("generateJava") as GenerateJavaTask
        val mainDir = File(tempDir.toFile(), "main")
        mainDir.mkdirs()
        val testDir = File(tempDir.toFile(), "test")
        testDir.mkdirs()
        val packageName = "com.example.test"
        val extension = project.extensions.findByType(RestCodegenExtension::class.java)!!
        extension.mainDir.set(mainDir)
        extension.testDir.set(testDir)
        extension.packageName.set(packageName)

        assertThat(mainDir).isEqualTo(task.mainDir.get())
        assertThat(testDir).isEqualTo(task.testDir.get())
        assertThat(packageName).isEqualTo(task.packageName.get())
    }

    @Test
    fun `downloadSchema task is created`() {
        val task = project.tasks.findByName("downloadSchema")
        assertThat(task).isNotNull().isInstanceOf(DownloadSchemaTask::class.java)
    }

    @Test
    fun `generateJava task depends on downloadSchema`() {
        val generateJava = project.tasks.findByName("generateJava")
        val downloadSchema = project.tasks.findByName("downloadSchema")
        assertThat(generateJava?.taskDependencies?.getDependencies(generateJava))
            .contains(downloadSchema)
    }
}