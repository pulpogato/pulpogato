import com.fasterxml.jackson.databind.ObjectMapper
import com.github.rahulsom.waena.WaenaExtension
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactsExtension
import java.net.HttpURLConnection

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // waena 0.12.0 -> jreleaser 1.19.0 -> commonmark 0.21.0 messes with the codegen module.
    // This forces the version of commonmark to 0.25.0 which is compatible with the codegen module.
    configurations {
        classpath {
            resolutionStrategy {
                force("org.commonmark:commonmark:0.26.0")
                force("org.commonmark:commonmark-ext-autolink:0.26.0")
            }
        }
    }
}

plugins {
    alias(libs.plugins.waenaRoot)
    alias(libs.plugins.waenaPublished).apply(false)
    alias(libs.plugins.dgs).apply(false)
    alias(libs.plugins.download).apply(false)
}

allprojects {
    group = "io.github.pulpogato"

    extensions.findByType<ContactsExtension>()?.apply {
        addPerson("rahulsom@noreply.github.com", delegateClosureOf<Contact> {
            moniker("Rahul Somasunderam")
            roles("owner")
            github("https://github.com/rahulsom")
        })
    }
}

subprojects {
    repositories {
        mavenCentral()
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

waena {
    publishMode.set(WaenaExtension.PublishMode.Central)
}

tasks.register("updateRestSchemaVersion") {
    doLast {
        val connection = uri("https://api.github.com/repos/github/rest-api-description/branches/main").toURL().openConnection()
        val gitHubToken = System.getenv("GITHUB_TOKEN")
        if (gitHubToken != null) {
            (connection as HttpURLConnection).addRequestProperty("Authentication", "token $gitHubToken")
        }
        val branches = connection.getInputStream().bufferedReader().readText()
        val json = ObjectMapper().readTree(branches)
        val sha = json.get("commit").get("sha").asText().take(7)
        val oldProps = project.file("gradle.properties").readText()
        val newProps = oldProps.replace(Regex("gh.api.version=.*"), "gh.api.version=$sha")
        project.file("gradle.properties").writeText(newProps)
        if (project.ext.get("gh.api.version") != sha) {
            println("Updated gh.api.version from ${project.ext.get("gh.api.version")} to $sha")
        } else {
            println("gh.api.version is already up to date")
        }
    }
}