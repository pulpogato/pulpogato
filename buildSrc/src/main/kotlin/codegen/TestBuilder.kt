package codegen

import codegen.ext.pascalCase
import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.TypeName
import java.util.TreeMap

object TestBuilder {
    fun buildTest(
        key: String,
        example: Any,
        className: TypeName,
    ): MethodSpec {
        val om = ObjectMapper()

        val formatted: String =
            try {
                val parsed = om.readValue(example.toString(), TreeMap::class.java)
                format(om, parsed)
            } catch (_: Exception) {
                try {
                    val parsed = om.readValue(example.toString(), List::class.java)
                    format(om, parsed)
                } catch (_: Exception) {
                    example.toString()
                }
            }

        val testUtilsClass = ClassName.get("io.github.pulpogato.test", "TestUtils")

        val ignoredTestsForVersion = IgnoredTests.causes[Context.instance.get().version] ?: mapOf()
        val ignoreCause = ignoredTestsForVersion[Context.getSchemaStackRef()]
        val ignoreAnnotation =
            if (ignoreCause != null) {
                AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Disabled"))
                    .addMember("value", "\$S", ignoreCause)
                    .build()
            } else {
                null
            }

        try {
            val methodSpec =
                MethodSpec.methodBuilder("test${key.pascalCase()}")
                    .addAnnotation(ClassName.get("org.junit.jupiter.api", "Test"))
                    .addAnnotation(Annotations.generated(1))
                    .addAnnotations(listOfNotNull(ignoreAnnotation))
                    .addException(ClassName.get("com.fasterxml.jackson.core", "JsonProcessingException"))
                    .addStatement("\$T input = /* language=JSON */ \$L", String::class.java, formatted.blockQuote())
                    .addStatement("var softly = new \$T()", ClassName.get("org.assertj.core.api", "SoftAssertions"))
                    .addStatement(
                        "var processed = \$T.parseAndCompare(new \$T<\$T>() {}, input, softly)",
                        testUtilsClass,
                        ClassName.get("com.fasterxml.jackson.core.type", "TypeReference"),
                        className.withoutAnnotations(),
                    )
                    .addStatement("softly.assertThat(processed).isNotNull()")
                    .addStatement("softly.assertAll()")
                    .build()
            return methodSpec
        } catch (e: Exception) {
            throw RuntimeException(
                """
                Failed to build test for ${Context.getSchemaStackRef()}.
                ValueType: ${example.javaClass}
                Formatted: ${formatted}
                Value: ${example}                
                """.trimIndent(), e
            )
        }
    }

    private fun format(om: ObjectMapper, parsed: Any): String {

        return om.writerWithDefaultPrettyPrinter().writeValueAsString(normalize(parsed))
    }

    private fun normalize(input: Any?): Any? {
        return when (input) {
            is List<*> -> input.map { it -> normalize(it) }
            is Map<*, *> -> normalizeMap(input)
            else -> input
        }
    }

    private fun normalizeMap(input: Map<*, *>): Any {
        if (input.size == 1 && input.containsKey("\$ref")) {
            return normalize(Context.instance.get().openAPI.components.examples[input["\$ref"].toString().replace("#/components/examples/", "")]?.value)!!
        }
        return input.entries.associate { (k, v) -> k to normalize(v) }
    }

    private fun String.quote() = "\"$this\""

    private fun String.blockQuote() = "\n${this.replace("\\", "\\\\")}\n".quote().quote().quote()
}