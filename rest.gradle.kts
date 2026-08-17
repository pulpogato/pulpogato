import com.adarshr.gradle.testlogger.theme.ThemeType
import io.github.pulpogato.buildsupport.PropertiesFileValueClosure
import io.github.pulpogato.buildsupport.WriteInfoPropertiesTask
import io.github.pulpogato.restcodegen.DownloadSchemaTask
import nebula.plugin.info.InfoBrokerPlugin
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    id("io.github.pulpogato.build-support")
    id("io.github.pulpogato.rest-codegen")
    alias(libs.plugins.roseau)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
}

dependencies {
    errorprone(libs.errorprone)
    errorprone(libs.nullaway)

    compileOnly(libs.jspecify)
    compileOnly(libs.springBootWeb)
    compileOnly(libs.springBootWebflux)
    compileOnly(libs.springWeb)

    implementation(libs.commonsLang3)

    api(project(":${rootProject.name}-common"))

    testImplementation(libs.bundles.springBoot)
    testImplementation(project(":${rootProject.name}-rest-tests"))

    testRuntimeOnly(libs.groovy)

    testCompileOnly(libs.jetbrainsAnnotations)
    testCompileOnly(libs.lombok)

    testAnnotationProcessor(libs.lombok)
}

val variant = project.name.replace("${rootProject.name}-rest-", "")

description = "REST types for $variant"

// Every REST variant generates identical package/class names (codegen.packageName is uniformly
// "io.github.pulpogato"), which makes Sonar's JaCoCo coverage sensor unable to tell one variant's
// generated file from another's and report spurious "not found in project sources" warnings.
// sonar.skip does not prevent this (it doesn't affect the auto-detected sonar.sources/sonar.tests
// of other modules), so explicitly empty this module's own contribution instead. Since coverage is
// representative across variants, only the fpt (default) variant is left in the scan.
if (variant != "fpt") {
    sonar {
        properties {
            property("sonar.sources", "")
            property("sonar.tests", "")
        }
    }
}

codegen {
    packageName.set("io.github.pulpogato")
    // Sonar's Gradle plugin unconditionally drops any sonar.sources/sonar.tests entry whose path
    // contains the literal substring "build/generated" (org.sonarqube.gradle.SonarTask.containsValidSources),
    // regardless of what any of our own sonar.properties overrides say. That silently excluded even
    // the fpt variant's generated code from every scan so far, so it lives under "codegen-src" instead.
    mainDir.set(file("${project.layout.buildDirectory.get()}/codegen-src/main/java"))
    testDir.set(file("${project.layout.buildDirectory.get()}/codegen-src/test/java"))
    apiCommit.set(project.ext.get("gh.api.commit").toString())
    apiVersion.set(project.ext.get("gh.api.version").toString())
    apiRepository.set(project.ext.get("gh.api.repo").toString())
    projectVariant.set(variant)
    projectVersion.set(project.version.toString())
}

sourceSets {
    named("main") {
        java.srcDir("${project.layout.buildDirectory.get()}/codegen-src/main/java")
        resources.srcDir("${project.layout.buildDirectory.get()}/generated-src/main/resources")
    }
    named("test") {
        java.srcDir("${project.layout.buildDirectory.get()}/codegen-src/test/java")
        // testResourcesDir in RestCodegenPlugin is derived from codegen.testDir's parent, so this
        // must track the codegen-src rename above — the codegen task writes large example JSON
        // fixtures here that tests load from the classpath at runtime.
        resources.srcDir("${project.layout.buildDirectory.get()}/codegen-src/test/resources")
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

nullaway {
    // Rely on JSpecify @NullMarked package-info.java files rather than an AnnotatedPackages allowlist.
    // Generated sources aren't null-marked yet, so this is currently a no-op until that lands.
    onlyNullMarked = true
}

tasks.withType<JavaCompile> {
    options.isIncremental = true
    // Error Prone's analysis on the large volume of generated sources needs more heap than the
    // forked javac's default, or compilation OOMs.
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("-Xmx4g")
    options.errorprone {
        disableAllChecks = true
        nullaway { error() }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    // Test sources aren't @NullMarked, so there's nothing for NullAway to check here.
    options.errorprone.enabled = false
    // Error Prone is off for this task, so it doesn't need the forked 4g heap either.
    options.isFork = false
}

val addSchemaInfoToBroker =
    tasks.register<WriteInfoPropertiesTask>("addSchemaInfoToBroker") {
        description = "Writes schema metadata (checksum, repo, commit, version) to info.properties for the info broker plugin"
        group = "build setup"
        dependsOn(downloadSchema)
        val schemaFile = tasks.named<DownloadSchemaTask>("downloadSchema").flatMap { theTask -> theTask.schemaFile }
        checksumFiles.from(schemaFile)
        checksumEntriesByFilename.put("github.schema.json", "GitHub-API-SHA256")
        staticEntries.put(
            "GitHub-API-Repo",
            project.ext.get("gh.api.repo").toString(),
        )
        staticEntries.put(
            "GitHub-API-Commit",
            project.ext.get("gh.api.commit").toString(),
        )
        staticEntries.put(
            "GitHub-API-Version",
            project.ext.get("gh.api.version").toString(),
        )
        outputFile.set(layout.buildDirectory.file("reports/schema-info.properties"))
    }

val infoPropertiesFile = addSchemaInfoToBroker.flatMap { it.outputFile }
val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
listOf("GitHub-API-Repo", "GitHub-API-Commit", "GitHub-API-Version", "GitHub-API-SHA256").forEach { key ->
    infoBrokerPlugin.add(key, PropertiesFileValueClosure(infoPropertiesFile.get().asFile, key))
}

tasks.withType<Jar>().configureEach {
    dependsOn(addSchemaInfoToBroker)
}

tasks.named("writeManifestProperties").configure {
    dependsOn(addSchemaInfoToBroker)
}

tasks.withType<GenerateMavenPom>().configureEach {
    dependsOn(addSchemaInfoToBroker)
}