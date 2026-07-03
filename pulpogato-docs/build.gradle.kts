plugins {
    alias(libs.plugins.asciidoctor)
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
    attributes(
        mapOf(
            "docinfo" to "shared",
            "docinfodir" to file("src/docinfo").absolutePath,
        ),
    )
}