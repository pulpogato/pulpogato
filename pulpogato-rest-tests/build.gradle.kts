plugins {
    alias(libs.plugins.javaLibrary)
    id("io.github.pulpogato.issue-statuses")
    alias(libs.plugins.spotless)
}

dependencies {
    api(libs.assertj)
    api(libs.junit)

    compileOnly(libs.jetbrainsAnnotations)
    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)

    implementation(libs.bundles.springBoot)
    implementation(libs.glassfishJson)
    implementation(libs.httpclient5)
    implementation(libs.jackson2Time)
    implementation(libs.jackson3Core)
    implementation(libs.jackson3Yaml)
    implementation(libs.jsonApi)
    implementation(libs.junitPlatformLauncher)
    implementation(libs.reflections)
    implementation(project(":${rootProject.name}-common"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.isIncremental = true
}

spotless {
    java {
        palantirJavaFormat()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}