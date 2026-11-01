package io.github.pulpogato.restcodegen

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateHeadersFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `headers survive cache restoration and version changes without downloading schemas`() {
        File(projectDir, "settings.gradle").writeText(
            """
            rootProject.name = 'headers-test'
            buildCache { local { directory = file('cache') } }
            """.trimIndent(),
        )
        File(projectDir, "build.gradle").writeText(
            """
            plugins { id 'io.github.pulpogato.rest-codegen' }
            codegen {
                apiVersion.set('2022-11-28')
                projectVersion.set(providers.gradleProperty('libraryVersion').orElse('1.0.0'))
            }
            """.trimIndent(),
        )
        val headers = File(projectDir, "build/generated-src/main/resources/pulpogato-headers.properties")
        val first = run()
        assertThat(first.task(":generateHeaders")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(headers.readText()).contains("pulpogato.version=1.0.0", "github.api.version=2022-11-28")

        val second = run()
        assertThat(second.output).contains("Reusing configuration cache")
        assertThat(second.task(":generateHeaders")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

        File(projectDir, "build").deleteRecursively()
        val restored = run()
        assertThat(restored.task(":generateHeaders")!!.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
        assertThat(headers.readText()).contains("pulpogato.version=1.0.0")

        val updated = run("-PlibraryVersion=2.0.0")
        assertThat(updated.task(":generateHeaders")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(headers.readText()).contains("pulpogato.version=2.0.0")
        listOf(first, second, restored, updated).forEach {
            assertThat(it.task(":downloadSchema")).isNull()
        }
    }

    private fun run(vararg arguments: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHeaders", "--build-cache", "--configuration-cache", "--max-workers=2", *arguments)
            .build()
}