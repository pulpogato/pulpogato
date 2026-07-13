package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SchemasBuilderTest {
    @TempDir
    lateinit var tempDir: Path

    private val packageName = "com.example.schemas"

    @Test
    fun `multi-event webhook group gets a sealed supertype with only the common getters`() {
        val openAPI = sampleOpenAPI()

        generate(openAPI)

        val supertype = readGenerated("WebhookThing")
        assertThat(supertype)
            .contains("public sealed interface WebhookThing extends WebhookEvent")
            .contains("permits")
            .contains("WebhookThingCreated")
            .contains("WebhookThingDeleted")
            // `sender` is a $ref shared by both members, so it is common.
            .contains("SimpleUser getSender();")
            // `value` differs in type, `extra` only exists on one, `action` is a per-member enum — none common.
            .doesNotContain("getValue(")
            .doesNotContain("getExtra(")
            .doesNotContain("getAction(")
    }

    @Test
    fun `a discriminable group gets JsonSubTypes mapping each action to its member`() {
        generate(sampleOpenAPI())

        assertThat(readGenerated("WebhookThing"))
            .contains("@JsonTypeInfo(")
            .contains("include = JsonTypeInfo.As.EXISTING_PROPERTY")
            .contains("property = \"action\"")
            .contains("@JsonSubTypes.Type(value = WebhookThingCreated.class, name = \"created\")")
            .contains("@JsonSubTypes.Type(value = WebhookThingDeleted.class, name = \"deleted\")")
    }

    @Test
    fun `members of a group are marked non-sealed and implement the supertype`() {
        generate(sampleOpenAPI())

        assertThat(readGenerated("WebhookThingCreated"))
            .contains("public non-sealed class WebhookThingCreated")
            .contains("WebhookThing")
        assertThat(readGenerated("WebhookThingDeleted"))
            .contains("public non-sealed class WebhookThingDeleted")
            .contains("WebhookThing")
    }

    @Test
    fun `single-event subcategory does not get a supertype`() {
        generate(sampleOpenAPI())

        // The lone event's body is generated as an ordinary class...
        assertThat(generatedFile("WebhookSoloEvent")).exists()
        assertThat(readGenerated("WebhookSoloEvent")).contains("public class WebhookSoloEvent")
        // ...and no `Webhook<subcategory>` interface is synthesized for it.
        assertThat(generatedFile("WebhookSolo")).doesNotExist()
    }

    @Test
    fun `composite oneOf members expose fields common to all branches via delegating getters`() {
        generate(compositeSampleOpenAPI())

        // `sender` lives inside both branches of the composite member, so it surfaces on the supertype.
        assertThat(readGenerated("WebhookCombo"))
            .contains("public sealed interface WebhookCombo")
            .contains("SimpleUser getSender();")

        // The composite member gains a getter that reads from whichever branch is populated.
        assertThat(readGenerated("WebhookComboComposite"))
            .contains("public SimpleUser getSender()")
            .contains("getWebhookComboComposite0()")
            .contains("getWebhookComboComposite1()")
    }

    @Test
    fun `allOf of a single ref schema collapses to the referenced type`() {
        generate(allOfSampleOpenAPI())

        // `creator` is `allOf: [{$ref: simple-user}]`, which adds nothing, so the field is typed
        // directly as the referenced type with no synthetic wrapper class.
        assertThat(readGenerated("CreatorWrapper"))
            .contains("private SimpleUser creator;")
            .doesNotContain("class Creator implements")
    }

    @Test
    fun `allOf of a ref plus inline object extends the referenced type`() {
        generate(allOfSampleOpenAPI())

        val releaseEvent = readGenerated("ReleaseEvent")
        assertThat(releaseEvent)
            // The inline extension becomes a subclass of the referenced base type...
            .contains("class Release extends io.github.pulpogato.rest.schemas.Release")
            .contains("extends io.github.pulpogato.rest.schemas.Release.ReleaseBuilder<C, B>")
            .contains("super(b)")
            // ...exposing only the inline properties as its own fields.
            .contains("getShortDescriptionHtml()")
            .contains("getIsShortDescriptionHtmlTruncated()")
            // No runtime merge machinery and no meaningless indexed field.
            .doesNotContain("FancySerializer")
            .doesNotContain("release1")
    }

    @Test
    fun `allOf of only inline objects flattens their properties into one object`() {
        generate(allOfSampleOpenAPI())

        val forkEvent = readGenerated("ForkEvent")
        assertThat(forkEvent)
            .contains("public static class Forkee implements PulpogatoType")
            // Properties from both inline members live directly on the single flattened class.
            .contains("getNodeId()")
            .contains("getFullName()")
            .doesNotContain("FancySerializer")
            .doesNotContain("forkee1")
    }

    @Test
    fun `allOf of multiple refs keeps the runtime merge serializer`() {
        generate(allOfSampleOpenAPI())

        // Two $refs cannot be expressed via single inheritance, so the merge serializer is retained.
        assertThat(readGenerated("Combo"))
            .contains("FancySerializer")
    }

    /**
     * A spec exercising each allOf shape as a property: a single-ref alias, a ref + inline extension,
     * a flatten of inline-only members, and a multi-ref merge.
     */
    private fun allOfSampleOpenAPI(): OpenAPI {
        val stringSchema = { Schema<Any>().apply { types = mutableSetOf("string") } }
        val booleanSchema = { Schema<Any>().apply { types = mutableSetOf("boolean") } }
        val refSchema = { ref: String -> Schema<Any>().apply { `$ref` = ref } }
        val objectSchema = { props: Map<String, Schema<Any>> ->
            Schema<Any>().apply {
                types = mutableSetOf("object")
                properties = LinkedHashMap(props)
            }
        }
        val allOfSchema = { members: List<Schema<Any>> ->
            Schema<Any>().apply { allOf = members }
        }

        val openAPI = OpenAPI()
        openAPI.schema("simple-user", objectSchema(mapOf("login" to stringSchema())))
        openAPI.schema("release", objectSchema(mapOf("id" to stringSchema(), "name" to stringSchema())))

        openAPI.schema(
            "creator-wrapper",
            objectSchema(mapOf("creator" to allOfSchema(listOf(refSchema("#/components/schemas/simple-user"))))),
        )
        openAPI.schema(
            "release-event",
            objectSchema(
                mapOf(
                    "release" to
                        allOfSchema(
                            listOf(
                                refSchema("#/components/schemas/release"),
                                objectSchema(
                                    mapOf(
                                        "short_description_html" to stringSchema(),
                                        "is_short_description_html_truncated" to booleanSchema(),
                                    ),
                                ),
                            ),
                        ),
                ),
            ),
        )
        openAPI.schema(
            "fork-event",
            objectSchema(
                mapOf(
                    "forkee" to
                        allOfSchema(
                            listOf(
                                objectSchema(mapOf("node_id" to stringSchema())),
                                objectSchema(mapOf("full_name" to stringSchema())),
                            ),
                        ),
                ),
            ),
        )
        openAPI.schema(
            "combo",
            objectSchema(
                mapOf(
                    "both" to
                        allOfSchema(
                            listOf(
                                refSchema("#/components/schemas/simple-user"),
                                refSchema("#/components/schemas/release"),
                            ),
                        ),
                ),
            ),
        )
        return openAPI
    }

    private fun generate(openAPI: OpenAPI) {
        val context = Context(openAPI, "test", emptyList(), emptyMap())
        SchemasBuilder().buildSchemas(context, tempDir.toFile(), packageName, mutableSetOf<ClassName>())
    }

    private fun generatedFile(simpleName: String): File = File(tempDir.toFile(), "com/example/schemas/$simpleName.java")

    private fun readGenerated(simpleName: String): String = generatedFile(simpleName).readText()

    /**
     * A minimal spec with one multi-event subcategory (`thing` -> created + deleted) and one
     * single-event subcategory (`solo`).
     */
    private fun sampleOpenAPI(): OpenAPI {
        val stringSchema = { Schema<Any>().apply { types = mutableSetOf("string") } }
        val integerSchema = { Schema<Any>().apply { types = mutableSetOf("integer") } }
        val refSchema = { ref: String -> Schema<Any>().apply { `$ref` = ref } }
        val actionSchema = { value: String ->
            Schema<Any>().apply {
                types = mutableSetOf("string")
                enum = listOf(value)
            }
        }

        val objectSchema = { props: Map<String, Schema<Any>> ->
            Schema<Any>().apply {
                types = mutableSetOf("object")
                properties = LinkedHashMap(props)
            }
        }

        val openAPI = OpenAPI()
        openAPI.schema("simple-user", objectSchema(mapOf("login" to stringSchema())))
        openAPI.schema(
            "webhook-thing-created",
            objectSchema(
                mapOf(
                    "action" to actionSchema("created"),
                    "sender" to refSchema("#/components/schemas/simple-user"),
                    "value" to stringSchema(),
                    "extra" to stringSchema(),
                ),
            ),
        )
        openAPI.schema(
            "webhook-thing-deleted",
            objectSchema(
                mapOf(
                    "action" to actionSchema("deleted"),
                    "sender" to refSchema("#/components/schemas/simple-user"),
                    "value" to integerSchema(),
                ),
            ),
        )
        openAPI.schema("webhook-solo-event", objectSchema(mapOf("sender" to refSchema("#/components/schemas/simple-user"))))

        openAPI.webhooks =
            linkedMapOf(
                "thing-created" to webhook("thing", "webhook-thing-created"),
                "thing-deleted" to webhook("thing", "webhook-thing-deleted"),
                "solo" to webhook("solo", "webhook-solo-event"),
            )
        return openAPI
    }

    /**
     * A `combo` subcategory with a plain member and a composite (`oneOf`) member whose branches both
     * carry `sender`.
     */
    private fun compositeSampleOpenAPI(): OpenAPI {
        val stringSchema = { Schema<Any>().apply { types = mutableSetOf("string") } }
        val refSchema = { ref: String -> Schema<Any>().apply { `$ref` = ref } }
        val actionSchema = { value: String ->
            Schema<Any>().apply {
                types = mutableSetOf("string")
                enum = listOf(value)
            }
        }
        val objectSchema = { props: Map<String, Schema<Any>> ->
            Schema<Any>().apply {
                types = mutableSetOf("object")
                properties = LinkedHashMap(props)
            }
        }

        val openAPI = OpenAPI()
        openAPI.schema("simple-user", objectSchema(mapOf("login" to stringSchema())))
        openAPI.schema(
            "webhook-combo-simple",
            objectSchema(
                mapOf(
                    "action" to actionSchema("simple"),
                    "sender" to refSchema("#/components/schemas/simple-user"),
                ),
            ),
        )
        openAPI.schema(
            "webhook-combo-composite",
            Schema<Any>().apply {
                oneOf =
                    listOf(
                        objectSchema(
                            mapOf(
                                "action" to actionSchema("composite"),
                                "sender" to refSchema("#/components/schemas/simple-user"),
                            ),
                        ),
                        objectSchema(
                            mapOf(
                                "action" to actionSchema("composite"),
                                "sender" to refSchema("#/components/schemas/simple-user"),
                                "note" to stringSchema(),
                            ),
                        ),
                    )
            },
        )

        openAPI.webhooks =
            linkedMapOf(
                "combo-simple" to webhook("combo", "webhook-combo-simple"),
                "combo-composite" to webhook("combo", "webhook-combo-composite"),
            )
        return openAPI
    }

    private fun webhook(
        subcategory: String,
        bodySchema: String,
    ): PathItem {
        val operation =
            Operation().apply {
                addExtension("x-github", mapOf("subcategory" to subcategory))
                requestBody =
                    RequestBody().content(
                        Content().addMediaType(
                            "application/json",
                            MediaType().schema(Schema<Any>().apply { `$ref` = "#/components/schemas/$bodySchema" }),
                        ),
                    )
            }
        return PathItem().post(operation)
    }
}