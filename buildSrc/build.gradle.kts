buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    alias(libs.plugins.kotlin)
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
