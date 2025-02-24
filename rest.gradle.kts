import codegen.Main
import com.adarshr.gradle.testlogger.theme.ThemeType
import de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
}

dependencies {
    compileOnly(libs.annotations)
    compileOnly(libs.springWeb)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    api(project(":${rootProject.name}-common"))

    testImplementation(project(":${rootProject.name}-rest-tests"))
    testImplementation(libs.bundles.springBoot)
}

fun getUrl(projectVariant: String): String {
    val path = if (projectVariant == "fpt") "api.github.com" else projectVariant
    return "https://github.com/github/rest-api-description/raw/main/descriptions-next/$path/$path.json"
}

val projectVariant = project.name.replace("${rootProject.name}-rest-", "")

description = "REST types for $projectVariant"

val downloadSchema = tasks.register<Download>("downloadSchema") {
    src(getUrl(projectVariant))
    dest(file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json"))
    onlyIfModified(true)
    tempAndMove(true)
    useETag("all")

    inputs.property("url", getUrl(projectVariant))
    outputs.file(file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json"))
}

val generateJava = tasks.register("generateJava") {
    dependsOn(downloadSchema)
    inputs.file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json")
    inputs.dir("${rootDir}/buildSrc/src")
    inputs.file("${rootDir}/buildSrc/build.gradle.kts")

    doLast {
        file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen").mkdirs()

        Main().process(
                file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json"),
                file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen"),
                "io.github.pulpogato",
                file("${project.layout.buildDirectory.get()}/generated/sources/test")
        )
    }
    outputs.dir(file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen"))
    outputs.dir(file("${project.layout.buildDirectory.get()}/generated/sources/test"))
}

tasks.compileJava {
    dependsOn(generateJava)
}
tasks.named("sourcesJar") {
    dependsOn(generateJava)
}
tasks.named("javadocJar") {
    dependsOn(generateJava)
}
tasks.processResources {
    dependsOn(downloadSchema)
}

sourceSets {
    named("main") {
        java.srcDir("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen")
        resources.srcDir("${project.layout.buildDirectory.get()}/generated/resources/main")
    }
    named("test") {
        java.srcDir("${project.layout.buildDirectory.get()}/generated/sources/test")
    }
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        addStringOption("encoding", "UTF-8")
        addStringOption("charSet", "UTF-8")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

testlogger {
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN else ThemeType.MOCHA
    slowThreshold = 5000

    showPassed = false
    showSkipped = true
    showFailed = true
}

pitest {
    timestampedReports = false
    junit5PluginVersion.set("1.2.1") // Look here for latest version - https://github.com/pitest/pitest-junit5-plugin/tags
    mutators.set(setOf("ALL"))
    outputFormats.set(setOf("XML", "HTML"))
}

val mockitoAgent = configurations.create("mockitoAgent")
dependencies {
    testImplementation(libs.mockito)
    mockitoAgent(libs.mockito) { isTransitive = false }
}
tasks {
    test {
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
    }
}
