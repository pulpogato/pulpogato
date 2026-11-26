import com.adarshr.gradle.testlogger.theme.ThemeType
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.javaLibrary)
    alias(libs.plugins.waenaPublished)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.pitest)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
    alias(libs.plugins.roseau)
    id("io.github.pulpogato.build-support")
}

val mockitoAgent = configurations.create("mockitoAgent")

fun coordinates(dependency: Provider<MinimalExternalModuleDependency>) = dependency.map { "${it.module.group}:${it.module.name}" }

val testSupport = configurations.dependencyScope("testSupport")
configurations.testImplementation {
    extendsFrom(testSupport.get())
}

val reactorBaseline = libs.versions.commonReactorBaseline.get()
listOf(configurations.compileClasspath, configurations.testCompileClasspath).forEach { classpath ->
    classpath.configure {
        resolutionStrategy.force("io.projectreactor:reactor-core:$reactorBaseline", "io.projectreactor:reactor-test:$reactorBaseline")
    }
}

val springFrameworkBom = "org.springframework:spring-framework-bom"
val springFrameworkBaseline = libs.versions.commonSpringBaseline.get()

dependencies {
    components {
        withModule("io.projectreactor:reactor-core") {
            val reactorCoreVersion = id.version
            allVariants {
                withDependencyConstraints {
                    // Keep StepVerifier on the Reactor version selected by the WebFlux runtime.
                    add("io.projectreactor:reactor-test:$reactorCoreVersion")
                }
            }
        }
    }

    errorprone(libs.errorprone)
    errorprone(libs.nullaway)

    compileOnly(libs.jspecify)
    compileOnly(libs.lombok)
    compileOnly(libs.commonMicrometerCoreBaseline)
    api(libs.commonMicrometerObservationBaseline)
    compileOnly(libs.commonSpringBootWebfluxBaseline)
    compileOnly(libs.commonSpringWebBaseline)
    compileOnly(libs.commonSpringWebfluxBaseline)
    compileOnly(enforcedPlatform("$springFrameworkBom:$springFrameworkBaseline"))

    annotationProcessor(libs.lombok)

    implementation(libs.bouncycastle)
    implementation(libs.javaJwt)
    implementation(libs.slf4j)

    api(libs.commonJackson2CoreBaseline)
    api(libs.commonJackson2TimeBaseline)
    api(libs.commonJackson3CoreBaseline)

    testCompileOnly(libs.lombok)
    testCompileOnly(enforcedPlatform("$springFrameworkBom:$springFrameworkBaseline"))

    testAnnotationProcessor(libs.lombok)

    add(testSupport.name, libs.assertj)
    add(testSupport.name, libs.junit)
    add(testSupport.name, libs.junitPlatformLauncher)
    add(testSupport.name, libs.mockito)
    add(testSupport.name, libs.mockitoJunitJupiter)

    testImplementation(libs.commonMicrometerCoreBaseline)
    testImplementation(libs.commonMicrometerObservationTestBaseline)
    testImplementation(libs.commonReactorTestBaseline)
    testImplementation(libs.commonSpringBootWebfluxBaseline)
    testImplementation(libs.commonSpringWebBaseline)
    testImplementation(libs.commonSpringWebfluxBaseline)

    // The standard test task uses the catalog runtime with the baseline-compiled classes.
    testRuntimeOnly(libs.commonJackson2CoreLatest)
    testRuntimeOnly(libs.commonJackson2TimeLatest)
    testRuntimeOnly(libs.commonJackson3CoreLatest)
    testRuntimeOnly(libs.commonMicrometerCoreLatest)
    testRuntimeOnly(libs.commonMicrometerObservationLatest)
    testRuntimeOnly(libs.commonMicrometerObservationTestLatest)
    testRuntimeOnly(libs.commonSpringBootWebfluxLatest)
    testRuntimeOnly(libs.commonSpringWebLatest)
    testRuntimeOnly(libs.commonSpringWebfluxLatest)

    mockitoAgent(libs.mockito) { isTransitive = false }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

description = "Common utilities for Pulpogato REST types"

nullaway {
    onlyNullMarked = true
}

tasks.withType<JavaCompile> {
    options.isIncremental = true
    options.errorprone {
        disableAllChecks = true
        nullaway { error() }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    // Test sources aren't @NullMarked, so there's nothing for NullAway to check here.
    options.errorprone.enabled = false
}

fun compatibilityVersions(
    family: String,
    vararg defaults: String,
): List<String> {
    val property = "common${family}Versions"
    val additionalVersions =
        providers
            .gradleProperty(property)
            .orNull
            ?.split(',')
            ?.map(String::trim)
            .orEmpty()
    require(additionalVersions.all { it.matches(Regex("""\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?""")) }) {
        "$property must be a comma-separated list of exact $family versions."
    }
    return (defaults.toList() + additionalVersions).distinct()
}

val testSourceSet = sourceSets.test.get()
val mainOutput = sourceSets.main.get().output
val springBootBaseline = libs.versions.commonSpringBootBaseline.get()

val checkDependencyCompatibility =
    tasks.register("checkDependencyCompatibility") {
        description = "Runs the common dependency compatibility matrix."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

tasks.check {
    dependsOn(checkDependencyCompatibility)
}

fun registerCompatibilityTest(
    family: String,
    version: String,
    springBootVersion: String,
    libraryPlatforms: List<String> = emptyList(),
): TaskProvider<Test> {
    val taskName = if (family == "SpringBoot") "testWithSpringBoot$version" else "testWith$family-$version"
    val stackDependencies = configurations.dependencyScope("${taskName}Dependencies")
    val stackRuntimeClasspath =
        configurations.resolvable("${taskName}RuntimeClasspath") {
            extendsFrom(
                stackDependencies.get(),
                testSupport.get(),
                configurations.implementation.get(),
                configurations.runtimeOnly.get(),
            )
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, java.toolchain.languageVersion.map { it.asInt() })
            }
        }

    dependencies {
        val bootPlatform = "org.springframework.boot:spring-boot-dependencies:$springBootVersion"
        if (libraryPlatforms.isEmpty()) {
            add(stackDependencies.name, enforcedPlatform(bootPlatform))
        } else {
            add(stackDependencies.name, platform(bootPlatform))
            libraryPlatforms.forEach { add(stackDependencies.name, enforcedPlatform(it)) }
        }
        add(stackDependencies.name, coordinates(libs.springBootWebflux).map { "$it:$springBootVersion" })
        add(stackDependencies.name, coordinates(libs.micrometerCore))
        add(stackDependencies.name, coordinates(libs.micrometerObservationTest))
        add(stackDependencies.name, coordinates(libs.reactorTest))
    }

    return tasks.register<Test>(taskName) {
        description = "Runs the common tests with $family $version."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = files(testSourceSet.output, mainOutput, stackRuntimeClasspath)
        extensions.configure<JacocoTaskExtension> {
            isEnabled = false
        }
    }
}

fun registerCompatibilityFamily(
    family: String,
    versions: List<String>,
    registerTest: (String) -> TaskProvider<Test>,
) {
    val versionTests = versions.map(registerTest)
    val familyCheck =
        tasks.register("check${family}Compatibility") {
            description = "Runs the common tests with the configured $family versions."
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            dependsOn(versionTests)
        }
    checkDependencyCompatibility.configure {
        dependsOn(familyCheck)
    }
}

val springBootTestVersions =
    compatibilityVersions(
        "SpringBoot",
        springBootBaseline,
        libs.versions.commonSpringBootCompatibility.get(),
        libs.versions.commonSpringBootLatest.get(),
    )
registerCompatibilityFamily("SpringBoot", springBootTestVersions) { version ->
    registerCompatibilityTest("SpringBoot", version, version)
}

data class CompatibilityLibrary(
    val name: String,
    val baseline: String,
    val current: String,
    val platform: String,
)

val compatibilityLibraries =
    listOf(
        CompatibilityLibrary(
            "Micrometer",
            libs.versions.commonMicrometerBaseline.get(),
            libs.versions.commonMicrometerLatest.get(),
            "io.micrometer:micrometer-bom",
        ),
        CompatibilityLibrary(
            "Jackson2",
            libs.versions.commonJackson2Baseline.get(),
            libs.versions.commonJackson2Latest.get(),
            "com.fasterxml.jackson:jackson-bom",
        ),
        CompatibilityLibrary("Jackson3", libs.versions.commonJackson3Baseline.get(), libs.versions.commonJackson3Latest.get(), "tools.jackson:jackson-bom"),
        CompatibilityLibrary("WebFlux", springFrameworkBaseline, libs.versions.commonSpringLatest.get(), springFrameworkBom),
    )

compatibilityLibraries.forEach { library ->
    registerCompatibilityFamily(library.name, compatibilityVersions(library.name, library.current)) { version ->
        val platforms =
            compatibilityLibraries.map { other ->
                "${other.platform}:${if (other == library) version else other.baseline}"
            }
        registerCompatibilityTest(library.name, version, springBootBaseline, platforms)
    }
}

testlogger {
    theme = if (System.getProperty("idea.active") == "true") ThemeType.PLAIN_PARALLEL else ThemeType.MOCHA_PARALLEL
    slowThreshold = 5000

    showPassed = false
    showSkipped = false
    showFailed = true
}

pitest {
    timestampedReports = false
    junit5PluginVersion.set(libs.versions.pitestJunit5Plugin)
    pitestVersion.set(libs.versions.pitest)
    mutators.set(setOf("ALL"))
    outputFormats.set(setOf("XML", "HTML", "CSV"))
}