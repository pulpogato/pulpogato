package io.github.pulpogato.buildsupport

import groovy.lang.Closure
import java.io.File
import java.io.Serializable
import java.util.Properties

class PropertiesFileValueClosure(
    private val propertiesFile: File,
    private val propertyName: String,
) : Closure<String>(null),
    Serializable {
    @Suppress("unused")
    fun doCall(): String {
        val properties = Properties()
        propertiesFile.inputStream().use(properties::load)
        return properties.getProperty(propertyName)
            ?: error("Missing property '$propertyName' in ${propertiesFile.absolutePath}")
    }
}