package codegen

import codegen.Annotations.generated
import codegen.ext.camelCase
import codegen.ext.pascalCase
import codegen.ext.referenceAndDefinition
import codegen.ext.unkeywordize
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.parameters.Parameter
import java.io.File
import javax.lang.model.element.Modifier

object PathsBuilder {
    class AtomicMethod(val path: String, val method: HttpMethod, val operationId: String, val operation: Operation)

    fun buildApis(
        openAPI: OpenAPI,
        outputDir: File,
        packageName: String,
        testDir: File,
    ) {
        val apiDir = File(outputDir, packageName.replace(".", "/"))
        apiDir.mkdirs()
        openAPI.paths
            .flatMap { (path, pathItem) ->
                pathItem.readOperationsMap().map { (method, operation) ->
                    AtomicMethod(path, method, operation.operationId, operation)
                }
            }
            .groupBy { it.operationId.split('/')[0] }
            .forEach { (groupName, atomicMethods) ->
                val docTag = atomicMethods[0].operation.tags[0]
                val apiDescription = openAPI.tags.find { it.name == docTag }!!.description
                val interfaceName = groupName.pascalCase() + "Api"

                val tagIndex = openAPI.tags!!.indexOfFirst { docTag == it.name }.toString()
                val pathInterface =
                    Context.withSchemaStack("#", "tags", tagIndex) {
                        TypeSpec.interfaceBuilder(interfaceName)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(generated(0))
                            .addJavadoc(apiDescription ?: "")
                    }

                val testClass = TypeSpec.classBuilder(interfaceName + "Test")
                val typeRef = ClassName.get(packageName, interfaceName)

                atomicMethods.forEach { atomicMethod ->
                    Context.withSchemaStack("#", "paths", atomicMethod.path, atomicMethod.method.name.lowercase()) {
                        buildMethod(atomicMethod, openAPI, pathInterface, typeRef, testClass)
                    }
                }

                JavaFile.builder(packageName, pathInterface.build()).build().writeTo(outputDir)
                val typeClassBuilt = testClass.build()
                if (typeClassBuilt.methodSpecs().isNotEmpty() || typeClassBuilt.typeSpecs().isNotEmpty()) {
                    JavaFile.builder(packageName, typeClassBuilt).build().writeTo(testDir)
                }
            }
    }

