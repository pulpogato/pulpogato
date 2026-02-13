plugins {
    id("com.gradle.develocity").version("4.3.2")
}

val isDevelocityConfigEnabled =
    providers
        .gradleProperty("develocity.config.enabled")
        .map { it.toBoolean() }
        .orElse(true)

if (isDevelocityConfigEnabled.get()) {
    develocity {
        buildScan {
            termsOfUseUrl.set("https://gradle.com/terms-of-service")
            termsOfUseAgree.set("yes")
        }
    }
}

rootProject.name = "pulpogato-rest-codegen"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}