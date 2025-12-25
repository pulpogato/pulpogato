import com.fasterxml.jackson.databind.ObjectMapper
import com.github.rahulsom.waena.WaenaExtension
import nebula.plugin.contacts.ContactsExtension
import java.net.HttpURLConnection

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // waena -> jreleaser -> commonmark can mess with the codegen module.
    // This forces the version of commonmark to one compatible with the codegen module.
    configurations {
        classpath {
            resolutionStrategy {
                force(libs.commonmark)
                force(libs.commonmarkExtAutolink)
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
        with(addPerson("rahulsom@noreply.github.com")) {
            moniker("Rahul Somasunderam")
            roles("owner")
            github("https://github.com/rahulsom")
        }
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
    description = "Update Rest Schema Version from GitHub Rest API Descriptions"
    group = "maintenance"
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
        val newProps = oldProps.replace(Regex("gh.api.commit=.*"), "gh.api.commit=$sha")
        project.file("gradle.properties").writeText(newProps)
        if (project.ext.get("gh.api.commit") != sha) {
            println("Updated gh.api.commit from ${project.ext.get("gh.api.commit")} to $sha")
        } else {
            println("gh.api.commit is already up to date")
        }
    }
}

val checkPlugin = tasks.register("checkPlugin", Exec::class) {
    description = "Run check on plugin code"
    group = "verification"
    commandLine("./gradlew", "--project-dir", "gradle/pulpogato-rest-codegen", "check")
}

tasks.named("check").configure {
    dependsOn(checkPlugin)
}

val pitestPlugin = tasks.register("pitestPlugin", Exec::class) {
    description = "Run pitest on plugin code"
    group = "verification"
    commandLine("./gradlew", "--project-dir", "gradle/pulpogato-rest-codegen", "pitest")
}

tasks.register("pitest") {
    description = "Run pitest from plugin"
    group = "verification"
    dependsOn(pitestPlugin)
}

val spotlessApplyPlugin = tasks.register("spotlessApplyPlugin", Exec::class) {
    description = "Run spotlessApply on plugin code"
    group = "verification"
    commandLine("./gradlew", "--project-dir", "gradle/pulpogato-rest-codegen", "spotlessApply")
}

tasks.register("spotlessApply") {
    description = "Run spotlessApply from plugin"
    group = "verification"
    dependsOn(spotlessApplyPlugin)
}