    private fun buildMethod(
        atomicMethod: AtomicMethod,
        openAPI: OpenAPI,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        testClass: TypeSpec.Builder,
    ) {
        val parameters = getParameters(atomicMethod, openAPI, typeDef, typeRef)

        val successResponses =
            atomicMethod.operation.responses
                .filter { (responseCode, apiResponse) ->
                    val content = apiResponse.content
                    content != null && content.isNotEmpty() && responseCode.startsWith("2")
                }
                .toMutableMap()

        if (successResponses.size > 1 && successResponses.containsKey("204")) {
            successResponses.remove("204")
        }

        val javadoc =
            buildMethodJavadoc(atomicMethod, parameters)
                .split("\n")
                .dropLastWhile { it.isEmpty() }
                .joinToString("\n")

        val successResponse = successResponses.entries.minByOrNull { it.key }

        if (successResponse == null || successResponse.value.content == null) {
            typeDef.addMethod(
                MethodSpec.methodBuilder(atomicMethod.operationId.split('/')[1].camelCase())
                    .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                    .addJavadoc(javadoc)
                    .addAnnotation(
                        AnnotationSpec.builder(ClassName.get("org.springframework.web.service.annotation", atomicMethod.method.name.lowercase().pascalCase() + "Exchange"))
                            .addMember("value", "\$S", atomicMethod.path)
                            .build(),
                    )
                    .addAnnotation(generated(0))
                    .addParameters(parameters.map { buildParameter(it, atomicMethod, typeDef, typeRef, testClass) })
                    .returns(ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"), ClassName.get("java.lang", "Void")))
                    .build(),
            )
        } else {
            successResponse.value.content.forEach { (contentType, details) ->
                val rad =
                    Context.withSchemaStack("responses", successResponse.key, "content", contentType, "schema") {
                        mapOf("${atomicMethod.operationId.split('/')[1].pascalCase()}${successResponse.key}" to details.schema).entries.first()
                            .referenceAndDefinition("", typeRef)
                    }
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
                    val testMethods =
                        Context.withSchemaStack("responses", successResponse.key, "content", contentType, "examples") {
                            val examples =
                                when {
                                    contentType.contains("json") -> details.examples ?: emptyMap()
                                    else -> emptyMap()
                                }
                            examples
                                .filter { (_, v) -> v.value != null }
                                .map { (k, v) ->
                                    Context.withSchemaStack(k, "value") {
                                        TestBuilder.buildTest("${successResponse.key}$k", v.value, respRef)
                                    }
                                }
                        }

                    val parameterSpecs = parameters.map { buildParameter(it, atomicMethod, typeDef, typeRef, testClass) }
                    if (contentType.contains("json") && testMethods.isNotEmpty()) {
                        testClass.addType(
                            TypeSpec.classBuilder(methodName.pascalCase() + "Response")
                                .addAnnotation(generated(0))
                                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                                .addMethods(testMethods)
                                .addJavadoc("Tests {@link $typeRef#$methodName}")
                                .build(),
                        )
                    }
                    val suitableAnotations = respRef.annotations().filter {
                        (it.type() as ClassName).simpleName() != "JsonFormat"
                    }
                    typeDef.addMethod(
                        MethodSpec.methodBuilder(methodName)
                            .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                            .addJavadoc(javadoc)
                            .addAnnotation(
                                AnnotationSpec.builder(ClassName.get("org.springframework.web.service.annotation", atomicMethod.method.name.lowercase().pascalCase() + "Exchange"))
                                    .addMember("value", "\$S", atomicMethod.path)
                                    .addMember("accept", "\$S", contentType)
                                    .build(),
                            )
                            .addAnnotation(generated(0))
                            .addParameters(parameterSpecs)
                            .returns(ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"),
                                respRef.withoutAnnotations().annotated(suitableAnotations)
                            ))
                            .build(),
                    )
                }
            }
        }
    }

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
        theParameter: Parameter,
        atomicMethod: AtomicMethod,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
        testClass: TypeSpec.Builder,
    ): ParameterSpec {
        val operationName = atomicMethod.operationId.split('/')[1]
        val (ref, def) =
            mapOf(theParameter.name to theParameter.schema).entries.first()
                .referenceAndDefinition(operationName.pascalCase(), typeRef)!!

        if (def != null) {
            val matchingType = typeDef.build().typeSpecs().find { k -> k.name() == def.name() }
            if (matchingType == null) {
                typeDef.addType(def.toBuilder().addModifiers(Modifier.STATIC, Modifier.PUBLIC).build())
            }
        }

        val paramName = theParameter.name.unkeywordize().camelCase()

        return ParameterSpec.builder(ref, paramName)
            .addAnnotation(
                when (theParameter.`in`) {
                    "query" ->
                        AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestParam"))
                            .addMember("value", "\$S", theParameter.name)
                            .build()
                    "body" -> buildBodyAnnotation(theParameter, paramName, ref, atomicMethod, testClass, operationName, typeRef)
                    "path" ->
                        AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PathVariable"))
                            .addMember("value", "\$S", theParameter.name)
                            .build()
                    "header" ->
                        AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestHeader"))
                            .build()
                    else -> throw IllegalArgumentException("Unknown parameter type: ${theParameter.`in`}")
                },
            )
            .build()
    }

    private fun buildBodyAnnotation(
        theParameter: Parameter,
        paramName: String,
        ref: TypeName,
        atomicMethod: AtomicMethod,
        testClass: TypeSpec.Builder,
        operationName: String,
        typeRef: ClassName,
    ): AnnotationSpec {
        val testMethods =
            (theParameter.examples ?: emptyMap())
                .filter { (_, v) -> v.value != null && !v.value.toString().startsWith("@") }
                .map { (k, v) ->
                    Context.withSchemaStack("requestBody", "content", "application/json", "examples", k, "value") {
                        TestBuilder.buildTest("${paramName}_$k", v.value, ref)
                    }
                }
        if (testMethods.isNotEmpty()) {
            testClass.addType(
                TypeSpec.classBuilder("${operationName}RequestBody".pascalCase())
                    .addJavadoc("Tests {@link $typeRef#${atomicMethod.operationId.split('/')[1].camelCase()}}")
                    .addAnnotation(generated(0))
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                    .addMethods(testMethods)
                    .build(),
            )
        }
        return AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestBody"))
            .build()
    }

    private fun getParameters(
        atomicMethod: AtomicMethod,
        openAPI: OpenAPI,
        typeDef: TypeSpec.Builder,
        typeRef: ClassName,
    ): List<Parameter> {
        val parameters =
            atomicMethod.operation.parameters
                ?.mapNotNull { parameter ->
                    if (parameter.`$ref` == null) {
                        parameter
                    } else {
                        val refName = parameter.`$ref`.replace("#/components/parameters/", "")
                        openAPI.components.parameters[refName]
                    }
                }?.toMutableList() ?: mutableListOf()

        if (atomicMethod.operation.requestBody != null) {
            val requestBody = atomicMethod.operation.requestBody
            val firstEntry = requestBody.content.firstEntry()
            Context.withSchemaStack("requestBody", "content", firstEntry.key, "schema") {
                val (_, def) =
                    mapOf("requestBody" to firstEntry.value.schema).entries.first()
                        .referenceAndDefinition(atomicMethod.operationId.split('/')[1].pascalCase(), typeRef)!!
                def?.let {
                    val matchingType = typeDef.build().typeSpecs().find { k -> k.name() == it.name() }
                    if (matchingType == null) {
                        typeDef.addType(it.toBuilder().addModifiers(Modifier.STATIC, Modifier.PUBLIC).build())
                    }
                }
            }
            parameters.add(
                Parameter()
                    .`in`("body")
                    .name("requestBody")
                    .description(requestBody.description ?: "The request body")
                    .schema(firstEntry.value.schema)
                    .examples(firstEntry.value.examples)
                    .required(true),
            )
        }
        return parameters.toList()
    }
}