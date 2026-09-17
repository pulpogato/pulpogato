package io.github.pulpogato.buildsupport

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

@Suppress("unused")
class BuildSupportPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("jacoco")
        configureExclusiveApiVariantCapability(target)
        val coverageEnabled =
            target.providers
                .gradleProperty("coverage")
                .map(String::toBoolean)
                .getOrElse(false)

        target.plugins.withId("java") {
            target.tasks.named("test", Test::class.java).configure {
                extensions.configure<JacocoTaskExtension> {
                    isEnabled = coverageEnabled
                }
            }
            target.tasks.named("jacocoTestReport", JacocoReport::class.java).configure {
                reports {
                    xml.required.set(true)
                }
            }
        }
    }

    private fun configureExclusiveApiVariantCapability(target: Project) {
        val variantName = target.name.removePrefix("${target.rootProject.name}-")
        val apiFamily = API_VARIANT.matchEntire(variantName)?.groupValues?.get(1) ?: return

        target.plugins.withId("java-library") {
            val moduleCapability = "${target.group}:${target.name}:${target.version}"
            val familyCapability = "${target.group}:${target.rootProject.name}-$apiFamily:${target.version}"
            listOf("apiElements", "runtimeElements").forEach { configurationName ->
                target.configurations.named(configurationName).configure {
                    outgoing.capability(moduleCapability)
                    outgoing.capability(familyCapability)
                }
            }
        }
    }

    private companion object {
        val API_VARIANT = Regex("(rest|graphql)-(fpt|ghec|ghes-.+)")
    }
}