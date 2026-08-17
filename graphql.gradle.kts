import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import de.undercouch.gradle.tasks.download.Download
import io.github.pulpogato.buildsupport.PatchDgsGeneratedSourcesAction
import io.github.pulpogato.buildsupport.PropertiesFileValueClosure
import io.github.pulpogato.buildsupport.TransformGraphqlSchemaTask
import io.github.pulpogato.buildsupport.WriteInfoPropertiesTask
import nebula.plugin.info.InfoBrokerPlugin

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.dgs)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.download)
    id("io.github.pulpogato.build-support")
    alias(libs.plugins.roseau)
}

dependencies {
    api(project(":pulpogato-common"))

    compileOnly(libs.jakartaAnnotations)

    testImplementation(libs.bundles.springBoot)
    testImplementation(libs.dgsClient)
    testImplementation(project(":${rootProject.name}-rest-tests"))
    testRuntimeOnly(libs.jackson2Jdk8)
    testRuntimeOnly(libs.jackson3ModuleKotlin)
    testRuntimeOnly(libs.jacksonModuleKotlin)
    testRuntimeOnly(libs.jacksonModuleParameterNames)
}

fun getUrl(projectVariant: String): String {
    val prefix = "https://docs.github.com/public"
    val filename =
        when {
            projectVariant.startsWith("ghes-") -> "schema.docs-enterprise.graphql"
            else -> "schema.docs.graphql"
        }
    return "$prefix/$projectVariant/$filename"
}

val projectVariant = project.name.replace("${rootProject.name}-graphql-", "")

description = "GraphQL types for $projectVariant"

// Every GraphQL variant generates identical package/class names (packageName is uniformly
// "io.github.pulpogato.graphql"), which makes Sonar's JaCoCo coverage sensor unable to tell one
// variant's generated file from another's and report spurious "not found in project sources"
// warnings. sonar.skip does not prevent this (it doesn't affect the auto-detected
// sonar.sources/sonar.tests of other modules), so explicitly empty this module's own contribution
// instead. Since coverage is representative across variants, only the fpt (default) variant is left
// in the scan.
if (projectVariant != "fpt") {
    sonar {
        properties {
            property("sonar.sources", "")
            property("sonar.tests", "")
        }
    }
}

sourceSets {
    named("main") {
        resources.srcDir(layout.buildDirectory.dir("generated-src/main/resources"))
    }
}

val originalSchemaLocation = layout.buildDirectory.file("generated-src/main/resources/schema.graphqls")
val transformedSchemaLocation = layout.buildDirectory.file("schema/transformed/schema.graphqls")

val downloadSchema =
    tasks.register<Download>("downloadSchema") {
        description = "Downloads the GitHub GraphQL schema from the documentation"
        group = "code generation"
        src(getUrl(projectVariant))
        dest(originalSchemaLocation)
        onlyIfModified(true)
        tempAndMove(true)
        useETag("all")
        quiet(true)

        inputs.property("url", getUrl(projectVariant))
        outputs.file(originalSchemaLocation)
    }

val transformSchema =
    tasks.register<TransformGraphqlSchemaTask>("transformSchema") {
        description = "Transforms the downloaded GraphQL schema to remove incompatible types"
        group = "code generation"
        dependsOn(downloadSchema)
        inputSchema.set(originalSchemaLocation)
        outputSchema.set(transformedSchemaLocation)
    }

val schemaInfoFile = project.layout.buildDirectory.file("reports/schema-info.properties")

val calculateSchemaChecksum =
    tasks.register<WriteInfoPropertiesTask>("calculateSchemaChecksum") {
        description = "Calculates the SHA256 checksum of the GraphQL schema and writes it to info.properties"
        group = "build setup"
        dependsOn(downloadSchema)
        dependsOn(tasks.processResources)
        checksumFiles.from(originalSchemaLocation)
        checksumEntriesByFilename.put("schema.graphqls", "GitHub-Schema-SHA256")
        outputFile.set(schemaInfoFile)
    }

val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
infoBrokerPlugin.add("GitHub-Schema-SHA256", PropertiesFileValueClosure(schemaInfoFile.get().asFile, "GitHub-Schema-SHA256"))

tasks.named<GenerateJavaTask>("generateJava") {
    dependsOn(transformSchema)

    // Sonar's Gradle plugin unconditionally drops any sonar.sources/sonar.tests entry whose path
    // contains the literal substring "build/generated" (org.sonarqube.gradle.SonarTask.containsValidSources),
    // regardless of what any of our own sonar.properties overrides say. The DGS codegen plugin defaults
    // its output under "<generatedSourcesDir>/generated/sources/dgs-codegen", which matches that filter
    // and silently excluded even the fpt variant's generated code from every scan so far.
    generatedSourcesDir = "${project.layout.buildDirectory.get()}/codegen"

    schemaPaths = mutableListOf(transformedSchemaLocation.get().asFile)
    packageName = "io.github.pulpogato.graphql"
    generateClientv2 = true

    addDeprecatedAnnotation = true
    addGeneratedAnnotation = true
    disableDatesInGeneratedAnnotation = true

    typeMapping =
        mutableMapOf(
            "Base64String" to "java.lang.String",
            "BigInt" to "java.math.BigInteger",
            "CustomPropertyValue" to "io.github.pulpogato.common.SingularOrPlural<java.lang.String>",
            "Date" to "java.time.LocalDate",
            "DateTime" to "java.time.OffsetDateTime",
            "GitObjectID" to "java.lang.String",
            "GitRefname" to "java.lang.String",
            "GitSSHRemote" to "java.lang.String",
            "GitTimestamp" to "java.time.OffsetDateTime",
            "HTML" to "java.lang.String",
            "PreciseDateTime" to "java.time.OffsetDateTime",
            "URI" to "java.net.URI",
            "X509Certificate" to "java.lang.String",
        )
    doLast(
        PatchDgsGeneratedSourcesAction(getOutputDir()),
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    dependsOn(downloadSchema)
}

tasks.withType<Jar>().configureEach {
    dependsOn(calculateSchemaChecksum)
}

tasks.named("writeManifestProperties").configure {
    dependsOn(calculateSchemaChecksum)
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        addStringOption("encoding", "UTF-8")
        addStringOption("charSet", "UTF-8")
    }
}

tasks.withType<GenerateMavenPom>().configureEach {
    dependsOn(calculateSchemaChecksum)
    dependsOn(downloadSchema)
}