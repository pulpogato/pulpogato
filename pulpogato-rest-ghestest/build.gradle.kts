import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    java
    alias(libs.plugins.testLogger)
}

dependencies {
    testImplementation(libs.bundles.springBoot)
    testImplementation(project(":${rootProject.name}-rest-ghes-3.17"))
    testImplementation(project(":${rootProject.name}-rest-tests"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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