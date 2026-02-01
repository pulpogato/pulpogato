import com.adarshr.gradle.testlogger.theme.ThemeType

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.swaggerParser)
    implementation(libs.jackson3Core)
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
    val issueStatuses by plugins.creating {
        id = "io.github.pulpogato.issue-statuses"
        implementationClass = "io.github.pulpogato.issues.IssueStatusesPlugin"
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
    junit5PluginVersion.set(libs.versions.pitestJunit5Plugin)
    pitestVersion.set(libs.versions.pitest)
    mutators.set(setOf("ALL"))
    outputFormats.set(setOf("XML", "HTML"))
    targetClasses.set(listOf("io.github.pulpogato.restcodegen.*"))
    excludedTestClasses.set(listOf("io.github.pulpogato.restcodegen.RestCodegenPluginTest"))
}

spotless {
    kotlin {
        ktlint()
        target("src/**/*.kt", "*.kts")
        targetExclude("build/**")
    }
}