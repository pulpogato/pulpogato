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

include("${rootProject.name}-common")
include("${rootProject.name}-rest-tests")

listOf("fpt", "ghes") + (12..15).map { "ghes-3.$it" }.forEach { ghesVersion ->
    createProject("graphql", ghesVersion)
    createProject("rest", ghesVersion)
}
