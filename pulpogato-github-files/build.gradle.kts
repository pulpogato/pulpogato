import com.adarshr.gradle.testlogger.theme.ThemeType
import de.undercouch.gradle.tasks.download.Download
import io.github.pulpogato.buildsupport.PropertiesFileValueClosure
import io.github.pulpogato.buildsupport.WriteInfoPropertiesTask
import nebula.plugin.info.InfoBrokerPlugin

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.download)
    alias(libs.plugins.testLogger)
    id("io.github.pulpogato.github-files-codegen")
}

val schemastoreRepo = project.ext["schemastore.repo"].toString()
val schemastoreCommit = project.ext["schemastore.commit"].toString()

description = "Java types for GitHub file configuration schemas"

data class SchemaSpec(
    val filename: String,
    val subpackage: String,
)

val schemas =
    listOf(
        SchemaSpec("github-action.json", "actions"),
        SchemaSpec("github-release-config.json", "releases"),
        SchemaSpec("github-workflow.json", "workflows"),
    )

val downloadAllSchemas =
    tasks.register("downloadAllSchemas") {
        group = "code generation"
        description = "Download all schemastore JSON schemas"
    }

schemas.forEach { spec ->
    val taskSuffix = spec.subpackage.replaceFirstChar { it.uppercase() }

    val downloadTask =
        tasks.register<Download>("downloadSchema$taskSuffix") {
            group = "code generation"
            description = "Download ${spec.filename} from schemastore"
            src("https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/${spec.filename}")
            dest(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
            overwrite(false)
        }

    downloadAllSchemas.configure { dependsOn(downloadTask) }
}

val addSchemaInfoToBroker =
    tasks.register<WriteInfoPropertiesTask>("addSchemaInfoToBroker") {
        dependsOn(downloadAllSchemas)
        staticEntries.put("Schemastore-Commit", schemastoreCommit)
        schemas.forEach { spec ->
            checksumFiles.from(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
            val label = spec.subpackage.replaceFirstChar { it.uppercase() }
            checksumEntriesByFilename.put(spec.filename, "Schemastore-$label-SHA256")
        }
        outputFile.set(layout.buildDirectory.file("reports/schema-info.properties"))
    }

val infoPropertiesFile = addSchemaInfoToBroker.flatMap { it.outputFile }
val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
infoBrokerPlugin.add("Schemastore-Commit", PropertiesFileValueClosure(infoPropertiesFile.get().asFile, "Schemastore-Commit"))
schemas.forEach { spec ->
    val label = spec.subpackage.replaceFirstChar { it.uppercase() }
    infoBrokerPlugin.add(
        "Schemastore-$label-SHA256",
        PropertiesFileValueClosure(infoPropertiesFile.get().asFile, "Schemastore-$label-SHA256"),
    )
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

githubFilesCodegen {
    schemas.forEach { spec ->
        schemaFiles.from(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
    }
    packageName.set("io.github.pulpogato.githubfiles")
    schemaPackageMapping.set(
        schemas.associate { it.filename to it.subpackage },
    )
}

tasks.named("generateGithubFilesTypes") {
    dependsOn(downloadAllSchemas)
}

tasks.compileJava { dependsOn("generateGithubFilesTypes") }
tasks.processResources { dependsOn(downloadAllSchemas) }
tasks.named("sourcesJar") { dependsOn("generateGithubFilesTypes") }
tasks.named("javadocJar") { dependsOn("generateGithubFilesTypes") }

sourceSets {
    named("main") {
        java.srcDir(project.layout.buildDirectory.dir("generated-src/main/java"))
        resources.srcDir(project.layout.buildDirectory.dir("generated-src/main/resources"))
    }
}

dependencies {
    api(project(":pulpogato-common"))
    api(libs.jackson2Core)
    api(libs.jackson2Time)
    api(libs.jackson3Core)

    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)

    testImplementation(libs.jetbrainsAnnotations)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson2Yaml)
    testImplementation(libs.jackson3Yaml)
    testImplementation(libs.junit)

    testRuntimeOnly(libs.junitPlatformLauncher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.isIncremental = true
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        addStringOption("encoding", "UTF-8")
        addStringOption("charSet", "UTF-8")
    }
}

testlogger {
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN_PARALLEL else ThemeType.MOCHA_PARALLEL
    slowThreshold = 5000

    showPassed = false
    showSkipped = false
    showFailed = true
}