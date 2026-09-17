package io.github.pulpogato.buildsupport

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class BuildSupportPluginTest {
    @ParameterizedTest
    @CsvSource(
        "pulpogato-rest-fpt, pulpogato-rest",
        "pulpogato-rest-ghec, pulpogato-rest",
        "pulpogato-rest-ghes-3.20, pulpogato-rest",
        "pulpogato-graphql-fpt, pulpogato-graphql",
        "pulpogato-graphql-ghec, pulpogato-graphql",
        "pulpogato-graphql-ghes-3.20, pulpogato-graphql",
    )
    fun `api variants provide their family capability`(
        projectName: String,
        capabilityName: String,
    ) {
        val project = createProject(projectName)

        listOf("apiElements", "runtimeElements").forEach { configurationName ->
            val capabilities =
                project.configurations
                    .getByName(configurationName)
                    .outgoing.capabilities
            assertThat(capabilities).anySatisfy { capability ->
                assertThat(capability.group).isEqualTo("io.github.pulpogato")
                assertThat(capability.name).isEqualTo(projectName)
                assertThat(capability.version).isEqualTo("1.2.3")
            }
            assertThat(capabilities).anySatisfy { capability ->
                assertThat(capability.group).isEqualTo("io.github.pulpogato")
                assertThat(capability.name).isEqualTo(capabilityName)
                assertThat(capability.version).isEqualTo("1.2.3")
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "pulpogato-common",
                "pulpogato-github-files",
                "pulpogato-rest-tests",
                "pulpogato-rest-ghestest",
            ],
    )
    fun `non-variant modules do not provide an api family capability`(projectName: String) {
        val project = createProject(projectName)

        listOf("apiElements", "runtimeElements").forEach { configurationName ->
            assertThat(
                project.configurations
                    .getByName(configurationName)
                    .outgoing.capabilities,
            ).noneSatisfy { capability ->
                assertThat(capability.name).isIn("pulpogato-rest", "pulpogato-graphql")
            }
        }
    }

    private fun createProject(projectName: String): Project {
        val rootProject = ProjectBuilder.builder().withName("pulpogato").build()
        val project =
            ProjectBuilder
                .builder()
                .withName(projectName)
                .withParent(rootProject)
                .build()
        project.group = "io.github.pulpogato"
        project.version = "1.2.3"
        project.pluginManager.apply("java-library")
        project.pluginManager.apply(BuildSupportPlugin::class.java)
        return project
    }
}