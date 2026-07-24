package io.github.pulpogato.restcodegen

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import com.palantir.javapoet.TypeVariableName
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.nonNull
import io.github.pulpogato.restcodegen.Annotations.nullable
import io.github.pulpogato.restcodegen.Annotations.testExtension
import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.github.pulpogato.restcodegen.ext.referenceAndDefinition
import io.github.pulpogato.restcodegen.ext.unkeywordize
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import org.apache.http.HttpStatus
import java.io.File
import javax.lang.model.element.Modifier

private const val PACKAGE_SPRING_FORMAT_SUPPORT = "org.springframework.format.support"
private const val PACKAGE_SPRING_WEBCLIENT = "org.springframework.web.reactive.function.client"
private const val PACKAGE_SPRING_WEBCLIENT_ADAPTER = "org.springframework.web.reactive.function.client.support"
private const val PACKAGE_SPRING_RESTCLIENT = "org.springframework.web.client"
private const val PACKAGE_SPRING_RESTCLIENT_ADAPTER = "org.springframework.web.client.support"
private const val PACKAGE_SPRING_HTTP_CLIENT = "org.springframework.http.client"
private const val PACKAGE_SPRING_HTTP = "org.springframework.http"
private const val PACKAGE_PULPOGATO_CLIENT = "io.github.pulpogato.common.client"

/**
 * Captures the handful of ways a generated `RestClients` container class differs depending on
 * the underlying Spring HTTP client - {@code WebClient} (blocking or reactive) or the synchronous
 * {@code RestClient} - so [PathsBuilder.buildClientsContainer] can emit all three from one
 * `TypeSpec`-building function instead of duplicating it per client technology.
 */
private interface ClientContainerSpec {
    val clientClassName: ClassName
    val builderClassName: ClassName
    val adapterClassName: ClassName
    val filterInterfaceName: ClassName
    val clientFieldName: String
    val filterParamName: String
    val filterApplyMethodName: String
    val chainNoun: String
    val defaultFilters: List<Pair<ClassName, String>>
    val exampleSnippet: String
}

private object WebClientContainerSpec : ClientContainerSpec {
    override val clientClassName: ClassName = ClassName.get(PACKAGE_SPRING_WEBCLIENT, "WebClient")
    override val builderClassName: ClassName = ClassName.get(PACKAGE_SPRING_WEBCLIENT, "WebClient", "Builder")
    override val adapterClassName: ClassName = ClassName.get(PACKAGE_SPRING_WEBCLIENT_ADAPTER, "WebClientAdapter")
    override val filterInterfaceName: ClassName = ClassName.get(PACKAGE_SPRING_WEBCLIENT, "ExchangeFilterFunction")
    override val clientFieldName = "restWebClient"
    override val filterParamName = "filters"
    override val filterApplyMethodName = "filter"
    override val chainNoun = "filter"
    override val defaultFilters: List<Pair<ClassName, String>> =
        listOf(
            ClassName.get(PACKAGE_PULPOGATO_CLIENT, "RedirectExchangeFunction") to
                "follows 3xx redirects, e.g. for renamed repositories",
            ClassName.get(PACKAGE_PULPOGATO_CLIENT, "DefaultHeadersExchangeFunction") to
                "adds {@code X-GitHub-Api-Version} and {@code X-Pulpogato-Version} headers",
            ClassName.get(PACKAGE_PULPOGATO_CLIENT, "NoContentExchangeFunction") to
                "handles 204 responses",
        )
    override val exampleSnippet: String =
        """
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        RestClients clients = new RestClients(webClient);
        UsersApi users = clients.getUsersApi();
        var response = users.getAuthenticated();
        """.trimIndent()
}

private object RestClientContainerSpec : ClientContainerSpec {
    override val clientClassName: ClassName = ClassName.get(PACKAGE_SPRING_RESTCLIENT, "RestClient")
    override val builderClassName: ClassName = ClassName.get(PACKAGE_SPRING_RESTCLIENT, "RestClient", "Builder")
    override val adapterClassName: ClassName = ClassName.get(PACKAGE_SPRING_RESTCLIENT_ADAPTER, "RestClientAdapter")
    override val filterInterfaceName: ClassName = ClassName.get(PACKAGE_SPRING_HTTP_CLIENT, "ClientHttpRequestInterceptor")
    override val clientFieldName = "restClient"
    override val filterParamName = "interceptors"
    override val filterApplyMethodName = "requestInterceptor"
    override val chainNoun = "interceptor"
    override val defaultFilters: List<Pair<ClassName, String>> =
        listOf(
            ClassName.get(PACKAGE_PULPOGATO_CLIENT, "RedirectClientHttpRequestInterceptor") to
                "follows 3xx redirects, e.g. for renamed repositories",
            ClassName.get(PACKAGE_PULPOGATO_CLIENT, "DefaultHeadersClientHttpRequestInterceptor") to
                "adds {@code X-GitHub-Api-Version} and {@code X-Pulpogato-Version} headers",
            ClassName.get(PACKAGE_PULPOGATO_CLIENT, "NoContentClientHttpRequestInterceptor") to
                "handles 204 responses",
        )
    override val exampleSnippet: String =
        """
        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        RestClients clients = new RestClients(restClient);
        UsersApi users = clients.getUsersApi();
        var response = users.getAuthenticated();
        """.trimIndent()
}

