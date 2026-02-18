plugins {
    id("java-platform")
    alias(libs.plugins.waenaPublished)
}
description = "BOM for aligning Pulpogato module versions"

val moduleNameRegex = Regex("^${Regex.escape(rootProject.name)}-(rest|graphql)-.+$")
val excludedModuleNames = setOf("${rootProject.name}-rest-tests", "${rootProject.name}-rest-ghestest")

val apiModulePaths =
    rootProject.subprojects
        .map { it.name }
        .filter { moduleNameRegex.matches(it) && it !in excludedModuleNames }
        .sorted()
        .map { ":$it" }

dependencies {
    constraints {
        api(project(":${rootProject.name}-common"))
        api(project(":${rootProject.name}-github-files"))
        apiModulePaths.forEach { modulePath ->
            api(project(modulePath))
        }
    }
}