import com.adarshr.gradle.testlogger.theme.ThemeType

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
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

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
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

tasks.withType<Test> {
    useJUnitPlatform()
}

testlogger {
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN else ThemeType.MOCHA
    slowThreshold = 5000

    showPassed = false
    showSkipped = true
    showFailed = true
}

pitest {
    timestampedReports = false
    junit5PluginVersion.set(libs.versions.pitestJunit5Plugin) // Look here for latest version - https://github.com/pitest/pitest-junit5-plugin/tags
    pitestVersion.set(libs.versions.pitest) // Look here for latest version - https://github.com/hcoles/pitest/releases
    mutators.set(setOf("ALL"))
    outputFormats.set(setOf("XML", "HTML"))
    targetClasses.set(listOf("io.github.pulpogato.restcodegen.*"))
    excludedTestClasses.set(listOf("io.github.pulpogato.restcodegen.RestCodegenPluginTest"))
}