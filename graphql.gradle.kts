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
}

dependencies {
    api(libs.jackson3Core)
    api(project(":pulpogato-common"))

    compileOnly(libs.jakartaAnnotations)

    testImplementation(libs.bundles.springBoot)
    testImplementation(libs.dgsClient)
    testImplementation(project(":${rootProject.name}-rest-tests"))
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

sourceSets {
    named("main") {
        resources.srcDir(layout.buildDirectory.dir("generated-src/main/resources"))
    }
}

val originalSchemaLocation = layout.buildDirectory.file("generated-src/main/resources/schema.graphqls")
val transformedSchemaLocation = layout.buildDirectory.file("schema/transformed/schema.graphqls")

val downloadSchema =
    tasks.register<Download>("downloadSchema") {
        group = "code generation"
        description = "Downloads the GitHub GraphQL schema."
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
        group = "code generation"
        description = "Transforms the GitHub GraphQL schema for code generation."
        dependsOn(downloadSchema)
        inputSchema.set(originalSchemaLocation)
        outputSchema.set(transformedSchemaLocation)
    }

val schemaInfoFile = project.layout.buildDirectory.file("reports/schema-info.properties")

val calculateSchemaChecksum =
    tasks.register<WriteInfoPropertiesTask>("calculateSchemaChecksum") {
        group = "publishing"
        description = "Calculates the checksum of the GitHub GraphQL schema."
        dependsOn(downloadSchema)
        dependsOn(tasks.processResources)
        checksumFiles.from(originalSchemaLocation)
        checksumEntriesByFilename.put("schema.graphqls", "GitHub-Schema-SHA256")
        outputFile.set(schemaInfoFile)
    }

val infoBrokerPlugin = project.plugins.getPlugin(InfoBrokerPlugin::class.java)
infoBrokerPlugin.add("GitHub-Schema-SHA256", PropertiesFileValueClosure(schemaInfoFile.get().asFile, "GitHub-Schema-SHA256"))

tasks.named<GenerateJavaTask>("generateJava") {
    description = "Generates Java code from the GitHub GraphQL schema."
    dependsOn(transformSchema)

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
        PatchDgsGeneratedSourcesAction(
            layout.buildDirectory
                .dir("generated/sources/dgs-codegen")
                .get()
                .asFile,
        ),
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
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