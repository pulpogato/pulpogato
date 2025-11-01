package io.github.pulpogato.restcodegen

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

open class RestCodegenExtension(
    project: Project,
) {
    var schema: RegularFileProperty = project.objects.fileProperty()
    var packageName: Property<String> = project.objects.property(String::class.java)
    var mainDir: RegularFileProperty = project.objects.fileProperty()
    var testDir: RegularFileProperty = project.objects.fileProperty()
    var apiVersion: Property<String> = project.objects.property(String::class.java)
    var projectVariant: Property<String> = project.objects.property(String::class.java)
}