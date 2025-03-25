package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class JsonRefValidatorTest {
    @TempDir lateinit var tempDir1: Path

    @TempDir lateinit var tempDir2: Path

    @TempDir lateinit var tempDir3: Path

    private lateinit var json: JsonNode
    private lateinit var root1: File
    private lateinit var root2: File
    private lateinit var root3: File
    private lateinit var file1: File
    private lateinit var file2: File
    private lateinit var file3: File

    companion object {
        private const val REF = "\$ref"
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
    }

    @Test
    fun `validate with valid json references - File1`() {
        JsonRefValidator().validate(json, listOf(root1))
    }

    @Test
    fun `validate with valid json references - File2`() {
        JsonRefValidator().validate(json, listOf(root2))
    }

    @Test
    fun `validate with invalid json references`() {
        val exception =
            assertThrows<IllegalStateException> {
                JsonRefValidator().validate(json, listOf(root2, root3))
            }
        Assertions.assertThat(exception).hasMessage("Found 2 errors in 4 JSON references")
    }
}