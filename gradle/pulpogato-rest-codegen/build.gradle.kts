buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.swaggerParser)
    implementation(libs.bundles.commonmark)
    implementation(libs.commonsText)
    implementation(libs.javapoet)
    implementation(libs.spotless)
    implementation(libs.palantirJavaFormat)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

gradlePlugin {
    val restCodegen by plugins.creating {
        id = "io.github.pulpogato.rest-codegen"
        implementationClass = "io.github.pulpogato.restcodegen.RestCodegenPlugin"
    }
}