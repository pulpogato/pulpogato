plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
}

dependencies {
    compileOnly(libs.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.slf4j)

    api(libs.bundles.jackson)
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