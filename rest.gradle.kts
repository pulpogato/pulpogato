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
    compileOnly(libs.jspecify)
    compileOnly(libs.springWeb)
    compileOnly(libs.springBootWebflux)
    implementation(libs.commonsLang3)

    api(project(":${rootProject.name}-common"))

    testImplementation(project(":${rootProject.name}-rest-tests"))
    testImplementation(libs.bundles.springBoot)
    testRuntimeOnly(libs.groovy)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

val variant = project.name.replace("${rootProject.name}-rest-", "")

description = "REST types for $variant"

codegen {
    packageName.set("io.github.pulpogato")
    mainDir.set(file("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen"))
    testDir.set(file("${project.layout.buildDirectory.get()}/generated/sources/test"))
    apiVersion.set(project.ext.get("gh.api.version").toString())
    projectVariant.set(variant)
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

sourceSets {
    named("main") {
        java.srcDir("${project.layout.buildDirectory.get()}/generated/sources/rest-codegen")
        resources.srcDir("${project.layout.buildDirectory.get()}/generated/resources/main")
    }
    named("test") {
        java.srcDir("${project.layout.buildDirectory.get()}/generated/sources/test")
    }
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

publishing {
    publications {
        named<MavenPublication>("nebula") {
            pom {
                withXml {
                    val root = asNode()
                    val propertiesNode = root.get("properties") as groovy.util.NodeList
                    if (propertiesNode.isNotEmpty()) {
                        val propNode = propertiesNode.first() as groovy.util.Node
                        propNode.appendNode("gh.api.version", project.ext.get("gh.api.version").toString())
                        propNode.appendNode("github.api.sha256", project.ext.get("github.api.sha256").toString())
                    }
                }
            }
        }
    }
}