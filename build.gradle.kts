import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.rahulsom.RoseauExtension
import com.github.rahulsom.waena.WaenaExtension
import io.github.pulpogato.buildsupport.UpdateRepositoryBranchPropertyTask

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
    alias(libs.plugins.asciidoctor).apply(false)
    alias(libs.plugins.roseau).apply(false)
    id("io.github.pulpogato.build-support")
    alias(libs.plugins.sonarqube)
}

repositories {
    mavenCentral()
}

allprojects {
    group =
        when (System.getenv("JITPACK")) {
            "true" -> "com.github.pulpogato.pulpogato"
            else -> "io.github.pulpogato"
        }
    plugins.apply("com.diffplug.spotless")

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
                .npmExecutable("$rootDir/npmw")
                .nodeExecutable("$rootDir/nodew")
            target("src/**/*.yaml", "*.yaml", "*.yml", ".github/**/*.yaml", ".github/**/*.yml")
            targetExclude("build/**")
        }
    }
    contacts {
        addPerson("rahulsom@noreply.github.com") {
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
    tasks.withType<JavaCompile>().configureEach {
        // Codegen emits @Deprecated fields verbatim from GitHub's OpenAPI spec, so a stray
        // reference to one anywhere in handwritten code is a real signal, not noise.
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Werror"))
    }
    tasks.withType<Test> {
        useJUnitPlatform()
        // Tests are MockMvc-based (no bound ports) and data-driven, so they parallelize safely across forked JVMs.
        // Half the cores leaves headroom for codegen/compile work and Gradle's own worker leases during a full build.
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
    plugins.withId("io.github.rahulsom.roseau") {
        configure<RoseauExtension> {
            html.set(true)
            json.set(true)
            md.set(true)
            csv.set(true)
            cli.set(false)
            verbosity.set(RoseauExtension.VerbosityLevel.NONE)
            excludeNames.set(
                listOf(
                    // Codegen emits a `*Converter` inner class per enum-like schema field; each one gains a
                    // new abstract `convert(S)` method whenever the enum's values change. That's noise, not
                    // an API break for consumers, since these converters aren't meant to be implemented externally.
                    ".*\\$.*Converter",
                    // Same story for the generated Jackson2/Jackson3 (de)serializer inner classes: their
                    // base-class method set shifts across Jackson releases, but these types are never
                    // implemented or called directly by consumers, so it's not a real API break.
                    ".*\\$.*Jackson[23](Serializer|Deserializer)",
                    // Generated model constructors churn constantly as fields are added/removed/reordered
                    // to match GitHub's schema. Consumers use builders/setters, not these constructors
                    // directly, so their signature changes aren't a real API break worth flagging.
                    ".*\\.<init>\\(.*\\)",
                    // oneOf/webhook supertypes are generated as sealed interfaces whose permitted
                    // subtypes each implement toCode() concretely. Roseau sees the concrete method
                    // "removed" when a type becomes one of these interfaces, but it's still declared
                    // abstractly on PulpogatoType and reachable through the sealed supertype, so calls
                    // through it keep resolving; this isn't a break for consumers.
                    ".*\\.toCode\\(\\)",
                ),
            )
        }
    }
}

waena {
    publishModes.set(setOf(WaenaExtension.PublishMode.Central, WaenaExtension.PublishMode.GitHub))
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

tasks.register<UpdateRepositoryBranchPropertyTask>("updateGithubActionsTypingSchemaVersion") {
    description = "Update GitHub Actions typing schema version from typesafegithub/github-actions-typing"
    group = "maintenance"
    repository.set(project.ext["gh.actions.typing.repo"].toString())
    branch.set("schema-latest")
    propertyName.set("gh.actions.typing.commit")
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

sonar {
    properties {
        property("sonar.projectKey", "pulpogato_pulpogato")
        property("sonar.organization", "pulpogato")
        // The generated REST/GraphQL/github-files sources live under each module's build/ dir, which
        // is gitignored. The scanner excludes gitignored files by default regardless of sonar.sources,
        // so this has to be disabled for the generated sources to be analyzed at all.
        property("sonar.scm.exclusions.disabled", "true")
    }
}