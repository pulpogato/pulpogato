plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.slf4j)

    api(libs.bundles.jackson)
    
    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.mockitoJunitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)

    mockitoAgent(libs.mockito) { isTransitive = false }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

description = "Common utilities for Pulpogato REST types"

tasks.withType<JavaCompile> {
    options.setIncremental(true)
}

tasks.withType<Test> {
    useJUnitPlatform()
}