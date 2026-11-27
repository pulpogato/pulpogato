plugins {
    alias(libs.plugins.asciidoctor)
}

val releaseTag = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")
val pulpogatoVersion =
    providers
        .exec {
            workingDir(rootDir)
            commandLine("git", "tag", "--list", "v*", "--sort=-version:refname")
        }.standardOutput.asText
        .map { tags ->
            tags.lineSequence().firstOrNull { releaseTag.matches(it) }?.removePrefix("v") ?: "PULPOGATO_VERSION"
        }

asciidoc {
    publications.named("main") {
        sourceSet {
            setSourceDir(layout.projectDirectory.dir("src"))
            sources("index.adoc")
            attributes {
                add("revnumber!", "")
                add("pulpogato-version", pulpogatoVersion)
                add("spring-boot-version", libs.versions.springBoot)
                add("dgs-client-version", libs.versions.dgsClient)
            }
            docInfo {
                setDocInfoDir(layout.projectDirectory.dir("src/docinfo"))
                setHeadIsPrivate(false)
                setFooterIsPrivate(false)
            }
        }
        output("asciidoctorj", "html")
    }
}

val asciidoctorHtml =
    tasks.named("asciidoctorHtml") {
        inputs.file(rootProject.layout.projectDirectory.file("README.adoc")).withPathSensitivity(PathSensitivity.RELATIVE)
    }

tasks.register("asciidoctor") {
    group = "documentation"
    description = "Builds the HTML documentation."
    dependsOn(asciidoctorHtml)
}

// Nothing here for Sonar to analyze, but SonarCloud's DBD Java sensor still runs on every scanned
// module and warns that sonar.java.binaries is empty.
sonar {
    isSkipProject = true
}