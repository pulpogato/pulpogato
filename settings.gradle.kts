plugins {
    id("com.gradle.develocity").version("4.0.1")
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

val allVersions = listOf("fpt", "ghec") + (13..16).map { "ghes-3.$it" }
allVersions.forEach { ghVersion ->
    createProject("graphql", ghVersion)
    createProject("rest", ghVersion)
}

includeBuild("gradle/pulpogato-rest-codegen")