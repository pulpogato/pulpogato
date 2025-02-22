plugins {
    id("com.gradle.develocity").version("3.19.2")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/terms-of-service")
        termsOfUseAgree.set("yes")
    }
}

rootProject.name = "pulpogato"

private fun createProject(variant: String,  ghesVersion:String) {
    val projectName = "${rootProject.name}-$variant-$ghesVersion"
    if (!file(projectName).exists()) {
        file(projectName).mkdirs()
    }
    include(projectName)
    project(":${projectName}").buildFileName = "../${variant}.gradle.kts"
}

createProject("graphql", "fpt")
createProject("graphql", "ghec")
createProject("graphql", "ghes-3.15")
createProject("graphql", "ghes-3.14")
createProject("graphql", "ghes-3.13")
createProject("graphql", "ghes-3.12")

createProject("rest", "fpt")
createProject("rest", "ghec")
createProject("rest", "ghes-3.15")
createProject("rest", "ghes-3.14")
createProject("rest", "ghes-3.13")
createProject("rest", "ghes-3.12")

include("${rootProject.name}-common")
include("${rootProject.name}-rest-tests")
