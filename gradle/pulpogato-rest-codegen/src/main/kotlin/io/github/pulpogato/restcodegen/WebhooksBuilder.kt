package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonInclude
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
import io.github.pulpogato.restcodegen.Annotations.lombok
import io.github.pulpogato.restcodegen.Annotations.testExtension
import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.className
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.lang.model.element.Modifier

class WebhooksBuilder {
    data class WebhookBuilderParams(
        val interfaceBuilder: TypeSpec.Builder,
        val unitTestBuilder: TypeSpec.Builder,
        val testControllerBuilder: TypeSpec.Builder,
    )

    data class ProcessingContext(
        val openAPI: OpenAPI,
        val restPackage: String,
        val operation: Operation,
        val methodSpecBuilder: MethodSpec.Builder,
        val context: Context,
        val tests: MutableList<MethodSpec>,
        val builders: WebhookBuilderParams,
        val name: String,
        val testResourcesDir: File,
    )

    fun buildWebhooks(
        context: Context,
        mainDir: File,
        testDir: File,
        restPackage: String,
        webhooksPackage: String,
    ) {
        // Create test resources directory for large JSON examples
        val testResourcesDir = File(testDir.parentFile, "resources")
        val testControllerBuilder = buildTestController()

        val openAPI = context.openAPI
        openAPI.webhooks
            .entries
            .groupBy {
                getSubcategory(
                    it.value
                        .readOperationsMap()
                        .values
                        .first(),
                )
            }.forEach { (subcategory, v) ->
                val interfaceBuilder = getInterfaceBuilder(subcategory)

                val unitTestBuilder = getUnitTestBuilder(subcategory)

                val requestBodyTypes = mutableMapOf<String, Pair<String, ClassName>>()
                val builders = WebhookBuilderParams(interfaceBuilder, unitTestBuilder, testControllerBuilder)
                v.forEach { (name, webhook) ->
                    val requestBody =
                        createWebhookInterface(context, name, webhook, openAPI, restPackage, builders, testResourcesDir)
                    val methodName =
                        "process" +
                            webhook
                                .readOperationsMap()
                                .values
                                .first()
                                .operationId
                                .replace("/", "-")
                                .pascalCase()
                    requestBodyTypes[name] = Pair(methodName, requestBody)
                }

                if (v.size > 1) {
                    buildSyntheticMethod(
                        context,
                        subcategory,
                        requestBodyTypes,
                        interfaceBuilder,
                        v.first().value,
                    )
                }

                val interfaceSpec = interfaceBuilder.build()
                if (interfaceSpec.methodSpecs().isNotEmpty()) {
                    JavaFile
                        .builder(webhooksPackage, interfaceSpec)
                        .build()
                        .writeTo(mainDir)

                    val webhooksClassname = ClassName.get(webhooksPackage, "${subcategory.pascalCase()}Webhooks")
                    testControllerBuilder.addSuperinterface(
                        ParameterizedTypeName.get(webhooksClassname, TEST_RESPONSE),
                    )
                }

                val testClassSpec = unitTestBuilder.build()
                if (testClassSpec.typeSpecs().isNotEmpty()) {
                    JavaFile
                        .builder(webhooksPackage, testClassSpec)
                        .build()
                        .writeTo(testDir)
                }
            }
        val testConfig = buildTestConfig(testControllerBuilder.build())
        val integrationTestBuilder = buildIntegrationTest(context, testConfig.build())
        JavaFile
            .builder(webhooksPackage, integrationTestBuilder.build())
            .build()
            .writeTo(testDir)
    }

    private fun getUnitTestBuilder(subcategory: String): TypeSpec.Builder =
        TypeSpec
            .classBuilder("${subcategory.pascalCase()}WebhooksTest")
            .addAnnotation(testExtension())

    private fun getInterfaceBuilder(subcategory: String): TypeSpec.Builder =
        TypeSpec
            .interfaceBuilder("${subcategory.pascalCase()}Webhooks")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(TypeVariableName.get("T"))

