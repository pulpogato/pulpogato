import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    java
    alias(libs.plugins.testLogger)
    id("io.github.pulpogato.build-support")
}

dependencies {
    testImplementation(libs.bundles.springBoot)
    testImplementation(project(":${rootProject.name}-rest-ghes-3.19"))
    testImplementation(project(":${rootProject.name}-rest-tests"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

testlogger {
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN_PARALLEL else ThemeType.MOCHA_PARALLEL
    slowThreshold = 5000

    showPassed = false
    showSkipped = false
    showFailed = true
}

// The Sonar plugin only derives sonar.java.binaries from the main source set, which this test-only
// module doesn't have, so SonarCloud's DBD Java sensor warns that the binaries are empty.
sonar {
    properties {
        property("sonar.java.binaries", sourceSets["test"].output.classesDirs.files)
    }
}