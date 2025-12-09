import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.spotless)
    alias(libs.plugins.testLogger)
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.slf4j)

    api(libs.jackson2Core)
    api(libs.jackson2Time)
    api(libs.jackson3Core)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

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

spotless {
    java {
        palantirJavaFormat()
    }
}

testlogger {
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN_PARALLEL else ThemeType.MOCHA_PARALLEL
    slowThreshold = 5000

    showPassed = false
    showSkipped = false
    showFailed = true
}