    private fun buildSyntheticMethod(
        context: Context,
        subcategory: String,
        requestBodyTypes: Map<String, Pair<String, ClassName>>,
        interfaceBuilder: TypeSpec.Builder,
        pathItem: PathItem,
    ) {
        val methodBuilder =
            MethodSpec
                .methodBuilder("process${subcategory.pascalCase()}")
                .addAnnotation(generated(0, context.withSchemaStack("#", "synthetic")))
                .addAnnotation(createHeaderAnnotation(subcategory))
                .returns(ParameterizedTypeName.get(ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"), TypeVariableName.get("T")))
                .addException(Types.EXCEPTION)
                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
        val headerNames =
            pathItem
                .readOperationsMap()
                .entries
                .first()
                .value.parameters
                .filter { it.`in` == "header" }
                .map { it.name }
        headerNames
            .forEach {
                methodBuilder
                    .addParameter(
                        ParameterSpec
                            .builder(Types.STRING, it.camelCase())
                            .addAnnotation(getParameterAnnotation(it).build())
                            .addAnnotation(generated(0, context.withSchemaStack("#", "synthetic")))
                            .build(),
                    )
            }

        val router = buildRouter(requestBodyTypes, subcategory, headerNames)

        methodBuilder
            .addParameter(buildRequestBodyParameter(context.withSchemaStack("#", "synthetic")))
            .addCode(router, ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"))

        interfaceBuilder
            .addMethod(methodBuilder.build())
            .addMethod(getObjectMapperMethod())
    }

    private fun getObjectMapperMethod(): MethodSpec =
        MethodSpec
            .methodBuilder("getObjectMapper")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(ClassName.get(ObjectMapper::class.java))
            .build()

    private fun createHeaderAnnotation(subcategory: String): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "PostMapping"))
            .addMember("headers", $$"$S", "X-Github-Event=${subcategory.replace("-", "_")}")
            .build()

    private fun buildRequestBodyParameter(context: Context): ParameterSpec =
        ParameterSpec
            .builder(ClassName.get(JsonNode::class.java), "requestBody")
            .addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RequestBody")).build())
            .addAnnotation(generated(0, context))
            .build()

    private fun buildRouter(
        requestBodyTypes: Map<String, Pair<String, ClassName>>,
        subcategory: String,
        headerNames: List<String>,
    ): String {
        val routerBuilder = StringWriter()
        val printWriter = PrintWriter(routerBuilder)
        printWriter.println(
            //language=java
            """final var action = (requestBody.isObject() && requestBody.has("action")) ? requestBody.get("action").asText() : "N/A";""",
        )
        printWriter.println("return switch (action) {")
        requestBodyTypes.forEach { (name, methodNameAndType) ->
            val cleanedAction = name.replace("-", "_").replace(subcategory, "").replace(Regex("^_"), "")
            val (methodName, type) = methodNameAndType
            printWriter.print("    case \"$cleanedAction\" -> $methodName(${headerNames.joinToString(", ") { it.camelCase() }},")
            printWriter.println(" getObjectMapper().treeToValue(requestBody, ${type.simpleName()}.class));")
        }
        printWriter.println($$"    default -> $T.badRequest().build();")
        printWriter.println("};")
        val router = routerBuilder.toString()
        return router
    }

    private fun getParameterAnnotation(string: String): AnnotationSpec.Builder {
        val required =
            when {
                string.startsWith("X-Hub-Signature") -> false
                string.startsWith("X-GitHub-Enterprise") -> false
                else -> true
            }
        val parameterAnnotation =
            getRequestHeaderAnnotation(string)
        if (!required) {
            parameterAnnotation.addMember("required", $$"$L", false)
        }
        return parameterAnnotation
    }

    private fun buildIntegrationTest(
        context: Context,
        testConfig: TypeSpec,
    ): TypeSpec.Builder =
        TypeSpec
            .classBuilder("WebhooksIntegrationTest")
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.webmvc.test.autoconfigure", "WebMvcTest")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.webmvc.test.autoconfigure", "AutoConfigureMockMvc")).build())
            .addField(createMvcField())
            .addMethod(
                MethodSpec
                    .methodBuilder("files")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(
                        ParameterizedTypeName.get(
                            ClassName.get("java.util.stream", "Stream"),
                            ClassName.get("org.junit.jupiter.params.provider", "Arguments"),
                        ),
                    ).addStatement(
                        $$"return $T.getArguments(\"$L\")",
                        ClassName.get(PACKAGE_PULPOGATO_TEST, "WebhookHelper"),
                        context.version,
                    ).build(),
            ).addMethod(
                MethodSpec
                    .methodBuilder("doTest")
                    .addParameter(Types.STRING, "hookname")
                    .addParameter(Types.STRING, "filename")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.params", "ParameterizedTest")).build())
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ClassName.get("org.junit.jupiter.params.provider", "MethodSource"))
                            .addMember("value", $$"$S", "files")
                            .build(),
                    ).addException(Types.EXCEPTION)
                    .addStatement($$"$T.testWebhook(hookname, filename, mvc)", ClassName.get(PACKAGE_PULPOGATO_TEST, "WebhookHelper"))
                    .build(),
            ).addType(testConfig)

    private fun createMvcField(): FieldSpec =
        FieldSpec
            .builder(ClassName.get("org.springframework.test.web.servlet", "MockMvc"), "mvc")
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.beans.factory.annotation", "Autowired")).build())
            .build()

    private fun buildTestConfig(testController: TypeSpec): TypeSpec.Builder =
        TypeSpec
            .classBuilder("TestConfig")
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.test.context", "TestConfiguration")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot", "SpringBootConfiguration")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.servlet.config.annotation", "EnableWebMvc")).build())
            .addMethod(
                MethodSpec
                    .methodBuilder("objectMapper")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.context.annotation", "Bean")).build())
                    .returns(ClassName.get(ObjectMapper::class.java))
                    .addStatement(
                        $$"""
                        return $T.builder()
                                .changeDefaultPropertyInclusion(value -> value.withValueInclusion($T.Include.NON_NULL))
                                .disable($T.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                                .configure($T.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                .build()
                        """.trimIndent(),
                        ClassName.get(JsonMapper::class.java),
                        ClassName.get(JsonInclude::class.java),
                        ClassName.get(DateTimeFeature::class.java),
                        ClassName.get(DeserializationFeature::class.java),
                    ).build(),
            ).addType(testController)
            .addModifiers(Modifier.STATIC)

    private fun buildTestController(): TypeSpec.Builder =
        TypeSpec
            .classBuilder("TestController")
            .addField(
                FieldSpec
                    .builder(ClassName.get(ObjectMapper::class.java), "objectMapper")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.beans.factory.annotation", "Autowired")).build())
                    .addAnnotation(lombok("Getter"))
                    .build(),
            ).addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RestController")).build())
            .addAnnotation(
                AnnotationSpec
                    .builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RequestMapping"))
                    .addMember("value", $$"$S", "/webhooks")
                    .build(),
            ).addModifiers(Modifier.STATIC)

    private fun createWebhookInterface(
        context: Context,
        name: String,
        webhook: PathItem,
        openAPI: OpenAPI,
        restPackage: String,
        builders: WebhookBuilderParams,
        testResourcesDir: File,
    ): ClassName {
        if (webhook.readOperationsMap().size != 1) {
            throw RuntimeException("Webhook $name has more than one operation")
        }

        val tests = mutableListOf<MethodSpec>()
        val (_, operation) = webhook.readOperationsMap().entries.first()

        val methodName = "process" + operation.operationId.replace("/", "-").pascalCase()
        val context1 = context.withSchemaStack("#", "webhooks", name, "post")
        val methodSpecBuilder =
            MethodSpec
                .methodBuilder(methodName)
                .addAnnotation(generated(0, context1))
                .addAnnotation(createPostMappingAnnotation(name))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(
                    ParameterizedTypeName.get(ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"), TypeVariableName.get("T")),
                )

        val requestBody = operation.requestBody
        val processingContext = ProcessingContext(openAPI, restPackage, operation, methodSpecBuilder, context1, tests, builders, name, testResourcesDir)
        val bodyType = processRequestBody(requestBody, processingContext)

        val javadoc = buildJavadoc(operation)
        val methodSpec =
            methodSpecBuilder
                .addJavadoc($$"$L", javadoc.joinToString("\n"))
                .addException(Types.EXCEPTION)
                .build()
        builders.interfaceBuilder.addMethod(methodSpec)

        val testControllerMethod = buildTestControllerMethod(methodName, name)

        methodSpec.parameters().forEach { t ->
            testControllerMethod.addParameter(ParameterSpec.builder(t.type(), t.name()).build())
        }

        builders.testControllerBuilder.addMethod(testControllerMethod.build())

        return bodyType
    }

    private fun createPostMappingAnnotation(name: String): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "PostMapping"))
            .addMember("headers", $$"$S", "X-Github-Event=$name")
            .build()

    companion object {
        private val TEST_RESPONSE = ClassName.get("io.github.pulpogato.test", "TestWebhookResponse")
        private const val PACKAGE_SPRING_WEB_BIND_ANNOTATION = "org.springframework.web.bind.annotation"
        private const val PACKAGE_SPRING_HTTP = "org.springframework.http"
        private const val PACKAGE_PULPOGATO_TEST = "io.github.pulpogato.test"
    }

    private fun buildTestControllerMethod(
        methodName: String,
        name: String,
    ): MethodSpec.Builder =
        MethodSpec
            .methodBuilder(methodName)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
            .addException(Types.EXCEPTION)
            .returns(
                ParameterizedTypeName.get(
                    ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                    TEST_RESPONSE,
                ),
            ).addModifiers(Modifier.PUBLIC)
            .addStatement(
                $$"""
                return $T.ok(
                    $T.builder()
                        .webhookName("$L")
                        .body(objectMapper.writeValueAsString(requestBody))
                        .build()
                    )
                """.trimIndent(),
                ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                TEST_RESPONSE,
                name,
            )

    private fun buildJavadoc(operation: Operation): List<String> {
        val javadoc =
            mutableListOf(
                MarkdownHelper.mdToHtml(operation.summary) +
                    if (operation.description != null) "\n<br/>" + MarkdownHelper.mdToHtml(operation.description) else "",
                "\n",
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

        return javadoc
    }

    private fun processRequestBody(
        requestBody: io.swagger.v3.oas.models.parameters.RequestBody,
        ctx: ProcessingContext,
    ): ClassName {
        var bodyType: ClassName? = null
        requestBody.content
            .filter { entry -> entry.key.contains("json") }
            .forEach { entry ->
                if (entry.value.schema.`$ref` != null) {
                    bodyType = processJsonRequestBody(entry, ctx)
                } else {
                    throw RuntimeException("Unknown type for ${entry.value.schema}")
                }
            }
        return bodyType ?: throw RuntimeException("No body type found")
    }

    private fun processJsonRequestBody(
        entry: Map.Entry<String, io.swagger.v3.oas.models.media.MediaType>,
        ctx: ProcessingContext,
    ): ClassName {
        val ref =
            entry.value.schema.`$ref`
                .replace("#/components/schemas/", "")
        val schema =
            ctx.openAPI.components.schemas.entries
                .first { it.key == ref }
        val type = schema.className()

        addHeaderParameters(ctx.operation, ctx.methodSpecBuilder, ctx.context)

        val bodyType = ClassName.get("${ctx.restPackage}.schemas", type)
        ctx.methodSpecBuilder.addParameter(
            ParameterSpec
                .builder(bodyType, "requestBody")
                .addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RequestBody")).build())
                .addAnnotation(generated(0, ctx.context.withSchemaStack("requestBody")))
                .build(),
        )

        processExamples(entry, ctx.openAPI, ctx.context, ctx.tests, bodyType, ctx.testResourcesDir)

        if (ctx.tests.isNotEmpty()) {
            ctx.builders.unitTestBuilder.addType(
                TypeSpec
                    .classBuilder(ctx.name.pascalCase())
                    .addMethods(ctx.tests)
                    .addAnnotation(generated(0, ctx.context.withSchemaStack("requestBody", "content", entry.key, "schema")))
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                    .build(),
            )
        }

        return bodyType
    }

    private fun addHeaderParameters(
        operation: Operation,
        methodSpecBuilder: MethodSpec.Builder,
        context: Context,
    ) {
        operation.parameters.filter { it.`in` == "header" }.forEachIndexed { idx, it ->
            val required =
                when {
                    it.name.startsWith("X-Hub-Signature") -> false
                    it.name.startsWith("X-GitHub-Enterprise") -> false
                    else -> true
                }
            val parameterAnnotation =
                getRequestHeaderAnnotation(it.name)
            if (!required) {
                parameterAnnotation.addMember("required", $$"$L", false)
            }
            val contextForParameters = context.withSchemaStack("parameters", idx.toString())
            methodSpecBuilder.addParameter(
                ParameterSpec
                    .builder(Types.STRING, it.name.camelCase())
                    .addAnnotation(parameterAnnotation.build())
                    .addAnnotation(generated(0, contextForParameters))
                    .build(),
            )
        }
    }

    private fun getRequestHeaderAnnotation(requestHeaderName: String): AnnotationSpec.Builder =
        AnnotationSpec
            .builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RequestHeader"))
            .addMember("value", $$"$S", requestHeaderName)

    private fun processExamples(
        entry: Map.Entry<String, io.swagger.v3.oas.models.media.MediaType>,
        openAPI: OpenAPI,
        context: Context,
        tests: MutableList<MethodSpec>,
        bodyType: ClassName,
        testResourcesDir: File,
    ) {
        val examples = entry.value.examples
        if (entry.key.contains("json")) {
            examples?.forEach { (key, value) ->
                val ref1 = value.`$ref`
                val example =
                    openAPI.components.examples.entries
                        .firstOrNull { it.key == ref1.replace("#/components/examples/", "") }
                if (example != null) {
                    TestBuilder
                        .buildTest(
                            context.withSchemaStack("requestBody", "content", entry.key, "examples", key),
                            key,
                            example.value.value,
                            bodyType,
                            testResourcesDir,
                        )?.let { tests.add(it) }
                }
            }
        }
    }

    private fun getSubcategory(operation: Operation): String {
        val xGitHub = operation.extensions["x-github"] ?: throw RuntimeException("Missing x-github extension")
        val subcategory = (xGitHub as Map<*, *>)["subcategory"] as String
        return subcategory
    }
}