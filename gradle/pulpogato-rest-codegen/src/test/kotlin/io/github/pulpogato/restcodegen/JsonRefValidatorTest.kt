package io.github.pulpogato.restcodegen

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class JsonRefValidatorTest {
    @TempDir lateinit var tempDir1: Path

    @TempDir lateinit var tempDir2: Path

    @TempDir lateinit var tempDir3: Path

    @TempDir lateinit var tempDir4: Path

    private lateinit var json: JsonNode
    private lateinit var root1: File
    private lateinit var root2: File
    private lateinit var root3: File
    private lateinit var root4: File
    private lateinit var file1: File
    private lateinit var file2: File
    private lateinit var file3: File
    private lateinit var file4: File

    companion object {
        private const val REF = $$"$ref"

        private fun getJavaFiles(vararg dirs: File): List<File> =
            dirs.flatMap { dir ->
                dir
                    .walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".java") }
                    .toList()
            }
    }

    @BeforeEach
    fun setUp() {
        json =
            ObjectMapper().readTree(
                // language=json
                """
                {
                  "paths": {
                    "/pets": {
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "array",
                                  "items": {
                                    "$REF": "#/components/schemas/Pet"
                                  }
                                }
                              }
                            }
                          },
                          "default": {
                            "description": "unexpected error",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "$REF": "#/components/schemas/ErrorModel"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Pet": {
                        "type": "object",
                        "required": [
                          "id",
                          "name"
                        ],
                        "properties": {
                          "id": {
                            "type": "integer",
                            "format": "int64"
                          },
                          "name": {
                            "type": "string"
                          },
                          "tag": {
                            "type": "string"
                          }
                        }
                      },
                      "ErrorModel": {
                        "type": "object",
                        "required": [
                          "code",
                          "message"
                        ],
                        "properties": {
                          "code": {
                            "type": "integer",
                            "format": "int32"
                          },
                          "message": {
                            "type": "string"
                          }
                        }
                      },
                      "ArrayWithIndices": {
                        "type": "array",
                        "items": [
                          {
                            "type": "string"
                          },
                          {
                            "type": "integer"
                          }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
        root1 = tempDir1.toFile()
        root1.mkdirs()
        file1 =
            File(root1, "File1.java").apply {
                createNewFile()
                writeText(
                    // language=java
                    """
                    package com.example;
                    class Test {
                        String a = "something"; // schemaRef = "/paths/~1pets/get/responses/200/content/application~1json/schema/items",
                        String b = "something"; // schemaRef = "/components/schemas/Pet/properties/id",
                        String c = "something"; // schemaRef = "/components/schemas/ErrorModel",
                        String d = "something"; // schemaRef = "/components/schemas/ErrorModel/properties/message",
                    }
                    """.trimIndent(),
                )
            }
        root2 = tempDir2.toFile()
        root2.mkdirs()
        file2 =
            File(root2, "File2.java").apply {
                createNewFile()
                writeText(
                    // language=java
                    """
                    package com.example;
                    class Test2 {
                        String a = "something"; // schemaRef = "/paths/~1pets/get/responses/default/content/application~1json/schema",
                        String b = "something"; // schemaRef = "/components/schemas/Pet",
                    }
                    """.trimIndent(),
                )
            }
        root3 = tempDir3.toFile()
        root3.mkdirs()
        file3 =
            File(root3, "File3.java").apply {
                createNewFile()
                writeText(
                    // language=java
                    """
                    package com.example;
                    class Test3 {
                        String a = "something"; // schemaRef = "/components/schemas/Pet/properties/bad",
                        String b = "something"; // schemaRef = "/components/schemas/Bad",
                    }
                    """.trimIndent(),
                )
            }
        root4 = tempDir4.toFile()
        root4.mkdirs()
        file4 =
            File(root4, "File4.java").apply {
                createNewFile()
                writeText(
                    // language=java
                    """
                    package com.example;
                    class Test4 {
                        String a = "something"; // schemaRef = "/components/schemas/ArrayWithIndices/items/0",
                        String b = "something"; // schemaRef = "/components/schemas/ArrayWithIndices/items/1",
                        String c = "something"; // schemaRef = "/components/schemas/ArrayWithIndices/items/999", // This should fail
                    }
                    """.trimIndent(),
                )
            }
    }

    @Test
    fun `validate with valid json references - File1`() {
        JsonRefValidator().validate(mapOf("schema.json" to json), getJavaFiles(root1))
    }

    @Test
    fun `validate with valid json references - File2`() {
        JsonRefValidator().validate(mapOf("schema.json" to json), getJavaFiles(root2))
    }

    @Test
    fun `validate with invalid json references`() {
        val exception =
            assertThrows<IllegalStateException> {
                JsonRefValidator().validate(mapOf("schema.json" to json), getJavaFiles(root2, root3))
            }
        Assertions.assertThat(exception).hasMessage("Found 2 errors in 4 JSON references")
    }

    @Test
    fun `validate with threshold allows some errors`() {
        // Should not throw with threshold of 2
        JsonRefValidator(2).validate(mapOf("schema.json" to json), getJavaFiles(root2, root3))

        // Should throw with threshold of 1
        val exception =
            assertThrows<IllegalStateException> {
                JsonRefValidator(1).validate(mapOf("schema.json" to json), getJavaFiles(root2, root3))
            }
        Assertions.assertThat(exception).hasMessage("Found 2 errors in 4 JSON references")
    }

    @Test
    fun `validate with valid array indices`() {
        // Create a temporary file with only valid array indices
        val tempDir = createTempDirectory().toFile()
        File(tempDir, "ValidFile.java").apply {
            createNewFile()
            writeText(
                // language=java
                """
                package com.example;
                class ValidIndices {
                    String a = "something"; // schemaRef = "/components/schemas/ArrayWithIndices/items/0",
                    String b = "something"; // schemaRef = "/components/schemas/ArrayWithIndices/items/1",
                }
                """.trimIndent(),
            )
        }

        try {
            JsonRefValidator().validate(mapOf("schema.json" to json), getJavaFiles(tempDir))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `validate with invalid array indices`() {
        // Create a temporary file with an invalid array index
        val tempDir = createTempDirectory().toFile()
        File(tempDir, "InvalidFile.java").apply {
            createNewFile()
            writeText(
                // language=java
                """
                package com.example;
                class InvalidIndices {
                    String c = "something"; // schemaRef = "/components/schemas/ArrayWithIndices/items/999",
                }
                """.trimIndent(),
            )
        }

        try {
            val exception =
                assertThrows<IllegalStateException> {
                    JsonRefValidator(0).validate(mapOf("schema.json" to json), getJavaFiles(tempDir))
                }
            Assertions.assertThat(exception).hasMessage("Found 1 errors in 1 JSON references")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `validate with multiple roots`() {
        // Should validate all valid references across multiple roots
        JsonRefValidator().validate(mapOf("schema.json" to json), getJavaFiles(root1, root2))

        // Should count errors across all roots
        val exception =
            assertThrows<IllegalStateException> {
                JsonRefValidator(0).validate(mapOf("schema.json" to json), getJavaFiles(root1, root3, root4))
            }
        Assertions.assertThat(exception).hasMessage("Found 3 errors in 9 JSON references")
    }
}