import com.adarshr.gradle.testlogger.theme.ThemeType
import io.github.pulpogato.restcodegen.DownloadSchemaTask
import nebula.plugin.info.InfoBrokerPlugin
import java.security.MessageDigest
import kotlin.jvm.java

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    id("jacoco")
    id("io.github.pulpogato.rest-codegen")
}

dependencies {
    compileOnly(libs.jspecify)
    compileOnly(libs.springWeb)
    compileOnly(libs.springBootWebflux)
    implementation(libs.commonsLang3)

    api(project(":${rootProject.name}-common"))

    testImplementation(project(":${rootProject.name}-rest-tests"))
    testImplementation(libs.bundles.springBoot)
    testRuntimeOnly(libs.groovy)
    testCompileOnly(libs.lombok)
    testCompileOnly(libs.jetbrainsAnnotations)
    testAnnotationProcessor(libs.lombok)
}

val variant = project.name.replace("${rootProject.name}-rest-", "")

description = "REST types for $variant"

codegen {
    packageName.set("io.github.pulpogato")
    mainDir.set(file("${project.layout.buildDirectory.get()}/generated-src/main/java"))
    testDir.set(file("${project.layout.buildDirectory.get()}/generated-src/test/java"))
    apiVersion.set(project.ext.get("gh.api.commit").toString())
    apiRepository.set(project.ext.get("gh.api.repo").toString())
    projectVariant.set(variant)
}

sourceSets {
    named("main") {
        java.srcDir("${project.layout.buildDirectory.get()}/generated-src/main/java")
        resources.srcDir("${project.layout.buildDirectory.get()}/generated-src/main/resources")
    }
    named("test") {
        java.srcDir("${project.layout.buildDirectory.get()}/generated-src/test/java")
        resources.srcDir("${project.layout.buildDirectory.get()}/generated-src/test/resources")
    }
}

val downloadSchema = tasks.named("downloadSchema")
val generateJava = tasks.named("generateJava")

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
tasks.processTestResources {
    dependsOn(generateJava)
}

// Exclude schema.json from the main jar
tasks.named<Jar>("jar") {
    exclude("schema.json")
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
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN_PARALLEL else ThemeType.MOCHA_PARALLEL
    slowThreshold = 5000

    showPassed = false
    showSkipped = false
    showFailed = true
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

val addSchemaInfoToBroker = tasks.register("addSchemaInfoToBroker") {
    dependsOn(downloadSchema)
    val schemaFile = tasks.named<DownloadSchemaTask>("downloadSchema").flatMap { it.schemaFile }
    inputs.file(schemaFile)

    doLast {
        val schemaBytes = schemaFile.get().asFile.readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(schemaBytes)
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }

        val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
        infoBrokerPlugin.add("GitHub-API-Repo", project.ext.get("gh.api.repo").toString())
        infoBrokerPlugin.add("GitHub-API-Commit", project.ext.get("gh.api.commit").toString())
        infoBrokerPlugin.add("GitHub-API-SHA256", sha256)
    }
}

tasks.withType<GenerateMavenPom>().configureEach {
    dependsOn(addSchemaInfoToBroker)
}