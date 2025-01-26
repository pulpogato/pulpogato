plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
}

sourceSets {
    main {
        java {
            srcDir("build/generated/sources/main")
        }
    }
}

dependencies {
    compileOnly(libs.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.slf4j)

    api(libs.bundles.jackson)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val generateCode by tasks.registering {
    group = "codegen"
    description = "Generate code from schema"
    doLast {
        file("build/generated/sources/main/io/github/pulpogato/common").mkdirs()
        file("build/generated/sources/main/io/github/pulpogato/common/PackageVersion.java").writeText("""
            package io.github.pulpogato.common;
            
            import com.fasterxml.jackson.core.Version;
            import com.fasterxml.jackson.core.Versioned;
            import com.fasterxml.jackson.core.util.VersionUtil;
            
            public final class PackageVersion implements Versioned {
                public static final Version VERSION = VersionUtil.parseVersion("${project.version}", "io.github.pulpogato", "pulpogato-common");
            
                public PackageVersion() {
                }
            
                public Version version() {
                    return VERSION;
                }
            }
        """.trimIndent())
    }
}

tasks.named("compileJava") {
    dependsOn(generateCode)
}