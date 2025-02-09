plugins {
    alias(libs.plugins.javaLibrary)
}

dependencies {
    api(libs.junit)
    api(libs.assertj)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation("javax.json:javax.json-api:1.1.4")
    implementation("org.glassfish:javax.json:1.1.4")
    implementation(project(":${rootProject.name}-common"))

    implementation(libs.reflections)
    implementation(libs.bundles.springBoot)

    implementation(libs.bundles.jackson)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
