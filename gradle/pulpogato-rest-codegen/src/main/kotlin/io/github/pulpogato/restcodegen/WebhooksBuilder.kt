package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import com.palantir.javapoet.TypeVariableName
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.testExtension
import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.className
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.lang.model.element.Modifier
import kotlin.collections.get

object WebhooksBuilder {
    fun buildWebhooks(
        openAPI: OpenAPI,
        mainDir: File,
        restPackage: String,
        webhooksPackage: String,
        testDir: File,
    ) {
        val testController = buildTestController()

        openAPI.webhooks
            .entries
            .groupBy { getSubcategory(it.value.readOperationsMap().values.first())!! }
            .forEach { (k, v) ->
                val interfaceBuilder =
                    TypeSpec.interfaceBuilder("${k.pascalCase()}Webhooks")
                        .addModifiers(Modifier.PUBLIC)
                        .addTypeVariable(TypeVariableName.get("T"))

                val unitTestBuilder =
                    TypeSpec.classBuilder("${k.pascalCase()}WebhooksTest")
                        .addAnnotation(testExtension())

                val requestBodyTypes = mutableMapOf<String, Pair<String, ClassName>>()
                v.forEach { (name, webhook) ->
                    val requestBody = createWebhookInterface(name, webhook, openAPI, restPackage, interfaceBuilder, unitTestBuilder, testController)
                    val methodName = "process" + webhook.readOperationsMap().values.first().operationId.replace("/", "-").pascalCase()
                    requestBodyTypes[name] = Pair(methodName, requestBody)
                }

                if (v.size > 1) {
                    val methodBuilder =
                        MethodSpec.methodBuilder("process${k.pascalCase()}")
                            .addAnnotation(generated(0))
                            .addAnnotation(
                                AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                                    .addMember("headers", "\$S", "X-Github-Event=${k.replace("-", "_")}")
                                    .build(),
                            )
                            .returns(ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"), TypeVariableName.get("T")))
                            .addException(Types.EXCEPTION)
                            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                    val headers = v[0].value.readOperationsMap().entries.first().value.parameters.filter { it.`in` == "header" }.map { it.name }
                    headers
                        .forEach {
                            val required =
                                when {
                                    it.startsWith("X-Hub-Signature") -> false
                                    it.startsWith("X-GitHub-Enterprise") -> false
                                    else -> true
                                }
                            val parameterAnnotation =
                                AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestHeader"))
                                    .addMember("value", "\$S", it)
                            if (!required) {
                                parameterAnnotation.addMember("required", "\$L", false)
                            }
                            methodBuilder
                                .addParameter(
                                    ParameterSpec.builder(Types.STRING, it.camelCase())
                                        .addAnnotation(parameterAnnotation.build())
                                        .addAnnotation(generated(0))
                                        .build(),
                                )
                        }

                    val routerBuilder = StringWriter()
                    val printWriter = PrintWriter(routerBuilder)
                    printWriter.println(
                        "final var action = (requestBody.isObject() && requestBody.has(\"action\")) ? requestBody.get(\"action\").asText() : null;",
                    )
                    printWriter.println("return switch (action) {")
                    requestBodyTypes.forEach { (name, methodNameAndType) ->
                        val cleanedAction = name.replace("-", "_").replace(k, "").replace(Regex("^_"), "")
                        val (methodName, type) = methodNameAndType
                        printWriter.print("    case \"$cleanedAction\" -> $methodName(${headers.joinToString(", ") { it.camelCase() }},")
                        printWriter.println(" getObjectMapper().treeToValue(requestBody, ${type.simpleName()}.class));")
                    }
                    printWriter.println("    default -> ${"$"}T.badRequest().build();")
                    printWriter.println("};")

                    interfaceBuilder
                        .addMethod(
                            methodBuilder
                                .addParameter(
                                    ParameterSpec
                                        .builder(
                                            ClassName.get(JsonNode::class.java),
                                            "requestBody",
                                        )
                                        .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestBody")).build())
                                        .addAnnotation(generated(0))
                                        .build(),
                                )
                                .addCode(routerBuilder.toString(), ClassName.get("org.springframework.http", "ResponseEntity"))
                                .build(),
                        )
                        .addMethod(
                            MethodSpec.methodBuilder("getObjectMapper")
                                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                .returns(ClassName.get(ObjectMapper::class.java))
                                .build(),
                        )
                }

                val interfaceSpec = interfaceBuilder.build()
                if (interfaceSpec.methodSpecs().isNotEmpty()) {
                    JavaFile.builder(webhooksPackage, interfaceSpec)
                        .build()
                        .writeTo(mainDir)

                    testController.addSuperinterface(
                        ParameterizedTypeName.get(
                            ClassName.get(webhooksPackage, "${k.pascalCase()}Webhooks"),
                            TEST_RESPONSE,
                        ),
                    )
                }

                val testClassSpec = unitTestBuilder.build()
                if (testClassSpec.typeSpecs().isNotEmpty()) {
                    JavaFile.builder(webhooksPackage, testClassSpec)
                        .build()
                        .writeTo(testDir)
                }
            }
        val testConfig = buildTestConfig(testController.build())
        val integrationTestBuilder = buildIntegrationTest(testConfig.build())
        JavaFile.builder(webhooksPackage, integrationTestBuilder.build())
            .build()
            .writeTo(testDir)
    }

    private fun buildIntegrationTest(testConfig: TypeSpec): TypeSpec.Builder =
        TypeSpec.classBuilder("WebhooksIntegrationTest")
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.test.autoconfigure.web.servlet", "WebMvcTest")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.test.autoconfigure.web.servlet", "AutoConfigureMockMvc")).build())
            .addField(
                FieldSpec.builder(ClassName.get("org.springframework.test.web.servlet", "MockMvc"), "mvc")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.beans.factory.annotation", "Autowired")).build())
                    .build(),
            )
            .addMethod(
                MethodSpec.methodBuilder("files")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(
                        ParameterizedTypeName.get(
                            ClassName.get("java.util.stream", "Stream"),
                            ClassName.get("org.junit.jupiter.params.provider", "Arguments"),
                        ),
                    )
                    .addStatement(
                        "return \$T.getArguments(\"\$L\")",
                        ClassName.get("io.github.pulpogato.test", "WebhookHelper"),
                        Context.instance.get().version,
                    )
                    .build(),
            )
            .addMethod(
                MethodSpec.methodBuilder("doTest")
                    .addParameter(Types.STRING, "hookname")
                    .addParameter(Types.STRING, "filename")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.params", "ParameterizedTest")).build())
                    .addAnnotation(
                        AnnotationSpec.builder(ClassName.get("org.junit.jupiter.params.provider", "MethodSource"))
                            .addMember("value", "\$S", "files")
                            .build(),
                    )
                    .addException(Types.EXCEPTION)
                    .addStatement("\$T.testWebhook(hookname, filename, mvc)", ClassName.get("io.github.pulpogato.test", "WebhookHelper"))
                    .build(),
            )
            .addType(testConfig)

    private fun buildTestConfig(testController: TypeSpec): TypeSpec.Builder =
        TypeSpec.classBuilder("TestConfig")
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.test.context", "TestConfiguration")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot", "SpringBootConfiguration")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.servlet.config.annotation", "EnableWebMvc")).build())
            .addMethod(
                MethodSpec.methodBuilder("objectMapper")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.context.annotation", "Bean")).build())
                    .returns(ClassName.get(ObjectMapper::class.java))
                    .addStatement(
                        """
                        return new ${"$"}T()
                        .setSerializationInclusion(${"$"}T.Include.NON_NULL)
                        .registerModule(new ${"$"}T())
                        .disable(${"$"}T.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                        .configure(${"$"}T.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        """.trimIndent(),
                        ClassName.get(ObjectMapper::class.java),
                        ClassName.get(JsonInclude::class.java),
                        ClassName.get(JavaTimeModule::class.java),
                        ClassName.get(DeserializationFeature::class.java),
                        ClassName.get(DeserializationFeature::class.java),
                    )
                    .build(),
            )
            .addType(testController)
            .addModifiers(Modifier.STATIC)

    private fun buildTestController(): TypeSpec.Builder =
        TypeSpec.classBuilder("TestController")
            .addField(
                FieldSpec.builder(ClassName.get(ObjectMapper::class.java), "objectMapper")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.beans.factory.annotation", "Autowired")).build())
                    .build(),
            )
            .addMethod(
                MethodSpec.methodBuilder("getObjectMapper")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get(ObjectMapper::class.java))
                    .addStatement("return objectMapper")
                    .build(),
            )
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RestController")).build())
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestMapping"))
                    .addMember("value", "\$S", "/webhooks")
                    .build(),
            )
            .addModifiers(Modifier.STATIC)

    private fun createWebhookInterface(
        name: String,
        webhook: PathItem,
        openAPI: OpenAPI,
        restPackage: String,
        interfaceBuilder: TypeSpec.Builder,
        unitTestBuilder: TypeSpec.Builder,
        testController: TypeSpec.Builder,
    ): ClassName {
        var bodyType: ClassName? = null
        if (webhook.readOperationsMap().size != 1) {
            throw RuntimeException("Webhook $name has more than one operation")
        }

        val tests = mutableListOf<MethodSpec>()
        val (_, operation) = webhook.readOperationsMap().entries.first()

        val javadoc =
            mutableListOf(
                MarkdownHelper.mdToHtml(operation.summary) +
                    if (operation.description != null) "\n<br/>" + MarkdownHelper.mdToHtml(operation.description) else "",
                "\n",
            )

        val methodName = "process" + operation.operationId.replace("/", "-").pascalCase()
        val methodSpecBuilder =
            Context.withSchemaStack("#", "webhooks", name, "post") {
                val methodSpecBuilder =
                    MethodSpec.methodBuilder(methodName)
                        .addAnnotation(generated(0))
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                                .addMember("headers", "\$S", "X-Github-Event=$name")
                                .build(),
                        )
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(
                            ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"), TypeVariableName.get("T")),
                        )

                operation.parameters.filter { it.`in` == "header" }.forEach {
                    javadoc.add("@param ${it.name.camelCase()} '${it.name}' header. Example: <code>${it.example}</code>")
                }
                javadoc.add("@param requestBody The request body")
                javadoc.add("")
                javadoc.add("@return It doesn't really matter. A 200 means success. Anything else means failure.")
                if (operation.externalDocs != null) {
                    javadoc.add("")
                    javadoc.add("@see <a href=\"${operation.externalDocs.url}\">${operation.externalDocs.description ?: "GitHub Docs"}</a>")
                }

                val requestBody = operation.requestBody

                requestBody.content
                    .filter { entry -> entry.key.contains("json") }
                    .forEach { firstEntry ->
                        if (firstEntry.value.schema.`$ref` != null) {
                            val ref = firstEntry.value.schema.`$ref`.replace("#/components/schemas/", "")
                            val schema = openAPI.components.schemas.entries.first { it.key == ref }
                            val type = schema.className()
                            operation.parameters.filter { it.`in` == "header" }.forEachIndexed { idx, it ->
                                val required =
                                    when {
                                        it.name.startsWith("X-Hub-Signature") -> false
                                        it.name.startsWith("X-GitHub-Enterprise") -> false
                                        else -> true
                                    }
                                val parameterAnnotation =
                                    AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestHeader"))
                                        .addMember("value", "\$S", it.name)
                                if (!required) {
                                    parameterAnnotation.addMember("required", "\$L", false)
                                }
                                Context.withSchemaStack("parameters", idx.toString()) {
                                    methodSpecBuilder
                                        .addParameter(
                                            ParameterSpec.builder(Types.STRING, it.name.camelCase())
                                                .addAnnotation(parameterAnnotation.build())
                                                .addAnnotation(generated(0))
                                                .build(),
                                        )
                                }
                            }
                            bodyType = ClassName.get("$restPackage.schemas", type)
                            Context.withSchemaStack("requestBody") {
                                methodSpecBuilder.addParameter(
                                    ParameterSpec.builder(bodyType, "requestBody")
                                        .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestBody")).build())
                                        .addAnnotation(generated(0))
                                        .build(),
                                )
                            }

                            methodSpecBuilder.addJavadoc(javadoc.joinToString("\n"))

                            val examples = firstEntry.value.examples
                            if (firstEntry.key.contains("json")) {
                                Context.withSchemaStack("requestBody", "content", firstEntry.key, "examples") {
                                    examples?.forEach { (key, value) ->
                                        val ref1 = value.`$ref`
                                        val example = openAPI.components.examples.entries.firstOrNull { it.key == ref1.replace("#/components/examples/", "") }
                                        if (example != null) {
                                            tests.add(TestBuilder.buildTest(key, example.value.value, bodyType!!))
                                        }
                                    }
                                }
                            }
                        } else {
                            throw RuntimeException("Unknown type for ${firstEntry.value.schema}")
                        }
                    }

                methodSpecBuilder
            }

        if (tests.isNotEmpty()) {
            unitTestBuilder.addType(
                TypeSpec.classBuilder(name.pascalCase())
                    .addMethods(tests)
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                    .build(),
            )
        }

        val methodSpec =
            methodSpecBuilder
                .addException(Types.EXCEPTION)
                .build()
        interfaceBuilder.addMethod(methodSpec)

        val testControllerMethod = buildTestControllerMethod(methodName, name)

        methodSpec.parameters().forEach { t ->
            testControllerMethod.addParameter(ParameterSpec.builder(t.type(), t.name()).build())
        }

        testController.addMethod(testControllerMethod.build())

        return bodyType!!
    }

    private val TEST_RESPONSE = ClassName.get("io.github.pulpogato.test", "TestWebhookResponse")

    private fun buildTestControllerMethod(
        methodName: String,
        name: String,
    ): MethodSpec.Builder =
        MethodSpec.methodBuilder(methodName)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
            .addException(Types.EXCEPTION)
            .returns(
                ParameterizedTypeName.get(
                    ClassName.get("org.springframework.http", "ResponseEntity"),
                    TEST_RESPONSE,
                ),
            )
            .addModifiers(Modifier.PUBLIC)
            .addStatement(
                """
                return ${"$"}T.ok(
                    ${"$"}T.builder()
                        .webhookName("${"$"}L")
                        .body(objectMapper.writeValueAsString(requestBody))
                        .build()
                    )
                """.trimIndent(),
                ClassName.get("org.springframework.http", "ResponseEntity"),
                TEST_RESPONSE,
                name,
            )

    private fun getSubcategory(operation: Operation): String? {
        val xGitHub = operation.extensions["x-github"] ?: throw RuntimeException("Missing x-github extension")
        val subcategory = (xGitHub as Map<*, *>)["subcategory"] as String?
        return subcategory
    }
}