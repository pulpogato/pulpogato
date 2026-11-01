package io.github.pulpogato.restcodegen

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GeneratorFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `generation reuses configuration and removes stale outputs after schema changes`() {
        val directory = fixture("project")
        assertThat(run(directory).task(":generateFixture")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val second = run(directory)
        assertThat(second.output).contains("Reusing configuration cache")
        assertThat(second.task(":generateFixture")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(File(directory, "build/main/com/example/rest/api/RestClients.java").readText())
            .contains("RedirectExchangeFunction", "getThingsApi")
        assertThat(File(directory, "build/main/com/example/rest/webhooks/WebhookHeaders.java").readText())
            .contains("class WebhookHeaders")

        val staleFiles =
            listOf("build/main/Obsolete.java", "build/test/java/ObsoleteTest.java", "build/test/resources/obsolete.json")
                .map { File(directory, it).apply { writeText("stale") } }
        val schema = File(directory, "schema.json")
        val originalSchema = schema.readText()
        val updatedSchema = originalSchema.replace("\"name\":{\"type\":\"string\"}", "\"renamed\":{\"type\":\"string\"}")
        assertThat(updatedSchema).isNotEqualTo(originalSchema)
        schema.writeText(updatedSchema)
        assertThat(run(directory).task(":generateFixture")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        staleFiles.forEach { assertThat(it).doesNotExist() }
        assertThat(File(directory, "build/main/com/example/rest/schemas/Thing.java").readText()).contains("renamed")
    }

    private fun fixture(name: String): File {
        val directory = File(tempDir, name).apply { mkdirs() }
        File(directory, "common").mkdirs()
        File(directory, "module").mkdirs()
        File(directory, "settings.gradle").writeText(
            """
            rootProject.name = 'codegen-fixture'
            buildCache { local { directory = file('${File(tempDir, "cache").invariantSeparatorsPath}') } }
            """.trimIndent(),
        )
        File(directory, "build.gradle").writeText(
            """
            import io.github.pulpogato.restcodegen.GenerateJavaTask
            plugins { id 'io.github.pulpogato.rest-codegen' }
            tasks.register('generateFixture', GenerateJavaTask) {
                schema = objects.property(File).value(file('schema.json'))
                packageName = objects.property(String).value('com.example')
                mainDir = objects.property(File).value(file('build/main'))
                testDir = objects.property(File).value(file('build/test/java'))
                testResourcesDir = objects.property(File).value(file('build/test/resources'))
                rootProjectDir.set(layout.projectDirectory.asFile)
                projectDir.set(layout.projectDirectory.asFile)
                commonResourcesDir.set(file('common'))
                moduleResourcesDir.set(file('module'))
            }
            """.trimIndent(),
        )
        File(directory, "schema.json").writeText(
            """
            {
              "openapi":"3.1.0", "info":{"title":"Fixture","version":"1"},
              "tags":[{"name":"things","description":"Things"}],
              "paths":{"/things":{"get":{"operationId":"things/get","tags":["things"],
                "responses":{"200":{"description":"OK","content":{"application/json":{
                  "schema":{"${'$'}ref":"#/components/schemas/thing"}}}}}}}},
              "webhooks":{"thing":{"post":{"operationId":"webhooks/thing", "parameters":[],
                "x-github":{"subcategory":"thing"},
                "requestBody":{"content":{"application/json":{"schema":{"${'$'}ref":"#/components/schemas/thing"}}}},
                "responses":{"200":{"description":"OK"}}}}},
              "components":{"schemas":{"thing":{"type":"object","properties":{"name":{"type":"string"}}}}}
            }
            """.trimIndent(),
        )
        return directory
    }

    private fun run(directory: File) =
        GradleRunner
            .create()
            .withProjectDir(directory)
            .withPluginClasspath()
            .withArguments("generateFixture", "--build-cache", "--configuration-cache", "--no-scan", "--no-watch-fs", "--max-workers=2")
            .build()
}