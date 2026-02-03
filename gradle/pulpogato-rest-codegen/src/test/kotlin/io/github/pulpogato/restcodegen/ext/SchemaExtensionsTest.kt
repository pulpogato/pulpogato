package io.github.pulpogato.restcodegen.ext

import io.swagger.v3.oas.models.media.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaExtensionsTest {
    @Test
    fun `className converts key to PascalCase`() {
        val entry = mapOf("test-key" to Schema<Any>()).entries.first()

        assertThat(entry.className()).isEqualTo("TestKey")
    }

    @Test
    fun `className converts multiple words to PascalCase`() {
        val entry = mapOf("test-multiple-words" to Schema<Any>()).entries.first()

        assertThat(entry.className()).isEqualTo("TestMultipleWords")
    }

    @Test
    fun `className handles underscores`() {
        val entry = mapOf("test_key" to Schema<Any>()).entries.first()

        assertThat(entry.className()).isEqualTo("TestKey")
    }

    @Test
    fun `isSingleOrArray returns true for valid single or array schema`() {
        val singleSchema =
            Schema<Any>().apply {
                type = "string"
            }
        val arraySchema =
            Schema<Any>().apply {
                type = "array"
                items = Schema<Any>().apply { type = "string" }
            }

        val oneOf = listOf(singleSchema, arraySchema)

        // Set the types property for each schema
        singleSchema.types = setOf("string")
        arraySchema.types = setOf("array")
        arraySchema.items.types = setOf("string")

        assertThat(isSingleOrArray(oneOf, "string")).isTrue()
    }

    @Test
    fun `isSingleOrArray returns false for invalid schemas`() {
        val schema1 =
            Schema<Any>().apply {
                type = "integer"
                types = setOf("integer")
            }
        val schema2 =
            Schema<Any>().apply {
                type = "string"
                types = setOf("string")
            }

        val oneOf = listOf(schema1, schema2)

        assertThat(isSingleOrArray(oneOf, "string")).isFalse()
    }

    @Test
    fun `typesAre returns true when types match`() {
        val schema1 =
            Schema<Any>().apply {
                type = "string"
                types = setOf("string")
            }
        val schema2 =
            Schema<Any>().apply {
                type = "integer"
                types = setOf("integer")
            }

        val oneOf = listOf(schema1, schema2)

        assertThat(typesAre(oneOf, "string", "integer")).isTrue()
    }

    @Test
    fun `typesAre returns false when types don't match`() {
        val schema1 =
            Schema<Any>().apply {
                type = "string"
                types = setOf("string")
            }
        val schema2 =
            Schema<Any>().apply {
                type = "integer"
                types = setOf("integer")
            }

        val oneOf = listOf(schema1, schema2)

        assertThat(typesAre(oneOf, "string", "boolean")).isFalse()
    }

    @Test
    fun `isOneOfOnlyForValidation returns true for oneOf with only required constraints`() {
        val oneOfSchema1 =
            Schema<Any>().apply {
                required = listOf("code_scanning_alerts")
            }
        val oneOfSchema2 =
            Schema<Any>().apply {
                required = listOf("secret_scanning_alerts")
            }

        val parentSchema =
            Schema<Any>().apply {
                type = "object"
                properties =
                    mapOf(
                        "name" to Schema<Any>().apply { type = "string" },
                        "code_scanning_alerts" to Schema<Any>().apply { type = "array" },
                        "secret_scanning_alerts" to Schema<Any>().apply { type = "array" },
                    )
            }

        assertThat(isOneOfOnlyForValidation(listOf(oneOfSchema1, oneOfSchema2), parentSchema)).isTrue()
    }

    @Test
    fun `isOneOfOnlyForValidation returns false for oneOf with type definitions`() {
        val oneOfSchema1 =
            Schema<Any>().apply {
                type = "object"
                types = setOf("object")
                properties = mapOf("field1" to Schema<Any>().apply { type = "string" })
            }
        val oneOfSchema2 =
            Schema<Any>().apply {
                type = "object"
                types = setOf("object")
                properties = mapOf("field2" to Schema<Any>().apply { type = "integer" })
            }

        val parentSchema =
            Schema<Any>().apply {
                type = "object"
                properties = mapOf("name" to Schema<Any>().apply { type = "string" })
            }

        assertThat(isOneOfOnlyForValidation(listOf(oneOfSchema1, oneOfSchema2), parentSchema)).isFalse()
    }

    @Test
    fun `isOneOfOnlyForValidation returns false when parent has no properties`() {
        val oneOfSchema =
            Schema<Any>().apply {
                required = listOf("code_scanning_alerts")
            }

        val parentSchema = Schema<Any>()

        assertThat(isOneOfOnlyForValidation(listOf(oneOfSchema), parentSchema)).isFalse()
    }
}