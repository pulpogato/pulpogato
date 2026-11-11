package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import io.github.pulpogato.restcodegen.ext.pascalCase
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter
import kotlin.collections.get

object TestBuilder {
    private const val MAX_STRING_LENGTH = 60_000
    private const val PACKAGE_ASSERTJ = "org.assertj.core.api"
    private val mapper = ObjectMapper()

    fun buildTest(
        context: Context,
        key: String,
        example: Any,
        className: TypeName,
        testResourcesDir: File,
    ): MethodSpec? {
        val orderedExample =
            when (val value = mapper.convertValue(example, Any::class.java)) {
                is Map<*, *> -> value.entries.associate { it.key.toString() to it.value }.toSortedMap()
                else -> value
            }
        val formatted = format(context, mapper, orderedExample)

        val testUtilsClass = ClassName.get("io.github.pulpogato.test", "TestUtils")

        try {
            // If the string constant would be too large, write it to a JSON file
            val typeReferenceJackson3 = ClassName.get("tools.jackson.core.type", "TypeReference")
            val typeReferenceJackson2 = ClassName.get("com.fasterxml.jackson.core.type", "TypeReference")
            val method =
                MethodSpec
                    .methodBuilder("test${key.pascalCase()}")
                    .addAnnotation(ClassName.get("org.junit.jupiter.api", "Test"))
                    .addAnnotation(Annotations.generated(0, context))
            addFixtureInput(method, formatted, testResourcesDir)
            return method
                .addStatement($$"var softly = new $T()", ClassName.get(PACKAGE_ASSERTJ, "SoftAssertions"))
                .addStatement(
                    $$"var processed = $T.parseAndCompare(new $T<$T>() {}, input, softly)",
                    testUtilsClass,
                    typeReferenceJackson3,
                    className.withoutAnnotations(),
                ).addStatement(
                    $$"$T.parseAndCompare(new $T<$T>() {}, input, softly)",
                    testUtilsClass,
                    typeReferenceJackson2,
                    className.withoutAnnotations(),
                ).addStatement("softly.assertThat(processed).isNotNull()")
                .addStatement("softly.assertAll()")
                .build()
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

    private fun addFixtureInput(
        method: MethodSpec.Builder,
        formatted: String,
        testResourcesDir: File,
    ) {
        if (formatted.length <= MAX_STRING_LENGTH) {
            method.addStatement($$"@$T(\"json\") $T input = $L", Language::class.java, String::class.java, formatted.blockQuote())
            return
        }

        testResourcesDir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash =
            DatatypeConverter
                .printHexBinary(digest.digest(formatted.toByteArray(StandardCharsets.UTF_8)))
                .take(7)
                .lowercase()
        val fileName = "example-$hash.json"
        FileWriter(File(testResourcesDir, fileName)).use { it.write(formatted) }
        method
            .addException(ClassName.get("java.io", "IOException"))
            .addStatement($$"var stream = getClass().getClassLoader().getResourceAsStream($S)", fileName)
            .addStatement(
                $$"$T.assertThat(stream).as(\"Could not read stream for file $L\").isNotNull()",
                ClassName.get(PACKAGE_ASSERTJ, "Assertions"),
                fileName,
            ).addStatement(
                $$"@$T(\"json\") var input = new $T(stream.readAllBytes(), $T.UTF_8)",
                Language::class.java,
                String::class.java,
                ClassName.get("java.nio.charset", "StandardCharsets"),
            )
    }

    private fun format(
        context: Context,
        om: ObjectMapper,
        parsed: Any?,
    ): String = om.writerWithDefaultPrettyPrinter().writeValueAsString(normalize(context, parsed))

    private fun normalize(
        context: Context,
        input: Any?,
    ): Any? =
        when (input) {
            is JsonNode -> normalize(context, unwrapTreeValue(input))
            is List<*> -> input.map { normalize(context, it) }
            is Map<*, *> -> normalizeMap(context, input)
            else -> input
        }

    private fun normalizeMap(
        context: Context,
        input: Map<*, *>,
    ): Any? {
        if (input.size == 1 && input.containsKey($$"$ref")) {
            val ref = unwrapTreeValue(input[$$"$ref"]).toString()
            if (ref.startsWith("#/components/examples/")) {
                val exampleName = ref.removePrefix("#/components/examples/")
                val resolved =
                    context.openAPI.components
                        ?.examples
                        ?.get(exampleName)
                        ?.value
                if (resolved != null) {
                    return normalize(context, resolved)
                }
            }
            // If we can't resolve the ref, fall through to normal map handling
        }
        return input.entries.associate { (k, v) -> k to normalize(context, v) }
    }

    private fun unwrapTreeValue(input: Any?): Any? = if (input is JsonNode) mapper.convertValue(input, Any::class.java) else input

    private fun String.quote() = "\"$this\""

    private fun String.blockQuote() = "\n${this.replace("\\", "\\\\")}\n".quote().quote().quote()
}