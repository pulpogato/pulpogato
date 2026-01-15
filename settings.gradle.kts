plugins {
    id("com.gradle.develocity").version("4.3.1")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/terms-of-service")
        termsOfUseAgree.set("yes")
    }
}

rootProject.name = "pulpogato"

private fun createProject(variant: String, ghVersion:String) {
    val projectName = "${rootProject.name}-$variant-$ghVersion"
    if (!file(projectName).exists()) {
        file(projectName).mkdirs()
    }
    include(projectName)
    project(":${projectName}").buildFileName = "../${variant}.gradle.kts"
}

include("${rootProject.name}-common")
include("${rootProject.name}-rest-tests")

val allVersions = listOf(
    "fpt",
    "ghec",
    "ghes-3.14",
    "ghes-3.15",
    "ghes-3.16",
    "ghes-3.17",
    "ghes-3.18",
    "ghes-3.19",
)

allVersions.forEach { ghVersion ->
    createProject("graphql", ghVersion)
    createProject("rest", ghVersion)
}

include("${rootProject.name}-rest-ghestest")

includeBuild("gradle/pulpogato-rest-codegen")