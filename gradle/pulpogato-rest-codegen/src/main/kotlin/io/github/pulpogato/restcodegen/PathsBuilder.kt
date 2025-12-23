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
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import java.io.File
import javax.lang.model.element.Modifier

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
     * Represents a single HTTP operation (method + path) from an OpenAPI specification.
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
    )

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
     * @param enumConverters A mutable set to collect enum converter class names for later processing
     */
    fun buildApis(
        context: Context,
        mainDir: File,
        testDir: File,
        packageName: String,
        enumConverters: MutableSet<ClassName>,
    ) {
        // Create test resources directory for large JSON examples
        val testResourcesDir = File(testDir.parentFile, "resources")
        val apiDir = File(mainDir, packageName.replace(".", "/"))
        apiDir.mkdirs()
        val openAPI = context.openAPI

        val restClients =
            TypeSpec
                .classBuilder("RestClients")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(generated(0, context.withSchemaStack("#", "paths")))
                .addJavadoc($$"$L", "Client to access all REST APIs.")
                .addField(
                    FieldSpec
                        .builder(
                            ClassName.get("org.springframework.format.support", "FormattingConversionService"),
                            "conversionService",
                            Modifier.PRIVATE,
                            Modifier.FINAL,
                        ).addAnnotation(nonNull())
                        .build(),
                ).addField(
                    FieldSpec
                        .builder(
                            ClassName.get("org.springframework.web.service.invoker", "HttpServiceProxyFactory"),
                            "factory",
                            Modifier.PRIVATE,
                            Modifier.FINAL,
                        ).addAnnotation(nonNull())
                        .build(),
                ).addMethod(
                    MethodSpec
                        .methodBuilder("getConversionService")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(nonNull())
                        .returns(ClassName.get("org.springframework.format.support", "FormattingConversionService"))
                        .addStatement("return this.conversionService")
                        .addJavadoc("Returns the conversion service used for parameter conversion.")
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

        // List to collect API field initializations
        val apiFieldInitializers = mutableListOf<Pair<String, ClassName>>()

        openAPI.paths
            .flatMap { (path, pathItem) ->
                pathItem.readOperationsMap().map { (method, operation) ->
                    AtomicMethod(path, method, operation.operationId, operation)
                }
            }.groupBy { it.operationId.split('/')[0] }
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
                        openAPI,
                        pathInterface,
                        typeRef,
                        testClass,
                        enumConverters,
                        testResourcesDir,
                    )
                }

                JavaFile.builder(packageName, pathInterface.build()).build().writeTo(mainDir)
                val typeClassBuilt = testClass.build()
                if (typeClassBuilt.methodSpecs().isNotEmpty() || typeClassBuilt.typeSpecs().isNotEmpty()) {
                    JavaFile.builder(packageName, typeClassBuilt).build().writeTo(testDir)
                }

                // Add field without initializer (will be initialized in constructor)
                val fieldName = interfaceName.camelCase()
                val apiField =
                    FieldSpec
                        .builder(typeRef, fieldName, Modifier.PRIVATE, Modifier.FINAL)
                        .addJavadoc($$"$L", apiDescription ?: "")
                        .build()
                restClients.addField(apiField)

                // Store field initialization for later (will be added to constructor)
                apiFieldInitializers.add(Pair(fieldName, typeRef))

                // Add explicit getter
                val getterName = "get" + interfaceName.pascalCase()
                restClients.addMethod(
                    MethodSpec
                        .methodBuilder(getterName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(typeRef)
                        .addStatement($$"return this.$N", fieldName)
                        .addJavadoc($$"$L", apiDescription ?: "")
                        .build(),
                )
            }

        // Add constructor that initializes all API fields
        val constructorBuilder =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(
                    ParameterSpec
                        .builder(
                            ClassName.get("org.springframework.web.reactive.function.client", "WebClient"),
                            "restClient",
                        ).addAnnotation(nonNull())
                        .build(),
                ).addStatement(
                    $$"this.conversionService = new $T()",
                    ClassName.get("org.springframework.format.support", "DefaultFormattingConversionService"),
                ).addStatement(
                    $$"new $T().getConverters().forEach(conversionService::addConverter)",
                    ClassName.get(packageName, "EnumConverters"),
                ).addStatement(
                    $$"conversionService.addConverter(new $T())",
                    ClassName.get("io.github.pulpogato.common", "StringOrInteger", "StringConverter"),
                ).addStatement(
                    $$"""
                    this.factory = $T.builderFor(
                            $T.create(
                                    $T.requireNonNull(restClient)))
                            .conversionService(this.conversionService)
                            .build()
                    """.trimIndent(),
                    ClassName.get("org.springframework.web.service.invoker", "HttpServiceProxyFactory"),
                    ClassName.get("org.springframework.web.reactive.function.client.support", "WebClientAdapter"),
                    ClassName.get("java.util", "Objects"),
                )

        // Initialize all API fields in constructor
        apiFieldInitializers.forEach { (fieldName, typeRef) ->
            constructorBuilder.addStatement($$"this.$N = computeApi($T.class)", fieldName, typeRef)
        }

        restClients.addMethod(constructorBuilder.build())

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
            .addJavadoc($$"$L", apiDescription ?: "")

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
     * @param openAPI The complete OpenAPI specification
     * @param typeDef The type specification builder for the API interface
     * @param typeRef The class name reference for the API interface
     * @param testClass The test class builder to which test methods will be added
     * @param enumConverters A mutable set to collect enum converter class names
     * @param testResourcesDir The directory where test resource files will be stored
     */
    private fun buildMethod(
        context: Context,
        atomicMethod: AtomicMethod,
        openAPI: OpenAPI,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        testClass: TypeSpec.Builder,
        enumConverters: MutableSet<ClassName>,
        testResourcesDir: File,
    ) {
        val parameters = getParameters(context, atomicMethod, openAPI, typeDef, typeRef, testClass, enumConverters, testResourcesDir)

        val successResponses =
            atomicMethod.operation.responses
                .filter { (responseCode, apiResponse) ->
                    !apiResponse.content.isNullOrEmpty() && responseCode.startsWith("2")
                }.toMutableMap()

        if (successResponses.size > 1 && successResponses.containsKey("204")) {
            successResponses.remove("204")
        }

        val javadoc =
            buildMethodJavadoc(atomicMethod, parameters.map { it.first })
                .split("\n")
                .dropLastWhile { it.isEmpty() }
                .joinToString("\n")

        val successResponse = successResponses.entries.minByOrNull { it.key }

        if (successResponse == null || successResponse.value.content == null) {
            typeDef.addMethod(buildVoidMethod(context, atomicMethod, javadoc, parameters))
        } else {
            successResponse.value.content.forEach { (contentType, details) ->
                val rad =
                    referenceAndDefinition(
                        context.withSchemaStack("responses", successResponse.key, "content", contentType, "schema"),
                        mapOf("${atomicMethod.operationId.split('/')[1].pascalCase()}${successResponse.key}" to details.schema).entries.first(),
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
                            1 -> atomicMethod.operationId.split('/')[1].camelCase()
                            else -> atomicMethod.operationId.split('/')[1].camelCase() + suffixContentType(contentType)
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
                    typeDef.addMethod(buildNonVoidMethod(context, methodName, javadoc, atomicMethod, contentType, parameterSpecs, respRef))
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
                // Resolve example value and build proper schema ref, handling $ref if present
                val (exampleValue, schemaRefPath) =
                    when {
                        v.value != null ->
                            Pair(
                                v.value,
                                context.withSchemaStack("responses", successResponse.key, "content", contentType, "examples", k, "value"),
                            )
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
                        else -> Pair(null, context)
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
    ): MethodSpec {
        val suitableAnnotations =
            respRef.annotations().filter {
                (it.type() as ClassName).simpleName() != "JsonFormat"
            }
        val exchangeAnnotation =
            atomicMethod.method.name
                .lowercase()
                .pascalCase() + "Exchange"
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
            .addAnnotation(nonNull())
            .addParameters(parameterSpecs)
            .returns(
                ParameterizedTypeName.get(
                    ClassName.get("org.springframework.http", "ResponseEntity"),
                    respRef.withoutAnnotations().annotated(suitableAnnotations).annotated(nonNull()),
                ),
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
     * @return A MethodSpec object representing the generated void method
     */
    private fun buildVoidMethod(
        context: Context,
        atomicMethod: AtomicMethod,
        javadoc: String,
        parameters: List<Pair<Parameter, ParameterSpec>>,
    ): MethodSpec =
        MethodSpec
            .methodBuilder(atomicMethod.operationId.split('/')[1].camelCase())
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
                ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"), ClassName.get("java.lang", "Void").annotated(nonNull())),
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
            "application/json+sarif" -> "Sarif"
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
        val operationName = atomicMethod.operationId.split('/')[1]
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
                    "query" ->
                        mutableListOf(
                            AnnotationSpec
                                .builder(webBind("RequestParam"))
                                .addMember("value", $$"$S", theParameter.name)
                                .addMember("required", $$"$L", theParameter.required)
                                .build(),
                            if (theParameter.required) nonNull() else nullable(),
                        )

                    "body" ->
                        mutableListOf(
                            AnnotationSpec.builder(webBind("RequestBody")).build(),
                            nonNull(),
                        )

                    "path" ->
                        mutableListOf(
                            AnnotationSpec
                                .builder(webBind("PathVariable"))
                                .addMember("value", $$"$S", theParameter.name)
                                .build(),
                            nonNull(),
                        )

                    "header" ->
                        mutableListOf(
                            AnnotationSpec.builder(webBind("RequestHeader")).build(),
                            nonNull(),
                        )

                    else -> throw IllegalArgumentException("Unknown parameter type: ${theParameter.`in`}")
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
     * @param operationName The name of the operation
     * @param typeRef The class name reference for the API interface
     * @param testResourcesDir The directory where test resource files will be stored
     */
    private fun buildBodyTestCode(
        context: Context,
        theParameter: Parameter,
        ref: TypeName,
        atomicMethod: AtomicMethod,
        testClass: TypeSpec.Builder,
        operationName: String,
        typeRef: ClassName,
        testResourcesDir: File,
    ) {
        val paramName = theParameter.name.unkeywordize().camelCase()
        val testMethods =
            (theParameter.examples ?: emptyMap())
                .filter { (_, v) -> v.value != null && !v.value.toString().startsWith("@") }
                .mapNotNull { (k, v) ->
                    atomicMethod.operation.requestBody.content
                        .filterKeys { contentType -> contentType.lowercase().contains("json") }
                        .map { (_, _) ->
                            TestBuilder.buildTest(
                                context.withSchemaStack(
                                    "requestBody",
                                    "content",
                                    atomicMethod.operation.requestBody.content
                                        .firstEntry()
                                        .key,
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
                    .addJavadoc($$"$L", "Tests {@link $typeRef#${atomicMethod.operationId.split('/')[1].camelCase()}}")
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
     * @param openAPI The complete OpenAPI specification
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
        openAPI: OpenAPI,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        testClass: TypeSpec.Builder,
        enumConverters: MutableSet<ClassName>,
        testResourcesDir: File,
    ): List<Pair<Parameter, ParameterSpec>> {
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
                    atomicMethod.operationId.split('/')[1].pascalCase(),
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
                val operationName = atomicMethod.operationId.split('/')[1]
                val (ref, _) = referenceAndDefinition(context, mapOf(parameter.name to parameter.schema).entries.first(), operationName.pascalCase(), typeRef)!!
                buildBodyTestCode(context, parameter, ref, atomicMethod, testClass, operationName, typeRef, testResourcesDir)
            }
        }
    }
}