/**
 * A builder class that generates REST API client code based on OpenAPI specifications.
 *
 * This class processes OpenAPI definitions to create:
 * - API interfaces with Spring Web Service annotations
 * - A RestClients class that serves as a central access point
 * - Test classes with example-based tests
 * - Enum converters for type safety
 */
class PathsBuilder {
    /**
     * Represents a single HTTP operation (method and path) from an OpenAPI specification.
     *
     * @property path The API endpoint path (e.g., "/users/{id}")
     * @property method The HTTP method (GET, POST, PUT, DELETE, etc.)
     * @property operationId The unique identifier for this operation
     * @property operation The complete OpenAPI Operation object containing all operation details
     */
    class AtomicMethod(
        val path: String,
        val method: HttpMethod,
        val operationId: String,
        val operation: Operation,
    ) {
        // GitHub operation ids are always "<group>/<operation>" (e.g. "repos/get-content"); the group
        // becomes the API interface and the operation becomes the method name.
        val group: String get() = operationId.substringBefore('/')
        val operationName: String get() = operationId.substringAfter('/')
    }

    /**
     * Generates REST API client code based on the provided OpenAPI context.
     *
     * This method creates:
     * - API interface files for each group of operations
     * - A RestClients class containing fields for each API
     * - Test classes with example-based tests for each API
     * - Enum converters for type safety
     *
     * The method processes all paths in the OpenAPI specification, groups them by operation ID,
     * and generates appropriate Java interfaces with Spring Web Service annotations.
     *
     * @param context The generation context containing the OpenAPI specification and other configuration
     * @param mainDir The directory where generated source files will be written
     * @param testDir The directory where generated test files will be written
     * @param packageName The target Java package name for generated classes
     * @param enumConvertersPackageName The target Java package name for generated enum converter classes
     * @param enumConverters A mutable set to collect enum converter class names for later processing
     * @param reactiveReturnTypes Whether generated methods should return reactive ({@code Mono}) types
     */
    fun buildApis(
        context: Context,
        mainDir: File,
        testDir: File,
        packageName: String,
        enumConvertersPackageName: String,
        enumConverters: MutableSet<ClassName>,
        reactiveReturnTypes: Boolean,
    ) {
        // Create the test resources directory for large JSON examples
        val testResourcesDir = File(testDir.parentFile, "resources")
        val apiDir = File(mainDir, packageName.replace(".", "/"))
        apiDir.mkdirs()
        val openAPI = context.openAPI

        // List to collect API field initializations (field name, type, description)
        val apiFieldInitializers = mutableListOf<Triple<String, ClassName, String?>>()

        openAPI.paths
            .flatMap { (path, pathItem) ->
                pathItem.readOperationsMap().map { (method, operation) ->
                    AtomicMethod(path, method, operation.operationId, operation)
                }
            }.groupBy { it.group }
            .toSortedMap()
            .forEach { (groupName, atomicMethods) ->
                val docTag = atomicMethods[0].operation.tags[0]
                val apiDescription = openAPI.tags.find { it.name == docTag }!!.description
                val interfaceName = groupName.pascalCase() + "Api"

                val tagIndex = openAPI.tags!!.indexOfFirst { docTag == it.name }.toString()
                val pathInterface = getPathInterface(interfaceName, context, tagIndex, apiDescription)

                val testClass = getTestClass(interfaceName)
                val typeRef = ClassName.get(packageName, interfaceName)

                atomicMethods.forEach { atomicMethod ->
                    buildMethod(
                        context.withSchemaStack("#", "paths", atomicMethod.path, atomicMethod.method.name.lowercase()),
                        atomicMethod,
                        pathInterface,
                        typeRef,
                        testClass,
                        enumConverters,
                        testResourcesDir,
                        reactiveReturnTypes,
                    )
                }

                JavaFile.builder(packageName, pathInterface.build()).build().writeTo(mainDir)
                val typeClassBuilt = testClass.build()
                if (typeClassBuilt.methodSpecs().isNotEmpty() || typeClassBuilt.typeSpecs().isNotEmpty()) {
                    JavaFile.builder(packageName, typeClassBuilt).build().writeTo(testDir)
                }

                // Store field initialization for later (will be added to the container's constructor)
                val fieldName = interfaceName.camelCase()
                apiFieldInitializers.add(Triple(fieldName, typeRef, apiDescription))
            }

        buildClientsContainer(context, packageName, enumConvertersPackageName, apiFieldInitializers, mainDir, WebClientContainerSpec)

        // The blocking API interfaces (ResponseEntity<T> returns) are adapter-agnostic, so the
        // synchronous RestClient variant reuses them as-is - only the container class differs.
        if (!reactiveReturnTypes) {
            buildClientsContainer(
                context,
                "$packageName.restclient",
                enumConvertersPackageName,
                apiFieldInitializers,
                mainDir,
                RestClientContainerSpec,
            )
        }
    }

