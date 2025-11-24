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
import io.github.pulpogato.restcodegen.Annotations.lombok
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

class PathsBuilder {
    class AtomicMethod(
        val path: String,
        val method: HttpMethod,
        val operationId: String,
        val operation: Operation,
    )

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
                .addAnnotation(lombok("RequiredArgsConstructor"))
                .addJavadoc($$"$L", "Client to access all REST APIs.")
                .addField(
                    FieldSpec
                        .builder(
                            ClassName.get("org.springframework.web.reactive.function.client", "WebClient"),
                            "restClient",
                            Modifier.PRIVATE,
                            Modifier.FINAL,
                        ).addAnnotation(nonNull())
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
                        .addStatement(
                            $$"""
                        var fcs = new $T()
                            """.trimIndent(),
                            ClassName.get("org.springframework.format.support", "DefaultFormattingConversionService"),
                        ).addStatement(
                            $$"""
                        new $T().getConverters().forEach(fcs::addConverter)
                            """.trimIndent(),
                            ClassName.get(packageName, "EnumConverters"),
                        ).addStatement(
                            $$"""
                        return $T.builderFor(
                                $T.create(
                                        $T.requireNonNull(restClient)))
                                .conversionService(fcs)
                                .build()
                                .createClient(clazz)
                            """.trimIndent(),
                            ClassName.get("org.springframework.web.service.invoker", "HttpServiceProxyFactory"),
                            ClassName.get("org.springframework.web.reactive.function.client.support", "WebClientAdapter"),
                            ClassName.get("java.util", "Objects"),
                        ).build(),
                )

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

                val apiField =
                    FieldSpec
                        .builder(typeRef, interfaceName.camelCase(), Modifier.PRIVATE, Modifier.FINAL)
                        .initializer($$"computeApi($T.class)", typeRef)
                        .addJavadoc($$"$L", apiDescription ?: "")
                        .addAnnotation(
                            AnnotationSpec
                                .builder(ClassName.get("lombok", "Getter"))
                                .addMember("lazy", "true")
                                .build(),
                        ).build()
                restClients.addField(apiField)
            }
        JavaFile.builder(packageName, restClients.build()).build().writeTo(mainDir)
    }

    private fun getTestClass(interfaceName: String): TypeSpec.Builder =
        TypeSpec
            .classBuilder(interfaceName + "Test")
            .addAnnotation(testExtension())

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
                    respRef.withoutAnnotations().annotated(suitableAnnotations),
                ),
            ).build()
    }

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
            .returns(ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"), ClassName.get("java.lang", "Void")))
            .build()

    private fun suffixContentType(key: String) =
        when (key) {
            "application/json" -> ""
            "application/vnd.github.v3.star+json" -> "Star"
            "application/vnd.github.object" -> "Object"
            "application/json+sarif" -> "Sarif"
            else -> throw IllegalArgumentException("Unknown content type: $key")
        }

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

    private fun webBind(simpleName: String): ClassName = ClassName.get("org.springframework.web.bind.annotation", simpleName)

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