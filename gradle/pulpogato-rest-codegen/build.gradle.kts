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
    plugins {
        create("buildSupport") {
            id = "io.github.pulpogato.build-support"
            implementationClass = "io.github.pulpogato.buildsupport.BuildSupportPlugin"
        }
        create("restCodegen") {
            id = "io.github.pulpogato.rest-codegen"
            implementationClass = "io.github.pulpogato.restcodegen.RestCodegenPlugin"
        }
        create("issueStatuses") {
            id = "io.github.pulpogato.issue-statuses"
            implementationClass = "io.github.pulpogato.issues.IssueStatusesPlugin"
        }
        create("githubFilesCodegen") {
            id = "io.github.pulpogato.github-files-codegen"
            implementationClass = "io.github.pulpogato.githubfilescodegen.GithubFilesCodegenPlugin"
        }
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