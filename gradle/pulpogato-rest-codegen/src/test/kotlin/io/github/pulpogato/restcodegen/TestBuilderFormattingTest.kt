package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.ClassName
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

class TestBuilderFormattingTest {
    @TempDir
    lateinit var resources: File

    private val mapper = ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    @ParameterizedTest
    @MethodSource("examples")
    fun `inline examples preserve JSON values and resolve references`(
        example: Any,
        expected: Any,
    ) {
        val code = generate(example)
        val json = code.substringAfter("\"\"\"\n").substringBefore("\"\"\"").replace("\\\\", "\\")
        assertThat(mapper.readTree(json)).isEqualTo(mapper.valueToTree<JsonNode>(expected))
        assertThat(resources.listFiles()).isEmpty()
    }

    @Test
    fun `large examples are complete JSON resources with the same assertions`() {
        val expected = mapOf("message" to "x".repeat(60_001))
        val code = generate(expected)
        val fixture = resources.listFiles()!!.single()
        assertThat(mapper.readTree(fixture)).isEqualTo(mapper.valueToTree<JsonNode>(expected))
        assertThat(code).contains(fixture.name, "tools.jackson.core.type.TypeReference", "com.fasterxml.jackson.core.type.TypeReference", "softly.assertAll()")
        assertThat(code).doesNotContain("x".repeat(1000))
    }

    private fun generate(example: Any): String {
        val api = OpenAPI().components(Components().addExamples("sample", Example().value(mapOf("message" to "resolved"))))
        return TestBuilder
            .buildTest(
                Context(api, "fpt", listOf("#", "components", "examples")),
                "sample",
                example,
                ClassName.get("java.lang", "Object"),
                resources,
            )!!
            .toString()
    }

    companion object {
        @JvmStatic
        fun examples(): Stream<Arguments> =
            Stream.of(
                Arguments.of(mapOf("z" to 1, "a" to listOf(true, false)), mapOf("z" to 1, "a" to listOf(true, false))),
                Arguments.of(listOf(mapOf("value" to 2)), listOf(mapOf("value" to 2))),
                Arguments.of("text", "text"),
                Arguments.of(42, 42),
                Arguments.of(true, true),
                Arguments.of(mapOf("\$ref" to "#/components/examples/sample"), mapOf("message" to "resolved")),
                Arguments.of(mapOf("nested" to mapOf("\$ref" to "#/components/examples/sample")), mapOf("nested" to mapOf("message" to "resolved"))),
            )
    }
}