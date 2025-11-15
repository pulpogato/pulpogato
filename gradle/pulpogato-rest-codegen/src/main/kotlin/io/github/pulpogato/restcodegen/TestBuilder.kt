package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import io.github.pulpogato.restcodegen.ext.pascalCase
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.TreeMap
import javax.xml.bind.DatatypeConverter
import kotlin.collections.get

object TestBuilder {
    private const val MAX_STRING_LENGTH = 60_000

    fun buildTest(
        context: Context,
        key: String,
        example: Any,
        className: TypeName,
        testResourcesDir: File,
    ): MethodSpec? {
        val om = ObjectMapper()

        val formatted: String =
            try {
                val parsed = om.readValue(example.toString(), TreeMap::class.java)
                format(context, om, parsed)
            } catch (_: Exception) {
                try {
                    val parsed = om.readValue(example.toString(), List::class.java)
                    format(context, om, parsed)
                } catch (_: Exception) {
                    example.toString()
                }
            }

        val testUtilsClass = ClassName.get("io.github.pulpogato.test", "TestUtils")

        try {
            // If the string constant would be too large, write it to a JSON file
            val methodSpec =
                if (formatted.length > MAX_STRING_LENGTH) {
                    // Create the test resources directory if it doesn't exist
                    testResourcesDir.mkdirs()

                    // Generate a filename based on the MD5
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.update(formatted.toByteArray(StandardCharsets.UTF_8))
                    val hash = DatatypeConverter.printHexBinary(digest.digest()).take(7).lowercase()

                    val fileName = "example-$hash.json"
                    val jsonFile = File(testResourcesDir, fileName)

                    // Write the formatted JSON to the file
                    jsonFile.writeText(formatted)

                    // Generate test that reads from the JSON file
                    MethodSpec
                        .methodBuilder("test${key.pascalCase()}")
                        .addAnnotation(ClassName.get("org.junit.jupiter.api", "Test"))
                        .addAnnotation(Annotations.generated(1, context))
                        .addException(ClassName.get("com.fasterxml.jackson.core", "JsonProcessingException"))
                        .addException(ClassName.get("java.io", "IOException"))
                        .addStatement(
                            $$"$T input = new $T(getClass().getClassLoader().getResourceAsStream($S).readAllBytes(), $T.UTF_8)",
                            String::class.java,
                            String::class.java,
                            fileName,
                            ClassName.get("java.nio.charset", "StandardCharsets"),
                        ).addStatement($$"var softly = new $T()", ClassName.get("org.assertj.core.api", "SoftAssertions"))
                        .addStatement(
                            $$"var processed = $T.parseAndCompare(new $T<$T>() {}, input, softly)",
                            testUtilsClass,
                            ClassName.get("com.fasterxml.jackson.core.type", "TypeReference"),
                            className.withoutAnnotations(),
                        ).addStatement("softly.assertThat(processed).isNotNull()")
                        .addStatement("softly.assertAll()")
                        .build()
                } else {
                    // Use inline JSON as before
                    MethodSpec
                        .methodBuilder("test${key.pascalCase()}")
                        .addAnnotation(ClassName.get("org.junit.jupiter.api", "Test"))
                        .addAnnotation(Annotations.generated(1, context))
                        .addException(ClassName.get("com.fasterxml.jackson.core", "JsonProcessingException"))
                        .addStatement($$"$T input = /* language=JSON */ $L", String::class.java, formatted.blockQuote())
                        .addStatement($$"var softly = new $T()", ClassName.get("org.assertj.core.api", "SoftAssertions"))
                        .addStatement(
                            $$"var processed = $T.parseAndCompare(new $T<$T>() {}, input, softly)",
                            testUtilsClass,
                            ClassName.get("com.fasterxml.jackson.core.type", "TypeReference"),
                            className.withoutAnnotations(),
                        ).addStatement("softly.assertThat(processed).isNotNull()")
                        .addStatement("softly.assertAll()")
                        .build()
                }
            return methodSpec
        } catch (e: Exception) {
            throw RuntimeException(
                """
                Failed to build test for ${context.getSchemaStackRef()}.
                ValueType: ${example.javaClass}
                Formatted: $formatted
                Value: $example
                """.trimIndent(),
                e,
            )
        }
    }

    private fun format(
        context: Context,
        om: ObjectMapper,
        parsed: Any,
    ): String = om.writerWithDefaultPrettyPrinter().writeValueAsString(normalize(context, parsed))

    private fun normalize(
        context: Context,
        input: Any?,
    ): Any? =
        when (input) {
            is List<*> -> input.map { normalize(context, it) }
            is Map<*, *> -> normalizeMap(context, input)
            else -> input
        }

    private fun normalizeMap(
        context: Context,
        input: Map<*, *>,
    ): Any {
        if (input.size == 1 && input.containsKey($$"$ref")) {
            return normalize(
                context,
                context.openAPI.components.examples[input[$$"$ref"].toString().replace("#/components/examples/", "")]
                    ?.value,
            )!!
        }
        return input.entries.associate { (k, v) -> k to normalize(context, v) }
    }

    private fun String.quote() = "\"$this\""

    private fun String.blockQuote() = "\n${this.replace("\\", "\\\\")}\n".quote().quote().quote()
}