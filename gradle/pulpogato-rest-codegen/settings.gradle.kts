rootProject.name = "pulpogato-rest-codegen"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}