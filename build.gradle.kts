import com.fasterxml.jackson.databind.ObjectMapper
import com.github.rahulsom.waena.WaenaExtension
import nebula.plugin.contacts.Contact
import nebula.plugin.contacts.ContactsExtension

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
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
        val branches = uri("https://api.github.com/repos/github/rest-api-description/branches/main").toURL()
        val json = ObjectMapper().readTree(branches)
        val sha = json.get("commit").get("sha").asText().take(7)
        val oldProps = project.file("gradle.properties").readText()
        val newProps = oldProps.replace(Regex("github.api.version=.*"), "github.api.version=$sha")
        project.file("gradle.properties").writeText(newProps)
        if (project.ext.get("github.api.version") != sha) {
            println("Updated github.api.version from ${project.ext.get("github.api.version")} to $sha")
        } else {
            println("github.api.version is already up to date")
        }
    }
}