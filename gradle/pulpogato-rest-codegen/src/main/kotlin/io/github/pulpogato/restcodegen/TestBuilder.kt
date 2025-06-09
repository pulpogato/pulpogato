package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import io.github.pulpogato.restcodegen.ext.pascalCase
import java.util.TreeMap
import kotlin.collections.get

object TestBuilder {
    fun buildTest(
        context: Context,
        key: String,
        example: Any,
        className: TypeName,
    ): MethodSpec {
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
            val methodSpec =
                MethodSpec
                    .methodBuilder("test${key.pascalCase()}")
                    .addAnnotation(ClassName.get("org.junit.jupiter.api", "Test"))
                    .addAnnotation(Annotations.generated(1, context))
                    .addException(ClassName.get("com.fasterxml.jackson.core", "JsonProcessingException"))
                    .addStatement("\$T input = /* language=JSON */ \$L", String::class.java, formatted.blockQuote())
                    .addStatement("var softly = new \$T()", ClassName.get("org.assertj.core.api", "SoftAssertions"))
                    .addStatement(
                        "var processed = \$T.parseAndCompare(new \$T<\$T>() {}, input, softly)",
                        testUtilsClass,
                        ClassName.get("com.fasterxml.jackson.core.type", "TypeReference"),
                        className.withoutAnnotations(),
                    ).addStatement("softly.assertThat(processed).isNotNull()")
                    .addStatement("softly.assertAll()")
                    .build()
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
        if (input.size == 1 && input.containsKey("\$ref")) {
            return normalize(
                context,
                context.openAPI.components.examples[input["\$ref"].toString().replace("#/components/examples/", "")]
                    ?.value,
            )!!
        }
        return input.entries.associate { (k, v) -> k to normalize(context, v) }
    }

    private fun String.quote() = "\"$this\""

    private fun String.blockQuote() = "\n${this.replace("\\", "\\\\")}\n".quote().quote().quote()
}