    /**
     * Generates a `RestClients` container class exposing all the generated API interfaces,
     * wired to whichever Spring HTTP client [spec] describes ({@code WebClient} or the
     * synchronous {@code RestClient}).
     *
     * @param context The generation context, used for the {@code @Generated} annotation
     * @param packageName The target Java package name for the generated `RestClients` class
     * @param enumConvertersPackageName The target Java package name for generated enum converter classes
     * @param apiFieldInitializers The (field name, API interface type, description) triples collected
     * while building the API interfaces
     * @param mainDir The directory where generated source files will be written
     * @param spec Describes the client technology this container is built on top of
     */
    private fun buildClientsContainer(
        context: Context,
        packageName: String,
        enumConvertersPackageName: String,
        apiFieldInitializers: List<Triple<String, ClassName, String?>>,
        mainDir: File,
        spec: ClientContainerSpec,
    ) {
        val clientType = spec.clientClassName
        val fieldName = spec.clientFieldName
        val getterName = "get" + fieldName.pascalCase()

        val restClients =
            TypeSpec
                .classBuilder("RestClients")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(generated(0, context.withSchemaStack("#", "paths")))
                .addJavadoc(
                    """
                    Central entry point for accessing all GitHub REST APIs.

                    <p>Construct with a pre-configured {@link ${clientType.simpleName()}}, then call
                    {@code get<Xxx>Api()} to obtain a typed API interface. Example:

                    <pre>{@code
                    ${spec.exampleSnippet}
                    }</pre>
                    """.trimIndent(),
                ).addField(
                    FieldSpec
                        .builder(
                            ClassName.get(PACKAGE_SPRING_FORMAT_SUPPORT, "FormattingConversionService"),
                            "conversionService",
                            Modifier.PRIVATE,
                            Modifier.FINAL,
                        ).build(),
                ).addField(
                    FieldSpec
                        .builder(
                            ClassName.get("org.springframework.web.service.invoker", "HttpServiceProxyFactory"),
                            "factory",
                            Modifier.PRIVATE,
                            Modifier.FINAL,
                        ).build(),
                ).addField(
                    FieldSpec
                        .builder(clientType, fieldName, Modifier.PRIVATE, Modifier.FINAL)
                        .build(),
                ).addMethod(
                    MethodSpec
                        .methodBuilder("getConversionService")
                        .addModifiers(Modifier.PUBLIC)
                        .returns(ClassName.get(PACKAGE_SPRING_FORMAT_SUPPORT, "FormattingConversionService"))
                        .addStatement("return this.conversionService")
                        .addJavadoc("Returns the conversion service used for parameter conversion.")
                        .build(),
                ).addMethod(
                    MethodSpec
                        .methodBuilder(getterName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(clientType)
                        .addStatement($$"return this.$N", fieldName)
                        .addJavadoc($$"Returns the $L used for REST API calls.", clientType.simpleName())
                        .build(),
                ).addMethod(
                    MethodSpec
                        .methodBuilder("computeApi")
                        .addModifiers(Modifier.PRIVATE)
                        .addTypeVariable(TypeVariableName.get("T"))
                        .addParameter(
                            ParameterSpec
                                .builder(
                                    ParameterizedTypeName.get(
                                        ClassName.get(Class::class.java),
                                        TypeVariableName.get("T"),
                                    ),
                                    "clazz",
                                ).build(),
                        ).returns(TypeVariableName.get("T"))
                        .addStatement("return factory.createClient(clazz)")
                        .build(),
                )

        apiFieldInitializers.forEach { (apiFieldName, typeRef, apiDescription) ->
            restClients.addField(
                FieldSpec
                    .builder(typeRef, apiFieldName, Modifier.PRIVATE, Modifier.FINAL)
                    .addJavadoc($$"$L", apiDescription ?: "")
                    .build(),
            )

            restClients.addMethod(
                MethodSpec
                    .methodBuilder("get" + typeRef.simpleName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(typeRef)
                    .addStatement($$"return this.$N", apiFieldName)
                    .addJavadoc($$"$L", apiDescription ?: "")
                    .build(),
            )
        }

        val defaultFilterDoc =
            spec.defaultFilters.joinToString("\n") { (className, description) ->
                "  <li>{@code ${className.simpleName()}} ($description)</li>"
            }
        val defaultFilterSeeDoc = spec.defaultFilters.joinToString("\n") { (className, _) -> "@see ${className.canonicalName()}" }

        val constructorBuilder =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(
                    """
                    Constructs a client with an explicit ${spec.chainNoun} chain.

                    <p>The given {@code ${spec.filterParamName}} are applied, in order, to {@code $fieldName}.
                    Use this constructor to customize, reorder, or omit the default ${spec.chainNoun}s
                    applied by {@link #RestClients(${clientType.simpleName()})}.

                    $defaultFilterSeeDoc
                    """.trimIndent(),
                ).addParameter(
                    ParameterSpec
                        .builder(clientType, fieldName)
                        .build(),
                ).addParameter(
                    ParameterSpec
                        .builder(
                            ParameterizedTypeName.get(
                                ClassName.get("java.util", "List"),
                                spec.filterInterfaceName,
                            ),
                            spec.filterParamName,
                        ).build(),
                ).addStatement(
                    $$"$T builder = $N.mutate()",
                    spec.builderClassName,
                    fieldName,
                ).addStatement(
                    $$"$N.forEach(builder::$N)",
                    spec.filterParamName,
                    spec.filterApplyMethodName,
                ).addStatement(
                    $$"this.$N = builder.build()",
                    fieldName,
                ).addStatement(
                    $$"this.conversionService = new $T()",
                    ClassName.get(PACKAGE_SPRING_FORMAT_SUPPORT, "DefaultFormattingConversionService"),
                ).addStatement(
                    $$"new $T().getConverters().forEach(conversionService::addConverter)",
                    ClassName.get(enumConvertersPackageName, "EnumConverters"),
                ).addStatement(
                    $$"conversionService.addConverter(new $T())",
                    ClassName.get("io.github.pulpogato.common", "StringOrInteger", "StringConverter"),
                ).addStatement(
                    $$"""
                    this.factory = $T.builderFor($T.create(this.$N))
                            .conversionService(this.conversionService)
                            .build()
                    """.trimIndent(),
                    ClassName.get("org.springframework.web.service.invoker", "HttpServiceProxyFactory"),
                    spec.adapterClassName,
                    fieldName,
                )

        apiFieldInitializers.forEach { (apiFieldName, typeRef, _) ->
            constructorBuilder.addStatement($$"this.$N = computeApi($T.class)", apiFieldName, typeRef)
        }

        restClients.addMethod(constructorBuilder.build())

        restClients.addMethod(
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(
                    ParameterSpec
                        .builder(clientType, fieldName)
                        .build(),
                ).addJavadoc(
                    """
                    Constructs a client with the default ${spec.chainNoun} chain:
                    <ul>
                    $defaultFilterDoc
                    </ul>

                    @see #RestClients(${clientType.simpleName()}, List)
                    """.trimIndent(),
                ).addStatement(
                    $$"this($N, $T.of(new $T(), new $T(), new $T()))",
                    fieldName,
                    ClassName.get("java.util", "List"),
                    spec.defaultFilters[0].first,
                    spec.defaultFilters[1].first,
                    spec.defaultFilters[2].first,
                ).build(),
        )

        JavaFile.builder(packageName, restClients.build()).build().writeTo(mainDir)
    }

    /**
     * Creates a test class builder for the given interface name.
     *
     * The generated test class is annotated with the test extension annotation
     * to provide necessary test utilities and integrations.
     *
     * @param interfaceName The name of the API interface for which to create a test class
     * @return A TypeSpec.Builder configured for the test class
     */
    private fun getTestClass(interfaceName: String): TypeSpec.Builder =
        TypeSpec
            .classBuilder(interfaceName + "Test")
            .addAnnotation(testExtension())

    /**
     * Creates an API interface builder with appropriate annotations and documentation.
     *
     * The generated interface is annotated as generated code and includes the API description
     * from the OpenAPI specification as Javadoc. It uses the tag index to track the schema location.
     *
     * @param interfaceName The name for the API interface
     * @param context The generation context for tracking schema locations
     * @param tagIndex The index of the tag in the OpenAPI specification
     * @param apiDescription The description of the API from the OpenAPI specification
     * @return A TypeSpec.Builder configured for the API interface
     */
    private fun getPathInterface(
        interfaceName: String,
        context: Context,
        tagIndex: String,
        apiDescription: String?,
    ): TypeSpec.Builder =
        TypeSpec
            .interfaceBuilder(interfaceName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(generated(0, context.withSchemaStack("#", "tags", tagIndex)))
            .addJavadoc(
                buildString {
                    append(apiDescription ?: "API interface.")
                    append("\n<p>Obtain an instance via {@code RestClients.get$interfaceName()}.")
                }.trimIndent(),
            )

    /**
     * Builds a method specification for a REST API operation.
     *
     * This method processes an atomic method from the OpenAPI specification and generates
     * the appropriate method in the API interface, including parameters, return types,
     * and Javadoc documentation. It also creates corresponding test methods if examples
     * are provided in the OpenAPI specification.
     *
     * @param context The generation context containing schema information
     * @param atomicMethod The atomic method representing the API operation
     * @param typeDef The type specification builder for the API interface
     * @param typeRef The class name reference for the API interface
     * @param testClass The test class builder to which test methods will be added
     * @param enumConverters A mutable set to collect enum converter class names
     * @param testResourcesDir The directory where test resource files will be stored
     * @param reactiveReturnTypes Whether the generated method should return a reactive ({@code Mono}) type
     */
    private fun buildMethod(
        context: Context,
        atomicMethod: AtomicMethod,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        testClass: TypeSpec.Builder,
        enumConverters: MutableSet<ClassName>,
        testResourcesDir: File,
        reactiveReturnTypes: Boolean,
    ) {
        val parameters = getParameters(context, atomicMethod, typeDef, typeRef, testClass, enumConverters, testResourcesDir)

        val successResponses =
            atomicMethod.operation.responses
                .filter { (responseCode, apiResponse) ->
                    !apiResponse.content.isNullOrEmpty() && responseCode.startsWith("2")
                }.toMutableMap()
        val hasNoContent204Success =
            atomicMethod.operation.responses.any { (responseCode, apiResponse) ->
                responseCode == HttpStatus.SC_NO_CONTENT.toString() && apiResponse.content.isNullOrEmpty()
            }

        if (successResponses.size > 1 && successResponses.containsKey(HttpStatus.SC_NO_CONTENT.toString())) {
            successResponses.remove(HttpStatus.SC_NO_CONTENT.toString())
        }

        val javadoc =
            buildMethodJavadoc(atomicMethod, parameters.map { it.first })
                .split("\n")
                .dropLastWhile { it.isEmpty() }
                .joinToString("\n")

        val successResponse = successResponses.entries.minByOrNull { it.key }

        if (successResponse == null || successResponse.value.content == null) {
            typeDef.addMethod(buildVoidMethod(context, atomicMethod, javadoc, parameters, reactiveReturnTypes))
        } else {
            successResponse.value.content.forEach { (contentType, details) ->
                val rad =
                    referenceAndDefinition(
                        context.withSchemaStack("responses", successResponse.key, "content", contentType, "schema"),
                        mapOf("${atomicMethod.operationName.pascalCase()}${successResponse.key}" to details.schema).entries.first(),
                        "",
                        typeRef,
                    )
                rad!!.let { r ->
                    r.second?.let {
                        typeDef.addType(it.toBuilder().addModifiers(Modifier.STATIC, Modifier.PUBLIC).build())
                    }
                    val respRef = r.first
                    val methodName =
                        when (successResponse.value.content.size) {
                            1 -> atomicMethod.operationName.camelCase()
                            else -> atomicMethod.operationName.camelCase() + suffixContentType(contentType)
                        }
                    val testMethods = getTestMethods(context, successResponse, contentType, details, respRef, testResourcesDir)

                    val parameterSpecs = parameters.map { it.second }
                    if (contentType.contains("json") && testMethods.isNotEmpty()) {
                        testClass.addType(
                            TypeSpec
                                .classBuilder(methodName.pascalCase() + "Response")
                                .addAnnotation(generated(0, context))
                                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                                .addMethods(testMethods)
                                .addJavadoc($$"$L", "Tests {@link $typeRef#$methodName}")
                                .build(),
                        )
                    }
                    typeDef.addMethod(
                        buildNonVoidMethod(
                            context,
                            methodName,
                            javadoc,
                            atomicMethod,
                            contentType,
                            parameterSpecs,
                            respRef,
                            reactiveReturnTypes,
                            responseBodyNullable = hasNoContent204Success,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Generates test methods based on examples provided in the OpenAPI specification.
     *
     * This method creates test methods for response examples, handling both inline examples
     * and references to components examples. The generated tests validate that example data
     * matches the expected response type.
     *
     * @param context The generation context containing schema information
     * @param successResponse The success response entry from the operation
     * @param contentType The content type of the response
     * @param details The media type details containing the response schema and examples
     * @param respRef The type name reference for the response type
     * @param testResourcesDir The directory where test resource files will be stored
     * @return A list of MethodSpec objects representing the generated test methods
     */
    private fun getTestMethods(
        context: Context,
        successResponse: MutableMap.MutableEntry<String, ApiResponse>,
        contentType: String,
        details: MediaType,
        respRef: TypeName,
        testResourcesDir: File,
    ): List<MethodSpec> {
        val examples =
            when {
                contentType.contains("json") -> details.examples ?: emptyMap()
                else -> emptyMap()
            }
        return examples
            .mapNotNull { (k, v) ->
                // Resolve example value and build a proper schema ref, handling $ref if present
                val (exampleValue, schemaRefPath) =
                    when {
                        v.value != null -> {
                            Pair(
                                v.value,
                                context.withSchemaStack("responses", successResponse.key, "content", contentType, "examples", k, "value"),
                            )
                        }

                        v.`$ref` != null -> {
                            val refName = v.`$ref`.replace("#/components/examples/", "")
                            val resolvedExample =
                                context.openAPI.components.examples
                                    ?.get(refName)
                            Pair(
                                resolvedExample?.value,
                                context.withSchemaStack("#", "components", "examples", refName, "value"),
                            )
                        }

                        else -> {
                            Pair(null, context)
                        }
                    }

                if (exampleValue != null) {
                    TestBuilder.buildTest(
                        schemaRefPath,
                        "${successResponse.key}$k",
                        exampleValue,
                        respRef,
                        testResourcesDir,
                    )
                } else {
                    null
                }
            }
    }

    /**
     * Builds a method specification for operations that return a response body.
     *
     * This method creates Spring Web Service annotations with the appropriate HTTP method
     * and content type. The generated method returns a ResponseEntity containing the
     * response type, with proper nullability annotations.
     *
     * @param context The generation context for tracking schema locations
     * @param methodName The name of the method to be generated
     * @param javadoc The Javadoc documentation for the method
     * @param atomicMethod The atomic method representing the API operation
     * @param contentType The content type of the response
     * @param parameterSpecs The list of parameter specifications for the method
     * @param respRef The type name reference for the response type
     * @param reactiveReturnTypes Whether the generated method should return a reactive ({@code Mono}) type
     * @param responseBodyNullable Whether the response body should be annotated as nullable (e.g. when a 204 response is also possible)
     * @return A MethodSpec object representing the generated method
     */
    private fun buildNonVoidMethod(
        context: Context,
        methodName: String,
        javadoc: String,
        atomicMethod: AtomicMethod,
        contentType: String,
        parameterSpecs: List<ParameterSpec>,
        respRef: TypeName,
        reactiveReturnTypes: Boolean,
        responseBodyNullable: Boolean = false,
    ): MethodSpec {
        val suitableAnnotations =
            respRef.annotations().filter {
                (it.type() as ClassName).simpleName() != "JsonFormat"
            }
        val exchangeAnnotation =
            atomicMethod.method.name
                .lowercase()
                .pascalCase() + "Exchange"
        val bodyType = respRef.withoutAnnotations().annotated(suitableAnnotations)
        val nullableBodyType = if (responseBodyNullable) bodyType.annotated(nullable()) else bodyType
        return MethodSpec
            .methodBuilder(methodName)
            .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
            .addJavadoc($$"$L", javadoc)
            .addAnnotation(
                AnnotationSpec
                    .builder(
                        ClassName.get("org.springframework.web.service.annotation", exchangeAnnotation),
                    ).addMember("value", $$"$S", atomicMethod.path)
                    .addMember("accept", $$"$S", contentType)
                    .build(),
            ).addAnnotation(generated(0, context))
            .addParameters(parameterSpecs)
            .returns(
                if (reactiveReturnTypes) {
                    ParameterizedTypeName.get(
                        ClassName.get("reactor.core.publisher", "Mono"),
                        ParameterizedTypeName
                            .get(
                                ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                                nullableBodyType,
                            ).annotated(nonNull()),
                    )
                } else {
                    ParameterizedTypeName.get(
                        ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                        nullableBodyType,
                    )
                },
            ).build()
    }

    /**
     * Builds a method specification for operations that don't return a response body.
     *
     * This method creates Spring Web Service annotations for void operations, typically
     * for operations that return a 204 No Content response. The method returns a
     * ResponseEntity of Void type.
     *
     * @param context The generation context for tracking schema locations
     * @param atomicMethod The atomic method representing the API operation
     * @param javadoc The Javadoc documentation for the method
     * @param parameters The list of parameter pairs (OpenAPI Parameter and ParameterSpec)
     * @param reactiveReturnTypes Whether the generated method should return a reactive ({@code Mono}) type
     * @return A MethodSpec object representing the generated void method
     */
    private fun buildVoidMethod(
        context: Context,
        atomicMethod: AtomicMethod,
        javadoc: String,
        parameters: List<Pair<Parameter, ParameterSpec>>,
        reactiveReturnTypes: Boolean,
    ): MethodSpec =
        MethodSpec
            .methodBuilder(atomicMethod.operationName.camelCase())
            .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
            .addJavadoc($$"$L", javadoc)
            .addAnnotation(
                AnnotationSpec
                    .builder(
                        ClassName.get(
                            "org.springframework.web.service.annotation",
                            atomicMethod.method.name
                                .lowercase()
                                .pascalCase() + "Exchange",
                        ),
                    ).addMember("value", $$"$S", atomicMethod.path)
                    .build(),
            ).addAnnotation(generated(0, context))
            .addParameters(parameters.map { it.second })
            .returns(
                if (reactiveReturnTypes) {
                    ParameterizedTypeName.get(
                        ClassName.get("reactor.core.publisher", "Mono"),
                        ParameterizedTypeName
                            .get(
                                ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                                ClassName.get("java.lang", "Void"),
                            ).annotated(nonNull()),
                    )
                } else {
                    ParameterizedTypeName.get(
                        ClassName.get(PACKAGE_SPRING_HTTP, "ResponseEntity"),
                        ClassName.get("java.lang", "Void"),
                    )
                },
            ).build()

    /**
     * Maps content types to appropriate suffixes for method names.
     *
     * This method provides a way to distinguish between different content types
     * by adding appropriate suffixes to method names when multiple content types
     * are available for the same operation. Standard JSON gets no suffix,
     * while other specific content types get descriptive suffixes.
     *
     * @param key The content type string to map to a suffix
     * @return A suffix string to append to method names, or empty string for standard JSON
     * @throws IllegalArgumentException if an unknown content type is provided
     */
    private fun suffixContentType(key: String) =
        when (key) {
            "application/json" -> ""
            "application/vnd.github.v3.star+json" -> "Star"
            "application/vnd.github.object" -> "Object"
            "application/vnd.github.raw+json" -> "Raw"
            "application/vnd.github.html+json" -> "Html"
            "application/json+sarif", "application/sarif+json" -> "Sarif"
            "application/vnd.github.diff" -> "Diff"
            "application/vnd.github.patch" -> "Patch"
            "application/vnd.github.sha" -> "Sha"
            else -> throw IllegalArgumentException("Unknown content type: $key")
        }

    /**
     * Builds Javadoc documentation for a method based on the OpenAPI operation.
     *
     * This method combines the operation's summary and description, adds parameter
     * documentation for each method parameter, and includes links to external documentation
     * if provided in the OpenAPI specification.
     *
     * @param atomicMethod The atomic method representing the API operation
     * @param parameters The list of parameters for the method
     * @return A formatted Javadoc string containing the documentation
     */
    private fun buildMethodJavadoc(
        atomicMethod: AtomicMethod,
        parameters: List<Parameter>,
    ): String {
        val javadoc = StringBuilder()
        val summary = atomicMethod.operation.summary ?: ""
        val description = atomicMethod.operation.description ?: ""
        if (description.contains(summary)) {
            javadoc.append(MarkdownHelper.mdToHtml(description))
        } else {
            javadoc.append(MarkdownHelper.mdToHtml("**$summary**\n\n$description"))
        }
        javadoc.append("\n\n")
        parameters.forEach { theParameter ->
            val paramName = theParameter.name.unkeywordize().camelCase()
            val mdToHtml = MarkdownHelper.mdToHtml(theParameter.description)
            javadoc.append("@param $paramName $mdToHtml")
            if (!mdToHtml.endsWith("\n")) {
                javadoc.append("\n")
            }
        }
        if (parameters.isNotEmpty()) {
            javadoc.append("\n")
        }
        if (atomicMethod.operation.externalDocs != null) {
            javadoc.append(
                "@see <a href=\"${atomicMethod.operation.externalDocs.url}\">${atomicMethod.operation.externalDocs.description ?: "External Docs"}</a>\n",
            )
        }
        return javadoc.toString()
    }

    /**
     * Builds a parameter specification for a method based on an OpenAPI parameter.
     *
     * This method processes OpenAPI parameter definitions and creates appropriate Java
     * parameter specifications with Spring Web annotations (RequestParam, RequestBody,
     * PathVariable, RequestHeader). It also handles schema definitions and enum converters.
     *
     * @param context The generation context for tracking schema locations
     * @param theParameter The OpenAPI Parameter object to convert
     * @param atomicMethod The atomic method representing the API operation
     * @param typeDef The type specification builder for the API interface
     * @param typeRef The class name reference for the API interface
     * @param enumConverters A mutable set to collect enum converter class names
     * @return A ParameterSpec object representing the generated parameter
     */
    private fun buildParameter(
        context: Context,
        theParameter: Parameter,
        atomicMethod: AtomicMethod,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        enumConverters: MutableSet<ClassName>,
    ): ParameterSpec {
        val operationName = atomicMethod.operationName
        val (ref, def) =
            referenceAndDefinition(context, mapOf(theParameter.name to theParameter.schema).entries.first(), operationName.pascalCase(), typeRef)!!

        if (def != null) {
            val matchingType = typeDef.build().typeSpecs().find { k -> k.name() == def.name() }
            if (matchingType == null) {
                val builtDef = def.toBuilder().addModifiers(Modifier.STATIC, Modifier.PUBLIC).build()
                typeDef.addType(builtDef)

                // If this is an enum, add its converter to the set
                if (builtDef.enumConstants().isNotEmpty() && ref is ClassName) {
                    val converterClassName = ref.nestedClass("${ref.simpleName()}Converter")
                    enumConverters.add(converterClassName)
                }
            }
        }

        val paramName = theParameter.name.unkeywordize().camelCase()

        return ParameterSpec
            .builder(ref, paramName)
            .addAnnotations(
                when (theParameter.`in`) {
                    "query" -> {
                        mutableListOf(
                            AnnotationSpec
                                .builder(webBind("RequestParam"))
                                .addMember("value", $$"$S", theParameter.name)
                                .addMember("required", $$"$L", theParameter.required)
                                .build(),
                        ).apply {
                            if (!theParameter.required) add(nullable())
                        }
                    }

                    "body" -> {
                        mutableListOf(
                            AnnotationSpec.builder(webBind("RequestBody")).build(),
                        )
                    }

                    "path" -> {
                        mutableListOf(
                            AnnotationSpec
                                .builder(webBind("PathVariable"))
                                .addMember("value", $$"$S", theParameter.name)
                                .build(),
                        )
                    }

                    "header" -> {
                        mutableListOf(
                            AnnotationSpec.builder(webBind("RequestHeader")).build(),
                        )
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown parameter type: ${theParameter.`in`}")
                    }
                },
            ).build()
    }

    /**
     * Creates a ClassName for Spring Web binding annotations.
     *
     * This utility method provides a convenient way to reference Spring's web binding
     * annotations like RequestParam, RequestBody, PathVariable, and RequestHeader
     * by constructing the appropriate ClassName object.
     *
     * @param simpleName The simple name of the Spring web binding annotation
     * @return A ClassName object representing the fully qualified annotation
     */
    private fun webBind(simpleName: String): ClassName = ClassName.get("org.springframework.web.bind.annotation", simpleName)

    /**
     * Builds test methods for request body parameters based on examples.
     *
     * This method generates test methods that validate request body examples against
     * the expected parameter type. It creates nested test classes containing tests
     * for each example provided in the OpenAPI specification.
     *
     * @param context The generation context for tracking schema locations
     * @param theParameter The OpenAPI Parameter object for the request body
     * @param ref The type name reference for the parameter type
     * @param atomicMethod The atomic method representing the API operation
     * @param testClass The test class builder to which test methods will be added
     * @param typeRef The class name reference for the API interface
     * @param testResourcesDir The directory where test resource files will be stored
     */
    private fun buildBodyTestCode(
        context: Context,
        theParameter: Parameter,
        ref: TypeName,
        atomicMethod: AtomicMethod,
        testClass: TypeSpec.Builder,
        typeRef: ClassName,
        testResourcesDir: File,
    ) {
        val operationName = atomicMethod.operationName
        val paramName = theParameter.name.unkeywordize().camelCase()
        val testMethods =
            (theParameter.examples ?: emptyMap())
                .filter { (_, v) -> v.value != null && !v.value.toString().startsWith("@") }
                .mapNotNull { (k, v) ->
                    atomicMethod.operation.requestBody.content
                        .filterKeys { contentType -> contentType.lowercase().contains("json") }
                        .map { (contentType, _) ->
                            TestBuilder.buildTest(
                                context.withSchemaStack(
                                    "requestBody",
                                    "content",
                                    contentType,
                                    "examples",
                                    k,
                                    "value",
                                ),
                                "${paramName}_$k",
                                v.value,
                                ref,
                                testResourcesDir,
                            )
                        }
                }.flatten()
        if (testMethods.isNotEmpty()) {
            testClass.addType(
                TypeSpec
                    .classBuilder("${operationName}RequestBody".pascalCase())
                    .addJavadoc($$"$L", "Tests {@link $typeRef#${atomicMethod.operationName.camelCase()}}")
                    .addAnnotation(generated(0, context))
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                    .addMethods(testMethods)
                    .build(),
            )
        }
    }

    /**
     * Extracts and processes all parameters for an API operation.
     *
     * This method handles both regular parameters and request body parameters from the
     * OpenAPI specification. It resolves references to component parameters and creates
     * appropriate parameter specifications with type definitions and enum converters.
     * It also generates test code for body parameters.
     *
     * @param context The generation context for tracking schema locations
     * @param atomicMethod The atomic method representing the API operation
     * @param typeDef The type specification builder for the API interface
     * @param typeRef The class name reference for the API interface
     * @param testClass The test class builder to which test methods will be added
     * @param enumConverters A mutable set to collect enum converter class names
     * @param testResourcesDir The directory where test resource files will be stored
     * @return A list of parameter pairs containing the OpenAPI Parameter and generated ParameterSpec
     */
    private fun getParameters(
        context: Context,
        atomicMethod: AtomicMethod,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        testClass: TypeSpec.Builder,
        enumConverters: MutableSet<ClassName>,
        testResourcesDir: File,
    ): List<Pair<Parameter, ParameterSpec>> {
        val openAPI = context.openAPI
        val parameters =
            atomicMethod.operation.parameters
                ?.mapIndexed { idx, parameter ->
                    if (parameter.`$ref` == null) {
                        Pair(
                            parameter,
                            buildParameter(
                                context.withSchemaStack("parameters", idx.toString(), "schema"),
                                parameter,
                                atomicMethod,
                                typeDef,
                                typeRef,
                                enumConverters,
                            ),
                        )
                    } else {
                        val refName = parameter.`$ref`.replace("#/components/parameters/", "")
                        val newParameter = openAPI.components.parameters[refName]!!
                        Pair(
                            newParameter,
                            buildParameter(
                                context.withSchemaStack("#", "components", "parameters", refName, "schema"),
                                newParameter,
                                atomicMethod,
                                typeDef,
                                typeRef,
                                enumConverters,
                            ),
                        )
                    }
                }?.toMutableList() ?: mutableListOf()

        if (atomicMethod.operation.requestBody != null) {
            val requestBody = atomicMethod.operation.requestBody
            val firstEntry = requestBody.content.firstEntry()
            val (_, def) =
                referenceAndDefinition(
                    context.withSchemaStack("requestBody", "content", firstEntry.key, "schema"),
                    mapOf("requestBody" to firstEntry.value.schema).entries.first(),
                    atomicMethod.operationName.pascalCase(),
                    typeRef,
                )!!
            def?.let {
                val matchingType = typeDef.build().typeSpecs().find { k -> k.name() == it.name() }
                if (matchingType == null) {
                    typeDef.addType(it.toBuilder().addModifiers(Modifier.STATIC, Modifier.PUBLIC).build())
                }
            }
            val bodyParameter =
                Parameter()
                    .`in`("body")
                    .name("requestBody")
                    .description(requestBody.description ?: "The request body")
                    .schema(firstEntry.value.schema)
                    .examples(firstEntry.value.examples)
                    .required(true)
            parameters.add(
                Pair(
                    bodyParameter,
                    buildParameter(
                        context.withSchemaStack("requestBody", "content", firstEntry.key, "schema"),
                        bodyParameter,
                        atomicMethod,
                        typeDef,
                        typeRef,
                        enumConverters,
                    ),
                ),
            )
        }

        // Generate test code for body parameters separately
        generateParameterTestCode(context, parameters, atomicMethod, testClass, typeRef, testResourcesDir)

        return parameters.toList()
    }

    /**
     * Generates test code for body parameters in API operations.
     *
     * This method iterates through the parameters and specifically handles body parameters
     * by generating test methods based on examples provided in the OpenAPI specification.
     * These tests validate that example request bodies match the expected parameter types.
     *
     * @param context The generation context for tracking schema locations
     * @param parameters The list of parameter pairs to process
     * @param atomicMethod The atomic method representing the API operation
     * @param testClass The test class builder to which test methods will be added
     * @param typeRef The class name reference for the API interface
     * @param testResourcesDir The directory where test resource files will be stored
     */
    private fun generateParameterTestCode(
        context: Context,
        parameters: List<Pair<Parameter, ParameterSpec>>,
        atomicMethod: AtomicMethod,
        testClass: TypeSpec.Builder,
        typeRef: ClassName,
        testResourcesDir: File,
    ) {
        parameters.forEach { (parameter, _) ->
            if (parameter.`in` == "body") {
                val operationName = atomicMethod.operationName
                val (ref, _) = referenceAndDefinition(context, mapOf(parameter.name to parameter.schema).entries.first(), operationName.pascalCase(), typeRef)!!
                buildBodyTestCode(context, parameter, ref, atomicMethod, testClass, typeRef, testResourcesDir)
            }
        }
    }
}