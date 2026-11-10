package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.javapoet.ClassName
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.stream.Stream

class TestBuilderNormalizationTest {
    @TempDir
    lateinit var resources: File

    private val mapper = ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    @ParameterizedTest
    @MethodSource("nodeExamples")
    fun `tree containers preserve reference resolution and root ordering`(
        example: Any,
        expected: String,
    ) {
        val api =
            OpenAPI().components(
                Components()
                    .addExamples("sample", Example().value(mapOf("message" to "resolved")))
                    .addExamples("tree", Example().value(mapper.readTree("""{"nested":{"${'$'}ref":"#/components/examples/sample"}}""")))
                    .addExamples("mixed", Example().value(mapOf("nested" to mapOf("\$ref" to mapper.nodeFactory.textNode("#/components/examples/sample")))))
                    .addExamples("null", Example().value(mapper.readTree("null"))),
            )
        val actual = generatedJson(api, example)
        assertThat(actual).isEqualTo(mapper.readTree(expected))
        val keys = actual.fieldNames().asSequence().toList()
        assertThat(keys).isEqualTo(keys.sorted())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `unresolved references survive absent components or examples`(hasComponents: Boolean) {
        val api = OpenAPI()
        if (hasComponents) api.components(Components())
        val example = mapOf("\$ref" to "#/components/examples/missing")
        assertThat(generatedJson(api, example)).isEqualTo(mapper.valueToTree<JsonNode>(example))
    }

    private fun generatedJson(
        api: OpenAPI,
        example: Any,
    ): JsonNode {
        val code =
            TestBuilder
                .buildTest(
                    Context(api, "fpt", listOf("#", "components", "examples")),
                    "sample",
                    example,
                    ClassName.get("java.lang", "Object"),
                    resources,
                )!!
                .toString()
        val json = code.substringAfter("\"\"\"\n").substringBefore("\"\"\"").replace("\\\\", "\\")
        return mapper.readTree(json)
    }

    companion object {
        @JvmStatic
        fun nodeExamples(): Stream<Arguments> {
            val mapper = ObjectMapper()
            return Stream.of(
                Arguments.of(mapper.readTree("""{"z":1,"a":{"${'$'}ref":"#/components/examples/sample"}}"""), """{"a":{"message":"resolved"},"z":1}"""),
                Arguments.of(mapper.readTree("""[{"${'$'}ref":"#/components/examples/sample"}]"""), """[{"message":"resolved"}]"""),
                Arguments.of(mapOf("nested" to mapper.readTree("""{"${'$'}ref":"#/components/examples/sample"}""")), """{"nested":{"message":"resolved"}}"""),
                Arguments.of(mapper.readTree("null"), "null"),
                Arguments.of(arrayOf(mapOf("\$ref" to "#/components/examples/sample")), """[{"message":"resolved"}]"""),
                Arguments.of(mapOf("nested" to arrayOf(mapOf("\$ref" to "#/components/examples/sample"))), """{"nested":[{"message":"resolved"}]}"""),
                Arguments.of(mapOf("\$ref" to mapper.nodeFactory.textNode("#/components/examples/sample")), """{"message":"resolved"}"""),
                Arguments.of(mapOf("\$ref" to "#/components/examples/null"), "null"),
                Arguments.of(mapOf("\$ref" to "#/components/examples/tree"), """{"nested":{"message":"resolved"}}"""),
                Arguments.of(mapOf("\$ref" to "#/components/examples/mixed"), """{"nested":{"message":"resolved"}}"""),
            )
        }
    }
}