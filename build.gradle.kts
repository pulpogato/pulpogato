import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.rahulsom.waena.WaenaExtension
import io.github.pulpogato.buildsupport.UpdateRepositoryBranchPropertyTask
import nebula.plugin.contacts.ContactsExtension

buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    configurations {
        classpath {
            resolutionStrategy {
                // waena -> jreleaser -> commonmark can mess with the codegen module.
                // This forces the version of commonmark to one compatible with the codegen module.
                force(libs.commonmark, libs.commonmarkExtAutolink)
            }
        }
    }
    dependencies {
        components {
            // spotless brings jgit 7.x which is incompatible with jreleaser.
            // Removing jgit from spotless's metadata lets jreleaser be the sole provider.
            listOf("com.diffplug.spotless:spotless-plugin-gradle", "com.diffplug.spotless:spotless-lib-extra").forEach { module ->
                withModule(module) {
                    allVariants {
                        withDependencies {
                            removeAll { dependency -> dependency.group == "org.eclipse.jgit" }
                        }
                    }
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.waenaRoot)
    alias(libs.plugins.waenaPublished).apply(false)
    alias(libs.plugins.dgs).apply(false)
    alias(libs.plugins.download).apply(false)
    alias(libs.plugins.spotless).apply(false)
    id("io.github.pulpogato.build-support")
}

repositories {
    mavenCentral()
}

allprojects {
    group = "io.github.pulpogato"
    apply(plugin = "com.diffplug.spotless")

    configure<SpotlessExtension> {
        kotlin {
            ktlint()
            target("src/**/*.kt", "*.kts")
            targetExclude("build/**")
        }
        java {
            palantirJavaFormat()
            target("src/**/*.java")
            targetExclude("build/**")
        }
        json {
            jackson()
            target("src/**/*.json", "*.json", ".vscode/**/*.json", ".sonarlint/**/*.json")
            targetExclude("build/**")
        }
        yaml {
            prettier()
            target("src/**/*.yaml", "*.yaml", "*.yml", ".github/**/*.yaml", ".github/**/*.yml")
            targetExclude("build/**")
        }
    }

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

tasks.register<UpdateRepositoryBranchPropertyTask>("updateRestSchemaVersion") {
    description = "Update Rest Schema Version from GitHub Rest API Descriptions"
    group = "maintenance"
    repository.set(project.ext["gh.api.repo"].toString())
    branch.set("main")
    propertyName.set("gh.api.commit")
    propertiesFile.set(layout.projectDirectory.file("gradle.properties"))
    gitHubToken.set(providers.environmentVariable("GITHUB_TOKEN").orElse(""))
}

tasks.register<UpdateRepositoryBranchPropertyTask>("updateSchemastoreVersion") {
    description = "Update Schemastore Version from schemastore/schemastore"
    group = "maintenance"
    repository.set(project.ext["schemastore.repo"].toString())
    branch.set("master")
    propertyName.set("schemastore.commit")
    propertiesFile.set(layout.projectDirectory.file("gradle.properties"))
    gitHubToken.set(providers.environmentVariable("GITHUB_TOKEN").orElse(""))
}

val checkPlugin =
    tasks.register("checkPlugin", Exec::class) {
        description = "Run check on plugin code"
        group = "verification"
        notCompatibleWithConfigurationCache("Invokes a separate Gradle build for the included plugin project.")
        commandLine("./gradlew", "--project-dir", "gradle/pulpogato-rest-codegen", "check")
    }

val spotlessApplyPlugin =
    tasks.register("spotlessApplyPlugin", Exec::class) {
        description = "Run spotlessApply on plugin code"
        group = "verification"
        notCompatibleWithConfigurationCache("Invokes a separate Gradle build for the included plugin project.")
        commandLine("./gradlew", "--project-dir", "gradle/pulpogato-rest-codegen", "spotlessApply")
    }

tasks.named("check").configure {
    dependsOn(checkPlugin)
}

tasks.named("spotlessApply").configure {
    dependsOn(spotlessApplyPlugin)
}

val pitestPlugin =
    tasks.register("pitestPlugin", Exec::class) {
        description = "Run pitest on plugin code"
        group = "verification"
        notCompatibleWithConfigurationCache("Invokes a separate Gradle build for the included plugin project.")
        commandLine("./gradlew", "--project-dir", "gradle/pulpogato-rest-codegen", "pitest")
    }

tasks.register("pitest") {
    description = "Run pitest from plugin"
    group = "verification"
    notCompatibleWithConfigurationCache("Delegates to a task that invokes a separate Gradle build.")
    dependsOn(pitestPlugin)
}

val dockerExecutable =
    providers.environmentVariable("DOCKER_BIN").orNull?.takeIf { it.isNotBlank() }
        ?: listOf("/usr/local/bin/docker", "/opt/homebrew/bin/docker", "/usr/bin/docker")
            .firstOrNull { candidate -> rootProject.file(candidate).canExecute() }
        ?: "docker"

tasks.named("prepare") {
    group = "Nebula Release"
}

tasks.named("release") {
    group = "Nebula Release"
}

tasks.register<Exec>("asciidoctorDocs") {
    description = "Generate Asciidoctor HTML docs in Docker"
    group = "documentation"
    notCompatibleWithConfigurationCache("Runs documentation generation in Docker via an external process.")
    commandLine(
        dockerExecutable,
        "run",
        "--rm",
        "-v",
        "${rootProject.projectDir.absolutePath}:/documents",
        "asciidoctor/docker-asciidoctor:main",
        "asciidoctor",
        "--destination-dir",
        "pulpogato-docs/build",
        "--backend=html5",
        "--failure-level",
        "WARN",
        "--out-file",
        "index.html",
        "pulpogato-docs/src/index.adoc",
    )
    inputs.file(layout.projectDirectory.file("pulpogato-docs/src/index.adoc"))
    outputs.file(layout.projectDirectory.file("pulpogato-docs/build/index.html"))
}