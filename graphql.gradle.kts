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
    api(libs.jacksonCore)
}

fun getUrl(projectVariant: String): String {
    return if (projectVariant.matches(Regex("ghes-\\d+\\.\\d+"))) {
        "https://docs.github.com/public/$projectVariant/schema.docs-enterprise.graphql"
    } else {
        "https://docs.github.com/public/$projectVariant/schema.docs.graphql"
    }
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
        // Calculate SHA256 checksum of downloaded schema
        val schemaBytes = originalSchemaLocation.readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(schemaBytes)
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }
        project.ext.set("github.schema.sha256", sha256)
    }
}

val transformSchema = tasks.register<Sync>("transformSchema") {
    dependsOn(downloadSchema)
    inputs.file(originalSchemaLocation)
    outputs.file(transformedSchemaLocation)

    from(originalSchemaLocation)
    into(file("${project.layout.buildDirectory.get()}/resources/main"))
    rename("schema.original.graphqls", "schema.graphqls")

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

tasks.named<GenerateJavaTask>("generateJava") {
    dependsOn(transformSchema)

    schemaPaths = mutableListOf(transformedSchemaLocation)
    packageName = "io.github.pulpogato.graphql"
    generateClientv2 = true
    includeQueries = mutableListOf("")
    includeMutations = mutableListOf("")

    typeMapping = mutableMapOf(
        "Base64String" to "java.lang.String",
        "BigInt" to "java.math.BigInteger",
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
        delete(fileTree("${project.layout.buildDirectory.get()}/generated/sources/dgs-codegen") {
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
                        propNode.appendNode("github.schema.sha256", project.ext.get("github.schema.sha256").toString())
                    }
                }
            }
        }
    }
}

tasks.withType<GenerateMavenPom>().configureEach {
    dependsOn(downloadSchema)
}