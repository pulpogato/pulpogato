import codegen.Main
import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
    id("com.diffplug.spotless") version "7.0.2"
    id("jacoco")
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
    return "node_modules/rest-api-description/descriptions-next/$path/$path.json"
}

val projectVariant = project.name.replace("${rootProject.name}-rest-", "")

description = "REST types for $projectVariant"

val copySchema = tasks.register("copySchema") {
    doLast {
        copy {
            from("${rootDir}/${getUrl(projectVariant)}")
            into(file("${project.layout.buildDirectory.get()}/generated/resources/main"))
            rename { _ -> "schema.json" }
        }
    }
    inputs.file("${rootDir}/${getUrl(projectVariant)}")
    outputs.file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json")
}

val generateJava = tasks.register("generateJava") {
    dependsOn(copySchema)
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
    dependsOn("spotlessApply")
}
tasks.named("sourcesJar") {
    dependsOn("spotlessApply")
}
tasks.named("javadocJar") {
    dependsOn("spotlessApply")
}
tasks.processResources {
    dependsOn(copySchema)
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
    junit5PluginVersion.set(libs.versions.pitestJunit5Plugin) // Look here for latest version - https://github.com/pitest/pitest-junit5-plugin/tags
    pitestVersion.set(libs.versions.pitest) // Look here for latest version - https://github.com/hcoles/pitest/releases
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

spotless {
    java {
        target("build/generated/**/*.java")
        palantirJavaFormat()
    }
}

tasks.named("spotlessJava").configure {
    dependsOn(generateJava)
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}