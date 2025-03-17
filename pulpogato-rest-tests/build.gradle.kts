plugins {
    alias(libs.plugins.javaLibrary)
}

dependencies {
    api(libs.junit)
    api(libs.assertj)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.jsonApi)
    implementation(libs.glassfishJson)
    implementation(project(":${rootProject.name}-common"))

    implementation(libs.reflections)
    implementation(libs.bundles.springBoot)
    implementation(libs.jacksonYaml)

    implementation(libs.bundles.jackson)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.setIncremental(true)
}