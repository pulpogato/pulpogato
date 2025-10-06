import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.text.matches

plugins {
    alias(libs.plugins.javaLibrary)
}

dependencies {
    api(libs.junit)
    api(libs.assertj)
    implementation(libs.junitPlatformLauncher)
    compileOnly(libs.annotations)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.jsonApi)
    implementation(libs.glassfishJson)
    implementation(project(":${rootProject.name}-common"))

    implementation(libs.reflections)
    implementation(libs.bundles.springBoot)
    implementation(libs.jacksonYaml)

    implementation(libs.bundles.jackson)

    implementation(libs.httpclient5)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.setIncremental(true)
}

class IssueStatus {
    var number: Int? = null
    var state: String? = null
    var url: String? = null
    override fun toString(): String {
        return "IssueStatus(number=$number, state=$state, url=$url)"
    }
}

fun getIssueStatuses(file: File): List<IssueStatus> {
    val regex = ".+\"(https://github.com/github/rest-api-description/issues/\\d+)\"".toRegex()
    val objectMapper = ObjectMapper()
    val statuses = file.readLines()
        .filter { it.matches(regex) }
        .mapNotNull { regex.matchEntire(it)?.groupValues?.get(1) }
        .distinct()
        .map { ProcessBuilder("gh", "issue", "view", it, "--json", "state,url,number").start() }
        .onEach { it.waitFor() }
        .mapNotNull { objectMapper.readValue(it.inputReader(), IssueStatus::class.java) }
    return statuses
}

tasks.register("checkIssueStatuses") {
    doLast {
        val file = project.file("src/main/resources/IgnoredTests.yml")
        val statuses = getIssueStatuses(file)

        var notOpen = statuses.count { it.state != "OPEN" }

        println(statuses.groupBy { it.state }.mapValues { it.value.size })
        statuses
            .filter { it.state != "OPEN" }
            .forEach { println(it) }
        if (notOpen > 0) {
            throw GradleException("$notOpen issues report as not open. Please check $file")
        }
    }
}
