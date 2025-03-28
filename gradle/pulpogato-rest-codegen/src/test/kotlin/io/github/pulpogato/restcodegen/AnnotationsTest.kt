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
    fun `superBuilder creates annotation with toBuilder set to true`() {
        val result = Annotations.superBuilder()

        assertThat(result.type().toString()).isEqualTo("lombok.experimental.SuperBuilder")
        assertThat(result.members().get("toBuilder").toString()).contains("true")
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
        assertThat(result.members().get("value").toString()).contains("\"testProperty\"")
    }

    @Test
    fun `serializerAnnotation creates annotation with correct using member`() {
        val result = Annotations.serializerAnnotation("TestClass", mockTypeSpec)

        assertThat(result.type().toString()).contains("JsonSerialize")
        assertThat(result.members().get("using").toString()).contains("TestClass.MockType.class")
    }

    @Test
    fun `deserializerAnnotation creates annotation with correct using member`() {
        val result = Annotations.deserializerAnnotation("TestClass", mockTypeSpec)

        assertThat(result.type().toString()).contains("JsonDeserialize")
        assertThat(result.members().get("using").toString()).contains("TestClass.MockType.class")
    }

    @Test
    fun `jsonFormat creates annotation with correct shape`() {
        val result = Annotations.jsonFormat(JsonFormat.Shape.STRING)

        assertThat(result.type().toString()).contains("JsonFormat")
        assertThat(result.members().get("shape").toString()).contains("JsonFormat.Shape.STRING")
    }

    @Test
    fun `jsonFormat with pattern creates annotation with correct shape and pattern`() {
        val result = Annotations.jsonFormat(JsonFormat.Shape.STRING, "yyyy-MM-dd")

        assertThat(result.type().toString()).contains("JsonFormat")
        assertThat(result.members().get("shape").toString()).contains("JsonFormat.Shape.STRING")
        assertThat(result.members().get("pattern").toString()).contains("\"yyyy-MM-dd\"")
    }

    @Test
    fun `singleValueAsArray creates annotation with correct feature`() {
        val result = Annotations.singleValueAsArray()

        assertThat(result.type().toString()).contains("JsonFormat")
        assertThat(result.members().get("with").toString()).contains("ACCEPT_SINGLE_VALUE_AS_ARRAY")
    }

    @Test
    fun `jsonIncludeNonNull creates annotation with correct include value`() {
        val result = Annotations.jsonIncludeNonNull()

        assertThat(result.type().toString()).contains("JsonInclude")
        assertThat(result.members().get("value").toString()).contains("JsonInclude.Include.NON_NULL")
    }

    @Test
    fun `typeGenerated creates annotation with correct codeRef`() {
        val result = Annotations.typeGenerated()

        assertThat(result.type().toString()).contains("TypeGenerated")
        assertThat(result.members().get("codeRef").toString()).contains("AnnotationsTest.kt:")
    }

    @Test
    fun `testExtension creates annotation with correct value`() {
        val result = Annotations.testExtension()

        assertThat(result.type().toString()).contains("ExtendWith")
        assertThat(result.members().get("value").toString()).contains("IgnoredTestContext.class")
    }
}