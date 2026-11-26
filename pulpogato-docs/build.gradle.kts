plugins {
    alias(libs.plugins.asciidoctor)
}

tasks.asciidoctor.configure {
    notCompatibleWithConfigurationCache("Asciidoctor Gradle Plugin is not compatible with the configuration cache.")
    val releaseTag = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")
    val pulpogatoVersion =
        providers
            .exec {
                workingDir(rootDir)
                commandLine("git", "tag", "--list", "v*", "--sort=-version:refname")
            }.standardOutput.asText
            .get()
            .lineSequence()
            .firstOrNull { releaseTag.matches(it) }
            ?.removePrefix("v") ?: "PULPOGATO_VERSION"
    val dependencyVersions =
        mapOf(
            "pulpogato-version" to pulpogatoVersion,
            "spring-boot-version" to libs.versions.springBoot.get(),
            "dgs-client-version" to libs.versions.dgsClient.get(),
        )
    val dependencyVersionsFile = temporaryDir.resolve("dependency-versions.adoc")
    inputs.properties(dependencyVersions)
    inputs.file(rootProject.file("README.adoc"))
    doFirst {
        dependencyVersionsFile.writeText(
            buildString {
                dependencyVersions.forEach { (name, version) -> appendLine(":$name: $version") }
            },
        )
    }
    sourceDir(file("src"))
    sources {
        include("index.adoc")
    }
    baseDirFollowsSourceFile()
    setOutputDir(file("build"))
    outputOptions {
        separateOutputDirs = false
    }
    attributes(
        mapOf(
            "dependency-versions-file" to dependencyVersionsFile.absolutePath,
            "docinfo" to "shared",
            "docinfodir" to file("src/docinfo").absolutePath,
        ),
    )
}

// Nothing here for Sonar to analyze, but SonarCloud's DBD Java sensor still runs on every scanned
// module and warns that sonar.java.binaries is empty.
sonar {
    isSkipProject = true
}