package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonFormat
import com.palantir.javapoet.TypeSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AnnotationsTest {
    private lateinit var mockTypeSpec: TypeSpec

    @BeforeEach
    fun setUp() {
        mockTypeSpec = Mockito.mock(TypeSpec::class.java)
        Mockito.`when`(mockTypeSpec.name()).thenReturn("MockType")
    }

    @Test
    fun `jsonValue creates annotation with correct type`() {
        val result = Annotations.jsonValue()

        assertThat(result.type().toString()).contains("JsonValue")
    }

    @Test
    fun `jsonProperty creates annotation with correct value`() {
        val result = Annotations.jsonProperty("testProperty")

        assertThat(result.type().toString()).contains("JsonProperty")
        assertThat(result.members()["value"].toString()).contains("\"testProperty\"")
    }

    @Test
    fun `serializerAnnotation creates annotation with correct using member`() {
        val result = Annotations.serializerAnnotationForJackson3("TestClass", mockTypeSpec)

        assertThat(result.type().toString()).contains("JsonSerialize")
        assertThat(result.members()["using"].toString()).contains("TestClass.MockType.class")
    }

    @Test
    fun `deserializerAnnotation creates annotation with correct using member`() {
        val result = Annotations.deserializerAnnotationForJackson3("TestClass", mockTypeSpec)

        assertThat(result.type().toString()).contains("JsonDeserialize")
        assertThat(result.members()["using"].toString()).contains("TestClass.MockType.class")
    }

    @Test
    fun `jsonFormat creates annotation with correct shape`() {
        val result = Annotations.jsonFormat(JsonFormat.Shape.STRING)

        assertThat(result.type().toString()).contains("JsonFormat")
        assertThat(result.members()["shape"].toString()).contains("JsonFormat.Shape.STRING")
    }

    @Test
    fun `jsonFormat with pattern creates annotation with correct shape and pattern`() {
        val result = Annotations.jsonFormat(JsonFormat.Shape.STRING, "yyyy-MM-dd")

        assertThat(result.type().toString()).contains("JsonFormat")
        assertThat(result.members()["shape"].toString()).contains("JsonFormat.Shape.STRING")
        assertThat(result.members()["pattern"].toString()).contains("\"yyyy-MM-dd\"")
    }

    @Test
    fun `singleValueAsArray creates annotation with correct feature`() {
        val result = Annotations.singleValueAsArray()

        assertThat(result.type().toString()).contains("JsonFormat")
        assertThat(result.members()["with"].toString()).contains("ACCEPT_SINGLE_VALUE_AS_ARRAY")
    }

    @Test
    fun `jsonIncludeNonNull creates annotation with correct include value`() {
        val result = Annotations.jsonIncludeNonNull()

        assertThat(result.type().toString()).contains("JsonInclude")
        assertThat(result.members()["value"].toString()).contains("JsonInclude.Include.NON_NULL")
    }

    @Test
    fun `typeGenerated creates annotation with correct codeRef`() {
        val result = Annotations.typeGenerated()

        assertThat(result.type().toString()).contains("TypeGenerated")
        assertThat(result.members()["codeRef"].toString()).contains("AnnotationsTest.kt:")
    }

    @Test
    fun `generatedForGithubFiles creates annotation with expected members`() {
        val result = Annotations.generatedForGithubFiles("#/definitions/step", "github-workflow.json")

        assertThat(result.type().toString()).contains("Generated")
        assertThat(result.members()).doesNotContainKey("ghVersion")
        assertThat(result.members()["schemaRef"].toString()).contains("\"#/definitions/step\"")
        assertThat(result.members()["sourceFile"].toString()).contains("\"github-workflow.json\"")
        assertThat(result.members()["codeRef"].toString()).contains("AnnotationsTest.kt:")
    }

    @Test
    fun `testExtension creates annotation with correct value`() {
        val result = Annotations.testExtension()

        assertThat(result.type().toString()).contains("CompositeTestExtension")
    }
}