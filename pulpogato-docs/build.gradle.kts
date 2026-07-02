plugins {
    alias(libs.plugins.asciidoctor)
}

val asciidoctorExt = configurations.create("asciidoctorExt")

dependencies {
    asciidoctorExt("com.puravida-software.asciidoctor:asciidoctor-extensions:3.0.0")
}

tasks.asciidoctor {
    notCompatibleWithConfigurationCache("Asciidoctor Gradle Plugin is not compatible with the configuration cache.")
    sourceDir(file("src"))
    sources {
        include("index.adoc")
    }
    baseDirFollowsSourceFile()
    setOutputDir(file("build"))
    outputOptions {
        separateOutputDirs = false
    }
    configurations("asciidoctorExt")
}