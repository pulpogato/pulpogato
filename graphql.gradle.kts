import com.netflix.graphql.dgs.codegen.gradle.GenerateJavaTask
import de.undercouch.gradle.tasks.download.Download
import java.security.MessageDigest

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.dgs)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.download)
}

dependencies {
    api(project(":pulpogato-common"))
    api(libs.jackson3Core)
    compileOnly(libs.jakartaAnnotations)
}

fun getUrl(projectVariant: String): String {
    val prefix = "https://docs.github.com/public"
    val filename = when {
        projectVariant.startsWith("ghes-") -> "schema.docs-enterprise.graphql"
        else -> "schema.docs.graphql"
    }
    return "$prefix/$projectVariant/$filename"
}

val projectVariant = project.name.replace("${rootProject.name}-graphql-", "")

description = "GraphQL types for $projectVariant"

val originalSchemaLocation = file("${project.layout.buildDirectory.get()}/resources/main/schema.graphqls")
val transformedSchemaLocation = file("${project.layout.buildDirectory.get()}/resources/main-tmp/schema.graphqls")

val downloadSchema = tasks.register<Download>("downloadSchema") {
    src(getUrl(projectVariant))
    dest(originalSchemaLocation)
    onlyIfModified(true)
    tempAndMove(true)
    useETag("all")
    quiet(true)

    inputs.property("url", getUrl(projectVariant))
    outputs.file(originalSchemaLocation)

    doLast {
        if (!originalSchemaLocation.exists()) {
            throw GradleException("Failed to download schema from ${getUrl(projectVariant)}")
        }
    }
}

val transformSchema = tasks.register<Sync>("transformSchema") {
    dependsOn(downloadSchema)
    dependsOn(tasks.processResources)
    inputs.file(originalSchemaLocation)
    outputs.file(transformedSchemaLocation)

    from(originalSchemaLocation)
    into(transformedSchemaLocation.parent)
    rename { transformedSchemaLocation.name }

    filter { currentLine ->
        currentLine
            .replace(Regex("<(https?:.+?)>")) {
                "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>"
            }
            .replace("< ", "&lt; ")
            .replace("> ", "&gt; ")
            .replace("<= ", "&lt;= ")
            .replace(">= ", "&gt;= ")
            .replace("Query implements Node", "Query")
    }
}

val checksumFile = project.layout.buildDirectory.file("schema.sha256")

val calculateSchemaChecksum = tasks.register("calculateSchemaChecksum") {
    dependsOn(downloadSchema)
    dependsOn(tasks.processResources)
    inputs.file(originalSchemaLocation)
    outputs.file(checksumFile)

    doLast {
        val schemaBytes = originalSchemaLocation.readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(schemaBytes)
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }
        checksumFile.get().asFile.writeText(sha256)
    }
}

tasks.named<GenerateJavaTask>("generateJava") {
    dependsOn(transformSchema)

    schemaPaths = mutableListOf(transformedSchemaLocation)
    packageName = "io.github.pulpogato.graphql"
    generateClientv2 = true
    includeQueries = mutableListOf("")
    includeMutations = mutableListOf("")

    addDeprecatedAnnotation = true
    addGeneratedAnnotation = true
    disableDatesInGeneratedAnnotation = true

    typeMapping = mutableMapOf(
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

    doLast {
        delete(fileTree(layout.buildDirectory.dir("generated/sources/dgs-codegen")) {
            include("**/DgsConstants.java")
        })
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    dependsOn(downloadSchema)
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        addStringOption("encoding", "UTF-8")
        addStringOption("charSet", "UTF-8")
    }
}

publishing {
    publications {
        named<MavenPublication>("nebula") {
            pom {
                withXml {
                    val root = asNode()
                    val propertiesNode = root.get("properties") as groovy.util.NodeList
                    if (propertiesNode.isNotEmpty()) {
                        val propNode = propertiesNode.first() as groovy.util.Node
                        propNode.appendNode("github.schema.sha256", checksumFile.get().asFile.readText())
                    }
                }
            }
        }
    }
}

tasks.withType<GenerateMavenPom>().configureEach {
    dependsOn(calculateSchemaChecksum)
    dependsOn(downloadSchema)
}