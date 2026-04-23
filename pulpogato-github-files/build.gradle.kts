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
val githubActionsTypingRepo = project.ext["gh.actions.typing.repo"].toString()
val githubActionsTypingCommit = project.ext["gh.actions.typing.commit"].toString()

description = "Java types for GitHub file configuration schemas"

data class SchemaSpec(
    val filename: String,
    val subpackage: String,
    val sourceName: String,
    val sourceDescription: String,
    val sourceCommit: String,
    val sourceUrl: String,
)

val schemas =
    listOf(
        SchemaSpec(
            "github-action.json",
            "actions",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-action.json",
        ),
        SchemaSpec(
            "github-actions-typing.schema.json",
            "actionstyping",
            "Github-Actions-Typing",
            "typesafegithub/github-actions-typing",
            githubActionsTypingCommit,
            "https://raw.githubusercontent.com/$githubActionsTypingRepo/$githubActionsTypingCommit/github-actions-typing.schema.json",
        ),
        SchemaSpec(
            "github-discussion.json",
            "discussion",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-discussion.json",
        ),
        SchemaSpec(
            "github-funding.json",
            "funding",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-funding.json",
        ),
        SchemaSpec(
            "github-issue-config.json",
            "issueconfig",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-issue-config.json",
        ),
        SchemaSpec(
            "github-issue-forms.json",
            "issueforms",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-issue-forms.json",
        ),
        SchemaSpec(
            "github-release-config.json",
            "releases",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-release-config.json",
        ),
        SchemaSpec(
            "github-workflow.json",
            "workflows",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-workflow.json",
        ),
        SchemaSpec(
            "github-workflow-template-properties.json",
            "workflowtemplates",
            "Schemastore",
            "schemastore",
            schemastoreCommit,
            "https://raw.githubusercontent.com/$schemastoreRepo/$schemastoreCommit/src/schemas/json/github-workflow-template-properties.json",
        ),
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
            description = "Download ${spec.filename} from ${spec.sourceDescription}"
            src(spec.sourceUrl)
            dest(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
            overwrite(false)
        }

    downloadAllSchemas.configure { dependsOn(downloadTask) }
}

val addSchemaInfoToBroker =
    tasks.register<WriteInfoPropertiesTask>("addSchemaInfoToBroker") {
        description = "Writes schema metadata (checksums, source commits) to info.properties for the info broker plugin"
        group = "build setup"
        dependsOn(downloadAllSchemas)
        schemas
            .associate { it.sourceName to it.sourceCommit }
            .forEach { (sourceName, sourceCommit) ->
                staticEntries.put("$sourceName-Commit", sourceCommit)
            }
        schemas.forEach { spec ->
            checksumFiles.from(project.layout.buildDirectory.file("generated-src/main/resources/${spec.filename}"))
            val label = spec.subpackage.replaceFirstChar { it.uppercase() }
            checksumEntriesByFilename.put(spec.filename, "${spec.sourceName}-$label-SHA256")
        }
        outputFile.set(layout.buildDirectory.file("reports/schema-info.properties"))
    }

val infoPropertiesFile = addSchemaInfoToBroker.flatMap { it.outputFile }
val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
schemas
    .map { it.sourceName }
    .distinct()
    .forEach { sourceName ->
        infoBrokerPlugin.add(
            "$sourceName-Commit",
            PropertiesFileValueClosure(infoPropertiesFile.get().asFile, "$sourceName-Commit"),
        )
    }
schemas.forEach { spec ->
    val label = spec.subpackage.replaceFirstChar { it.uppercase() }
    infoBrokerPlugin.add(
        "${spec.sourceName}-$label-SHA256",
        PropertiesFileValueClosure(infoPropertiesFile.get().asFile, "${spec.sourceName}-$label-SHA256"),
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