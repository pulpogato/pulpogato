import com.adarshr.gradle.testlogger.theme.ThemeType
import de.undercouch.gradle.tasks.download.Download
import nebula.plugin.info.InfoBrokerPlugin
import java.security.MessageDigest

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.download)
    alias(libs.plugins.testLogger)
    id("io.github.pulpogato.github-files-codegen")
}

val schemastoreCommit: String by project

description = "Java types for GitHub file configuration schemas"

data class SchemaSpec(
    val filename: String,
    val subpackage: String,
)

val schemas =
    listOf(
        SchemaSpec("github-action.json", "actions"),
        SchemaSpec("github-workflow.json", "workflows"),
        SchemaSpec("github-release-config.json", "releases"),
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
            src("https://raw.githubusercontent.com/schemastore/schemastore/$schemastoreCommit/src/schemas/json/${spec.filename}")
            dest(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
            overwrite(false)
        }

    downloadAllSchemas.configure { dependsOn(downloadTask) }
}

val addSchemaInfoToBroker =
    tasks.register("addSchemaInfoToBroker") {
        dependsOn(downloadAllSchemas)
        schemas.forEach { spec ->
            inputs.file(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
        }

        doLast {
            val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
            infoBrokerPlugin.add("Schemastore-Commit", schemastoreCommit)

            schemas.forEach { spec ->
                val schemaBytes =
                    project.layout.buildDirectory
                        .file("generated-src/main/resources/${spec.filename}")
                        .get()
                        .asFile
                        .readBytes()
                val digest = MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(schemaBytes)
                val sha256 = hashBytes.joinToString("") { theByte -> "%02x".format(theByte) }
                val label = spec.subpackage.replaceFirstChar { it.uppercase() }
                infoBrokerPlugin.add("Schemastore-$label-SHA256", sha256)
            }
        }
    }!!

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

    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)

    testImplementation(libs.jetbrainsAnnotations)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson2Yaml)
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

tasks.withType<Test> {
    useJUnitPlatform()
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