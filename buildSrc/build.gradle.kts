buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.kotlin)
    id("org.jlleitschuh.gradle.ktlint").version("12.1.2")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.swaggerParser)
    implementation(libs.bundles.commonmark)
    implementation(libs.commonsText)
    implementation(libs.javapoet)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}