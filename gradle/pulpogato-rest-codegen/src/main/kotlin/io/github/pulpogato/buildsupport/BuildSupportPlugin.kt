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
}