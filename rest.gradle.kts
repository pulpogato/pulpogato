import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
    id("jacoco")
    id("io.github.pulpogato.rest-codegen")
}

dependencies {
    compileOnly(libs.annotations)
    compileOnly(libs.springWeb)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    api(project(":${rootProject.name}-common"))

    testImplementation(project(":${rootProject.name}-rest-tests"))
    testImplementation(libs.bundles.springBoot)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

fun getUrl(projectVariant: String): String {
    val path = if (projectVariant == "fpt") "api.github.com" else projectVariant
    val ref = project.ext.get("github.api.version")
    return "https://github.com/github/rest-api-description/raw/$ref/descriptions-next/$path/$path.json"
}

val projectVariant = project.name.replace("${rootProject.name}-rest-", "")

description = "REST types for $projectVariant"

val downloadSchema = tasks.register("downloadSchema") {
    val schemaLocation = file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json")
    inputs.property("github.api.version", project.ext.get("github.api.version"))
    outputs.file(schemaLocation)

    doLast {
        schemaLocation.parentFile.mkdirs()
        schemaLocation.writeBytes(uri(getUrl(projectVariant)).toURL().readBytes())
    }
}

val generateJava = tasks.named("generateJava")

generateJava.configure {
    dependsOn(downloadSchema)
}

codegen {
    schema.set(file("${project.layout.buildDirectory.get()}/generated/resources/main/schema.json"))
    packageName.set("io.github.pulpogato")
    mainDir.set(file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen"))
    testDir.set(file("${project.layout.buildDirectory.get()}/generated/sources/test"))
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
    junit5PluginVersion.set(libs.versions.pitestJunit5Plugin) // Look here for latest version - https://github.com/pitest/pitest-junit5-plugin/tags
    pitestVersion.set(libs.versions.pitest) // Look here for latest version - https://github.com/hcoles/pitest/releases
    mutators.set(setOf("ALL"))
    outputFormats.set(setOf("XML", "HTML", "CSV"))
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

tasks.withType<JavaCompile> {
    options.setIncremental(true)
}