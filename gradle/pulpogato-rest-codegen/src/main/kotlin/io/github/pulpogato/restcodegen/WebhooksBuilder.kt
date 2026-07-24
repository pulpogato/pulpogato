package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonInclude
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterSpec.builder
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import com.palantir.javapoet.TypeVariableName
import com.palantir.javapoet.WildcardTypeName
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.nullable
import io.github.pulpogato.restcodegen.Annotations.testExtension
import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.className
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.MediaType
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
    private lateinit var webhookHeadersType: ClassName
    private lateinit var webhookHeadersResolverType: ClassName

    data class WebhookBuilderParams(
        val interfaceBuilder: TypeSpec.Builder,
        val unitTestBuilder: TypeSpec.Builder,
        val testControllerBuilder: TypeSpec.Builder,
        val springEndpoint: Boolean,
        val supertype: WebhookSupertypes.Group?,
    )

    data class ProcessingContext(
        val restPackage: String,
        val operation: Operation,
        val methodSpecBuilder: MethodSpec.Builder,
        val context: Context,
        val tests: MutableList<MethodSpec>,
        val builders: WebhookBuilderParams,
        val name: String,
        val testResourcesDir: File,
        val springEndpoint: Boolean,
    )

    fun buildWebhooks(
        context: Context,
        mainDir: File,
        testDir: File,
        restPackage: String,
        webhooksPackage: String,
    ) {
        // Create the test resources directory for large JSON examples
        val testResourcesDir = File(testDir.parentFile, "resources")
        val testControllerBuilder = buildTestController()

        webhookHeadersType = ClassName.get(webhooksPackage, "WebhookHeaders")
        webhookHeadersResolverType = ClassName.get(webhooksPackage, "WebhookHeadersArgumentResolver")
        val headerFields = collectHeaderFields(context)
        JavaFile
            .builder(webhooksPackage, buildWebhookHeadersType(context, headerFields))
            .build()
            .writeTo(mainDir)
        JavaFile
            .builder(webhooksPackage, buildWebhookHeadersResolverType(context, headerFields))
            .build()
            .writeTo(mainDir)

        val openAPI = context.openAPI
        // The same supertypes SchemasBuilder generates; used here to route the synthetic handler
        // straight to the typed supertype when the subcategory can be deserialized polymorphically.
        val supertypesBySubcategory = WebhookSupertypes.compute(openAPI, "$restPackage.schemas").associateBy { it.subcategory }
        val requestBodyTypeByEventName = linkedMapOf<String, ClassName>()
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
                // For a multi-event subcategory only the synthetic process<Subcategory> is a real
                // Spring endpoint; the per-event methods are typed callbacks it dispatches to.
                val builders =
                    WebhookBuilderParams(
                        interfaceBuilder,
                        unitTestBuilder,
                        testControllerBuilder,
                        springEndpoint = v.size == 1,
                        supertype = supertypesBySubcategory[subcategory],
                    )
                v.forEach { (name, webhook) ->
                    val requestBody =
                        createWebhookInterface(context, name, webhook, restPackage, builders, testResourcesDir)
                    // Per-event methods only carry a real @RequestBody/header pairing when they ARE
                    // the Spring endpoint (single-event subcategory); for multi-event subcategories
                    // the only annotated endpoint is the synthetic dispatcher added below.
                    if (builders.springEndpoint) {
                        requestBodyTypeByEventName[name] = requestBody
                    }
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
                        builders,
                    )
                    // Only a discriminable supertype implements WebhookEvent; a non-discriminable group's
                    // synthetic dispatcher reads JsonNode instead, which has no typed body to publish here.
                    val supertype = builders.supertype
                    if (supertype != null && supertype.discriminable) {
                        requestBodyTypeByEventName[subcategory.replace("-", "_")] = supertype.supertype
                    }
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
        JavaFile
            .builder(webhooksPackage, buildWebhookEventTypes(requestBodyTypeByEventName))
            .build()
            .writeTo(mainDir)

        val testConfig = buildTestConfig(testControllerBuilder.build())
        val integrationTestBuilder = buildIntegrationTest(context, testConfig.build())
        JavaFile
            .builder(webhooksPackage, integrationTestBuilder.build())
            .build()
            .writeTo(testDir)
    }

    data class HeaderField(
        val headerName: String,
        val fieldName: String,
        val universal: Boolean,
    )

    /**
     * Derives a Java field name from a header name the same way for every header, so the generated
     * fields read the same as the ones that used to be handwritten (e.g. `X-GitHub-Delivery` ->
     * `githubDelivery`, `X-Hub-Signature-256` -> `hubSignature256`).
     */
    private fun headerFieldName(headerName: String): String {
        val withoutPrefix = if (headerName.startsWith("x-", ignoreCase = true)) headerName.substring(2) else headerName
        return withoutPrefix.lowercase().camelCase()
    }

    /**
     * Header sets can differ across, and even within, a schema's webhook operations (GHES sends two
     * extra enterprise headers on some but not all operations). Headers present on every operation
     * become required fields; the rest become nullable fields.
     */
    private fun collectHeaderFields(context: Context): List<HeaderField> {
        val operations =
            context.openAPI.webhooks
                .orEmpty()
                .values
                .flatMap { it.readOperationsMap().values }
        val headerSets =
            operations.map { operation ->
                operation.parameters
                    .orEmpty()
                    .filter { it.`in` == "header" }
                    .map { it.name }
                    .toSet()
            }
        val orderedNames = LinkedHashSet<String>()
        headerSets.forEach { orderedNames.addAll(it) }
        val (universalNames, nonUniversalNames) = orderedNames.partition { name -> headerSets.all { name in it } }
        return (universalNames + nonUniversalNames).map { name -> HeaderField(name, headerFieldName(name), name in universalNames) }
    }

    private fun buildWebhookHeadersType(
        context: Context,
        headerFields: List<HeaderField>,
    ): TypeSpec {
        val builderType = webhookHeadersType.nestedClass("Builder")
        val extraHeadersType = ParameterizedTypeName.get(Types.MAP, Types.STRING, Types.STRING)
        val allFieldNames = headerFields.map { it.fieldName } + "extraHeaders"

        val classBuilder =
            TypeSpec
                .classBuilder(webhookHeadersType)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(generated(0, context.withSchemaStack("#", "webhooks")))
                .addJavadoc(
                    $$"""
                    The set of HTTP headers GitHub sends on webhook deliveries in this schema, bound as a
                    single parameter instead of one {@code @RequestHeader} argument per header.
                    <p>
                    Requires {@link $L} to be registered as a {@code HandlerMethodArgumentResolver} on
                    the {@code WebMvcConfigurer} in use.
                    """.trimIndent(),
                    webhookHeadersResolverType.simpleName(),
                )

        val constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).addParameter(builderType, "builder")

        // The builder is public API and doesn't enforce that every header gets set before build(), so
        // even "universal" headers can reach here unset - every field/getter needs to admit @Nullable.
        val nullableString = Types.STRING.annotated(nullable())
        val nullableExtraHeadersType = extraHeadersType.annotated(nullable())

        headerFields.forEach { field ->
            val nullability =
                when {
                    isKnownOptionalDespiteUniversal(field.headerName) -> ", absent when GitHub omits it (e.g. no secret configured)"
                    field.universal -> ""
                    else -> ", absent on operations that don't send it"
                }
            classBuilder.addField(
                FieldSpec
                    .builder(nullableString, field.fieldName, Modifier.PRIVATE, Modifier.FINAL)
                    .addJavadoc($$"'$L' header$L\n", field.headerName, nullability)
                    .build(),
            )
            classBuilder.addMethod(
                MethodSpec
                    .methodBuilder("get${field.fieldName.pascalCase()}")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(nullableString)
                    .addStatement($$"return $L", field.fieldName)
                    .build(),
            )
        }
        classBuilder.addField(
            FieldSpec
                .builder(nullableExtraHeadersType, "extraHeaders", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc(
                    "any other headers on the request, keyed by header name, for headers GitHub adds after " +
                        "this library was built\n",
                ).build(),
        )
        classBuilder.addMethod(
            MethodSpec
                .methodBuilder("getExtraHeaders")
                .addModifiers(Modifier.PUBLIC)
                .returns(nullableExtraHeadersType)
                .addStatement("return extraHeaders")
                .build(),
        )

        allFieldNames.forEach { fieldName -> constructor.addStatement($$"this.$L = builder.$L", fieldName, fieldName) }
        classBuilder.addMethod(constructor.build())

        classBuilder.addMethod(
            MethodSpec
                .methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderType)
                .addStatement($$"return new $T()", builderType)
                .build(),
        )
        classBuilder.addType(buildWebhookHeadersBuilderType(builderType, headerFields, extraHeadersType))

        return classBuilder.build()
    }

    private fun buildWebhookHeadersBuilderType(
        builderType: ClassName,
        headerFields: List<HeaderField>,
        extraHeadersType: ParameterizedTypeName,
    ): TypeSpec {
        val builderClassBuilder =
            TypeSpec
                .classBuilder(builderType)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())

        val nullableString = Types.STRING.annotated(nullable())
        val nullableExtraHeadersType = extraHeadersType.annotated(nullable())

        headerFields.forEach { field ->
            builderClassBuilder.addField(FieldSpec.builder(nullableString, field.fieldName, Modifier.PRIVATE).build())
            builderClassBuilder.addMethod(
                MethodSpec
                    .methodBuilder(field.fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderType)
                    .addParameter(nullableString, field.fieldName)
                    .addStatement($$"this.$L = $L", field.fieldName, field.fieldName)
                    .addStatement("return this")
                    .build(),
            )
        }
        builderClassBuilder.addField(FieldSpec.builder(nullableExtraHeadersType, "extraHeaders", Modifier.PRIVATE).build())
        builderClassBuilder.addMethod(
            MethodSpec
                .methodBuilder("extraHeaders")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderType)
                .addParameter(extraHeadersType, "extraHeaders")
                .addStatement("this.extraHeaders = extraHeaders")
                .addStatement("return this")
                .build(),
        )
        builderClassBuilder.addMethod(
            MethodSpec
                .methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(webhookHeadersType)
                .addStatement($$"return new $T(this)", webhookHeadersType)
                .build(),
        )
        return builderClassBuilder.build()
    }

    /**
     * GitHub omits these headers on some deliveries (no secret configured for the signature headers;
     * non-Enterprise-Server-originated deliveries for the enterprise headers) even when the schema
     * declares them on every operation without a `required: false` marker (it never marks any header
     * optional), so this can't be derived from [collectHeaderFields] alone.
     */
    private fun isKnownOptionalDespiteUniversal(headerName: String) =
        headerName.startsWith("X-Hub-Signature", ignoreCase = true) ||
            headerName.startsWith("X-GitHub-Enterprise", ignoreCase = true)

    private fun buildWebhookHeadersResolverType(
        context: Context,
        headerFields: List<HeaderField>,
    ): TypeSpec {
        val webRequestType = ClassName.get("org.springframework.web.context.request", "NativeWebRequest")
        val constructorCall = CodeBlock.builder().add($$"return $T.builder()\n", webhookHeadersType)
        headerFields.forEach { field ->
            if (field.universal && !isKnownOptionalDespiteUniversal(field.headerName)) {
                constructorCall.add($$".$L(requireHeader(parameter, webRequest, $S))\n", field.fieldName, field.headerName)
            } else {
                constructorCall.add($$".$L(webRequest.getHeader($S))\n", field.fieldName, field.headerName)
            }
        }
        constructorCall.add(".extraHeaders(extraHeaders(webRequest))\n")
        constructorCall.add(".build();\n")

        val resolveArgument =
            MethodSpec
                .methodBuilder("resolveArgument")
                .addAnnotation(Types.OVERRIDE)
                .addModifiers(Modifier.PUBLIC)
                .returns(Types.OBJECT)
                .addParameter(ClassName.get("org.springframework.core", "MethodParameter"), "parameter")
                .addParameter(
                    ParameterSpec
                        .builder(ClassName.get("org.springframework.web.method.support", "ModelAndViewContainer").annotated(nullable()), "mavContainer")
                        .build(),
                ).addParameter(webRequestType, "webRequest")
                .addParameter(
                    ParameterSpec
                        .builder(ClassName.get("org.springframework.web.bind.support", "WebDataBinderFactory").annotated(nullable()), "binderFactory")
                        .build(),
                ).addException(Types.EXCEPTION)
                .addCode(constructorCall.build())
                .build()

        val supportsParameter =
            MethodSpec
                .methodBuilder("supportsParameter")
                .addAnnotation(Types.OVERRIDE)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addParameter(ClassName.get("org.springframework.core", "MethodParameter"), "parameter")
                .addStatement($$"return $T.class.equals(parameter.getParameterType())", webhookHeadersType)
                .build()

        val requireHeader =
            MethodSpec
                .methodBuilder("requireHeader")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(Types.STRING)
                .addParameter(ClassName.get("org.springframework.core", "MethodParameter"), "parameter")
                .addParameter(webRequestType, "webRequest")
                .addParameter(Types.STRING, "name")
                .addException(ClassName.get("org.springframework.web.bind", "MissingRequestHeaderException"))
                .addStatement("var value = webRequest.getHeader(name)")
                .beginControlFlow("if (value == null)")
                .addStatement($$"throw new $T(name, parameter)", ClassName.get("org.springframework.web.bind", "MissingRequestHeaderException"))
                .endControlFlow()
                .addStatement("return value")
                .build()

        val extraHeadersMethod =
            MethodSpec
                .methodBuilder("extraHeaders")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(Types.MAP, Types.STRING, Types.STRING))
                .addParameter(webRequestType, "webRequest")
                .addStatement($$"var extraHeaders = new $T<$T, $T>()", ClassName.get("java.util", "LinkedHashMap"), Types.STRING, Types.STRING)
                .addStatement("var headerNames = webRequest.getHeaderNames()")
                .beginControlFlow("while (headerNames.hasNext())")
                .addStatement("var name = headerNames.next()")
                .beginControlFlow(
                    $$"if (!KNOWN_HEADERS.contains(name.toLowerCase($T.ROOT)))",
                    ClassName.get("java.util", "Locale"),
                ).addStatement("extraHeaders.put(name, webRequest.getHeader(name))")
                .endControlFlow()
                .endControlFlow()
                .addStatement("return extraHeaders")
                .build()

        val knownHeadersField =
            FieldSpec
                .builder(ParameterizedTypeName.get(ClassName.get("java.util", "Set"), Types.STRING), "KNOWN_HEADERS")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(
                    CodeBlock
                        .builder()
                        .add($$"$T.of(\n", ClassName.get("java.util", "Set"))
                        .add(headerFields.joinToString(",\n") { "\"${it.headerName.lowercase()}\"" })
                        .add(")")
                        .build(),
                ).build()

        return TypeSpec
            .classBuilder(webhookHeadersResolverType)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated(0, context.withSchemaStack("#", "webhooks")))
            .addSuperinterface(ClassName.get("org.springframework.web.method.support", "HandlerMethodArgumentResolver"))
            .addJavadoc(
                $$"""
                Binds a {@link $L} parameter from the incoming request's headers, in place of
                Spring's {@code @RequestHeader}, which only supports single-value or {@code Map} binding.
                <p>
                Register on the {@code WebMvcConfigurer} in use:
                <pre>{@code
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new $L());
                }
                }</pre>
                """.trimIndent(),
                webhookHeadersType.simpleName(),
                webhookHeadersResolverType.simpleName(),
            ).addField(knownHeadersField)
            .addMethod(supportsParameter)
            .addMethod(resolveArgument)
            .addMethod(requireHeader)
            .addMethod(extraHeadersMethod)
            .build()
    }

    /**
     * Generates a lookup from each `X-Github-Event` header value to the request body type Spring
     * deserializes it into, for callers that need to resolve a payload's type before dispatch.
     */
    private fun buildWebhookEventTypes(requestBodyTypeByEventName: Map<String, ClassName>): TypeSpec {
        val webhookEvent = ClassName.get(Types.COMMON_PACKAGE, "WebhookEvent")
        val classOfWildcard = ParameterizedTypeName.get(ClassName.get(Class::class.java), WildcardTypeName.subtypeOf(webhookEvent))
        val mapType = ParameterizedTypeName.get(Types.MAP, Types.STRING, classOfWildcard)

        // Explicit type witness avoids "vararg method call with 50+ poly arguments" javac warning.
        val initializer = CodeBlock.builder().add($$"$T.<$T, $T>ofEntries(\n", Types.MAP, Types.STRING, classOfWildcard).indent()
        requestBodyTypeByEventName.entries.forEachIndexed { index, (eventName, type) ->
            initializer.add($$"$T.entry($S, $T.class)", Types.MAP, eventName, type)
            initializer.add(if (index == requestBodyTypeByEventName.size - 1) "\n" else ",\n")
        }
        initializer.unindent().add(")")

        return TypeSpec
            .classBuilder("WebhookEventTypes")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addJavadoc("Maps each {@code X-Github-Event} header value to its webhook request body type.\n")
            .addField(
                FieldSpec
                    .builder(mapType, "BY_EVENT_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer(initializer.build())
                    .build(),
            ).addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .build()
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
        builders: WebhookBuilderParams,
    ) {
        val supertype = builders.supertype
        val syntheticContext = context.withSchemaStack("#", "synthetic")
        val isDiscriminable = supertype?.discriminable == true
        val methodBuilder =
            MethodSpec
                .methodBuilder("process${subcategory.pascalCase()}")
                .addAnnotation(generated(0, syntheticContext))
                .addAnnotation(createHeaderAnnotation(subcategory))
                .returns(ParameterizedTypeName.get(ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"), TypeVariableName.get("T")))
                .addException(Types.EXCEPTION)
                .addModifiers(Modifier.PUBLIC, if (isDiscriminable) Modifier.ABSTRACT else Modifier.DEFAULT)
        methodBuilder
            .addParameter(
                ParameterSpec
                    .builder(webhookHeadersType, "headers")
                    .addAnnotation(generated(0, syntheticContext))
                    .build(),
            )

        if (supertype != null && supertype.discriminable) {
            // Spring deserializes straight to the sealed supertype (via its @JsonSubTypes), so the
            // handler just pattern matches over the permitted subtypes — no manual JSON routing.
            methodBuilder
                .addParameter(buildTypedRequestBodyParameter(syntheticContext, supertype.supertype))
            val methodSpec = methodBuilder.build()
            builders.interfaceBuilder.addMethod(methodSpec)
            builders.testControllerBuilder.addMethod(
                buildSyntheticTestControllerMethod("process${subcategory.pascalCase()}", methodSpec, requestBodyTypes).build(),
            )
        } else {
            methodBuilder
                .addParameter(buildRequestBodyParameter(syntheticContext))
                .addCode(buildRouter(requestBodyTypes, subcategory), ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"))
            builders.interfaceBuilder
                .addMethod(methodBuilder.build())
                .addMethod(getObjectMapperMethod())
        }
    }

    private fun buildSyntheticTestControllerMethod(
        methodName: String,
        interfaceMethod: MethodSpec,
        requestBodyTypes: Map<String, Pair<String, ClassName>>,
    ): MethodSpec.Builder {
        val code = CodeBlock.builder()
        code.add("return switch (requestBody) {\n")
        requestBodyTypes.forEach { (name, methodNameAndType) ->
            val (_, type) = methodNameAndType
            code.add(
                $$"    case $T body -> $T.ok($T.builder().webhookName($S).body(objectMapper.writeValueAsString(body)).build());\n",
                type,
                ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                TEST_RESPONSE,
                name,
            )
        }
        code.add("};\n")

        val builder =
            MethodSpec
                .methodBuilder(methodName)
                .addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_JAVA_LANG, "Override")).build())
                .addModifiers(Modifier.PUBLIC)
                .returns(
                    ParameterizedTypeName.get(
                        ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                        TEST_RESPONSE,
                    ),
                )
        interfaceMethod.parameters().forEach { t ->
            builder.addParameter(ParameterSpec.builder(t.type(), t.name()).build())
        }
        builder.addCode(code.build())
        return builder
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

    private fun buildTypedRequestBodyParameter(
        context: Context,
        supertype: ClassName,
    ): ParameterSpec =
        ParameterSpec
            .builder(supertype, "requestBody")
            .addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RequestBody")).build())
            .addAnnotation(generated(0, context))
            .build()

    private fun buildRouter(
        requestBodyTypes: Map<String, Pair<String, ClassName>>,
        subcategory: String,
    ): String {
        val routerBuilder = StringWriter()
        val printWriter = PrintWriter(routerBuilder)
        printWriter.println(
            //language=java
            """final var action = (requestBody.isObject() && requestBody.has("action")) ? requestBody.get("action").asString() : "N/A";""",
        )
        printWriter.println("return switch (action) {")
        requestBodyTypes.forEach { (name, methodNameAndType) ->
            val cleanedAction = name.replace("-", "_").replace(subcategory, "").replace(Regex("^_"), "")
            val (methodName, type) = methodNameAndType
            printWriter.print("    case \"$cleanedAction\" -> $methodName(headers,")
            printWriter.println(" getObjectMapper().treeToValue(requestBody, ${type.simpleName()}.class));")
        }
        printWriter.println($$"    default -> $T.badRequest().build();")
        printWriter.println("};")
        val router = routerBuilder.toString()
        return router
    }

    private fun buildIntegrationTest(
        context: Context,
        testConfig: TypeSpec,
    ): TypeSpec.Builder =
        TypeSpec
            .classBuilder("WebhooksIntegrationTest")
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.webmvc.test.autoconfigure", "WebMvcTest")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot.webmvc.test.autoconfigure", "AutoConfigureMockMvc")).build())
            .addAnnotation(
                AnnotationSpec
                    .builder(ClassName.get("org.springframework.test.context", "ContextConfiguration"))
                    .addMember("classes", $$"$T.class", ClassName.get("", "WebhooksIntegrationTest.TestConfig"))
                    .build(),
            ).addField(createMvcField())
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
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.context.annotation", "Configuration")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.boot", "SpringBootConfiguration")).build())
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.servlet.config.annotation", "EnableWebMvc")).build())
            .addSuperinterface(ClassName.get("org.springframework.web.servlet.config.annotation", "WebMvcConfigurer"))
            .addMethod(
                MethodSpec
                    .methodBuilder("addArgumentResolvers")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_JAVA_LANG, "Override")).build())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(
                        ParameterizedTypeName.get(
                            ClassName.get("java.util", "List"),
                            ClassName.get("org.springframework.web.method.support", "HandlerMethodArgumentResolver"),
                        ),
                        "resolvers",
                    ).addStatement($$"resolvers.add(new $T())", webhookHeadersResolverType)
                    .build(),
            ).addMethod(
                MethodSpec
                    .methodBuilder("objectMapper")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.context.annotation", "Bean")).build())
                    .returns(ClassName.get(ObjectMapper::class.java))
                    .addStatement(
                        $$"""
                        return $T.builder()
                                .changeDefaultPropertyInclusion(value -> value.withValueInclusion($T.Include.NON_NULL))
                                .disable($T.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                                .configure($T.FAIL_ON_UNKNOWN_PROPERTIES, true)
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
                    .addModifiers(Modifier.PRIVATE)
                    .build(),
            ).addMethod(
                MethodSpec
                    .methodBuilder("getObjectMapper")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get(ObjectMapper::class.java))
                    .addStatement("return this.objectMapper")
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
        restPackage: String,
        builders: WebhookBuilderParams,
        testResourcesDir: File,
    ): ClassName {
        if (webhook.readOperationsMap().size != 1) {
            throw RuntimeException("Webhook $name has more than one operation")
        }

        val tests = mutableListOf<MethodSpec>()
        val (_, operation) = webhook.readOperationsMap().entries.first()

        val springEndpoint = builders.springEndpoint
        val discriminableGroup = builders.supertype?.takeIf { it.discriminable }

        val methodName = "process" + operation.operationId.replace("/", "-").pascalCase()
        val context1 = context.withSchemaStack("#", "webhooks", name, "post")
        val methodSpecBuilder =
            MethodSpec
                .methodBuilder(methodName)
                .addAnnotation(generated(0, context1))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(
                    ParameterizedTypeName.get(ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"), TypeVariableName.get("T")),
                )
        if (springEndpoint) {
            methodSpecBuilder.addAnnotation(createPostMappingAnnotation(name))
        }

        val requestBody = operation.requestBody
        val processingContext =
            ProcessingContext(restPackage, operation, methodSpecBuilder, context1, tests, builders, name, testResourcesDir, springEndpoint)
        val bodyType = processRequestBody(requestBody, processingContext)

        // Discriminable-group per-event methods are omitted from the interface — implementers use
        // the typed abstract process<Subcategory> method and pattern-match on the sealed supertype.
        if (discriminableGroup == null) {
            val methodSpec =
                methodSpecBuilder
                    .addJavadoc($$"$L", buildJavadoc(operation).joinToString("\n"))
                    .addException(Types.EXCEPTION)
                    .build()
            builders.interfaceBuilder.addMethod(methodSpec)

            val testControllerMethod = buildTestControllerMethod(methodName, name)
            methodSpec.parameters().forEach { t ->
                testControllerMethod.addParameter(ParameterSpec.builder(t.type(), t.name()).build())
            }
            builders.testControllerBuilder.addMethod(testControllerMethod.build())
        }

        return bodyType
    }

    private fun createPostMappingAnnotation(name: String): AnnotationSpec =
        AnnotationSpec
            .builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "PostMapping"))
            .addMember("headers", $$"$S", "X-Github-Event=$name")
            .build()

    companion object {
        private val TEST_RESPONSE = ClassName.get("io.github.pulpogato.test", "TestWebhookResponse")
        private const val PACKAGE_JAVA_LANG = "java.lang"
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
            .addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_JAVA_LANG, "Override")).build())
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

        javadoc.add("@param headers The webhook headers")
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
        entry: Map.Entry<String, MediaType>,
        ctx: ProcessingContext,
    ): ClassName {
        val ref =
            entry.value.schema.`$ref`
                .replace("#/components/schemas/", "")
        val schema =
            ctx.context.openAPI.components.schemas.entries
                .first { it.key == ref }
        val type = schema.className()

        ctx.methodSpecBuilder.addParameter(
            builder(webhookHeadersType, "headers")
                .addAnnotation(generated(0, ctx.context.withSchemaStack("parameters")))
                .build(),
        )

        val bodyType = ClassName.get("${ctx.restPackage}.schemas", type)
        val bodyParam =
            ParameterSpec
                .builder(bodyType, "requestBody")
                .addAnnotation(generated(0, ctx.context.withSchemaStack("requestBody")))
        if (ctx.springEndpoint) {
            bodyParam.addAnnotation(AnnotationSpec.builder(ClassName.get(PACKAGE_SPRING_WEB_BIND_ANNOTATION, "RequestBody")).build())
        }
        ctx.methodSpecBuilder.addParameter(bodyParam.build())

        processExamples(entry, ctx.context, ctx.tests, bodyType, ctx.testResourcesDir)

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

    private fun processExamples(
        entry: Map.Entry<String, MediaType>,
        context: Context,
        tests: MutableList<MethodSpec>,
        bodyType: ClassName,
        testResourcesDir: File,
    ) {
        val openAPI = context.openAPI
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