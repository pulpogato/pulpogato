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
        file("build/generated/sources/main/io/github/pulpogato/common/PackageVersion.java").writeText(/* language=java */"""
            package io.github.pulpogato.common;
            
            import com.fasterxml.jackson.core.Version;
            import com.fasterxml.jackson.core.Versioned;
            import com.fasterxml.jackson.core.util.VersionUtil;
            
            /**
             * Package version for ${project.group}:${project.name}
             */
            public final class PackageVersion implements Versioned {
                /**
                 * Single shared instance
                 */
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