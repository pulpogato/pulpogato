package io.github.pulpogato.restcodegen.ext

import io.swagger.v3.oas.models.media.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleOrArrayCases")
    fun `isSingleOrArray detects a string-or-array-of-string oneOf`(
        @Suppress("UNUSED_PARAMETER") description: String,
        oneOf: List<Schema<Any>>,
        expected: Boolean,
    ) {
        assertThat(isSingleOrArray(oneOf, "string")).isEqualTo(expected)
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
    fun `isOnlyForValidation returns true for oneOf with only required constraints`() {
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

        assertThat(isOnlyForValidation(listOf(oneOfSchema1, oneOfSchema2), parentSchema)).isTrue()
    }

    @Test
    fun `isOnlyForValidation returns false for oneOf with type definitions`() {
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

        assertThat(isOnlyForValidation(listOf(oneOfSchema1, oneOfSchema2), parentSchema)).isFalse()
    }

    @Test
    fun `isOnlyForValidation returns false when parent has no properties`() {
        val oneOfSchema =
            Schema<Any>().apply {
                required = listOf("code_scanning_alerts")
            }

        val parentSchema = Schema<Any>()

        assertThat(isOnlyForValidation(listOf(oneOfSchema), parentSchema)).isFalse()
    }

    companion object {
        private fun schema(vararg types: String) = Schema<Any>().apply { this.types = types.toSet() }

        private fun arrayOfItems(items: Schema<Any>?) =
            Schema<Any>().apply {
                types = setOf("array")
                this.items = items
            }

        @JvmStatic
        fun singleOrArrayCases(): List<Arguments> =
            listOf(
                Arguments.of("plain string branch", listOf(schema("string"), arrayOfItems(schema("string"))), true),
                // https://github.com/github/rest-api-description made custom-property-value.value's first
                // branch `["string", "null"]`, which must still map to SingularOrPlural<String>.
                Arguments.of("nullable string branch", listOf(schema("string", "null"), arrayOfItems(schema("string"))), true),
                Arguments.of("nullable array items", listOf(schema("string"), arrayOfItems(schema("string", "null"))), true),
                Arguments.of("second branch is not an array", listOf(schema("integer"), schema("string")), false),
                Arguments.of("branch type is not string", listOf(schema("integer"), arrayOfItems(schema("integer"))), false),
                Arguments.of("mismatched item type", listOf(schema("string"), arrayOfItems(schema("integer"))), false),
                Arguments.of("array branch without items", listOf(schema("string"), arrayOfItems(null)), false),
                Arguments.of("untyped branches", listOf(Schema<Any>(), arrayOfItems(Schema<Any>())), false),
            )
    }
}