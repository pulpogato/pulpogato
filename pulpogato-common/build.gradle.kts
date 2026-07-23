import com.adarshr.gradle.testlogger.theme.ThemeType
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    errorprone(libs.errorprone)
    errorprone(libs.nullaway)

    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)
    compileOnly(libs.micrometerCore)
    api(libs.micrometerObservation)
    compileOnly(libs.springBootWebflux)
    compileOnly(libs.springWeb)

    annotationProcessor(libs.lombok)

    implementation(libs.bouncycastle)
    implementation(libs.javaJwt)
    implementation(libs.slf4j)

    api(libs.jackson2Core)
    api(libs.jackson2Time)
    api(libs.jackson3Core)

    testCompileOnly(libs.lombok)

    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.assertj)
    testImplementation(libs.junit)
    testImplementation(libs.micrometerCore)
    testImplementation(libs.micrometerObservation)
    testImplementation(libs.micrometerObservationTest)
    testImplementation(libs.mockito)
    testImplementation(libs.mockitoJunitJupiter)
    testImplementation(libs.reactorTest)
    testImplementation(libs.springBootWebflux)
    testImplementation(libs.springWeb)

    testRuntimeOnly(libs.junitPlatformLauncher)

    mockitoAgent(libs.mockito) { isTransitive = false }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

description = "Common utilities for Pulpogato REST types"

nullaway {
    onlyNullMarked = true
}

tasks.withType<JavaCompile> {
    options.isIncremental = true
    options.errorprone {
        disableAllChecks = true
        nullaway { error() }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    // Test sources aren't @NullMarked, so there's nothing for NullAway to check here.
    options.errorprone.enabled = false
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
    junit5PluginVersion.set(libs.versions.pitestJunit5Plugin)
    pitestVersion.set(libs.versions.pitest)
    mutators.set(setOf("ALL"))
    outputFormats.set(setOf("XML", "HTML", "CSV"))
}