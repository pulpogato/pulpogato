package codegen

import codegen.Annotations.generated
import codegen.ext.camelCase
import codegen.ext.className
import codegen.ext.pascalCase
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import com.palantir.javapoet.TypeVariableName
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import java.io.File
import javax.lang.model.element.Modifier

object WebhooksBuilder {
    fun buildWebhooks(
        openAPI: OpenAPI,
        mainDir: File,
        restPackage: String,
        webhooksPackage: String,
        testDir: File,
    ) {
        openAPI.webhooks
            .entries
            .groupBy { getSubcategory(it.value.readOperationsMap().values.first())!! }
            .forEach { (k, v) ->
                val interfaceBuilder =
                    TypeSpec.interfaceBuilder(k.pascalCase() + "Webhooks")
                        .addModifiers(Modifier.PUBLIC)
                        .addTypeVariable(TypeVariableName.get("T"))
                val triples = v.map { (name, webhook) -> createWebhookInterface(name, webhook, openAPI, restPackage) }
                interfaceBuilder
                    .addMethods(triples.map { it.second })
                JavaFile.builder(webhooksPackage, interfaceBuilder.build())
                    .build()
                    .writeTo(mainDir)

                val nestedTests = triples.mapNotNull { it.third }
                if (nestedTests.isNotEmpty()) {
                    val testBuilder =
                        TypeSpec.classBuilder(k.pascalCase() + "WebhooksTest")
                            .addTypes(nestedTests)
                    JavaFile.builder(webhooksPackage, testBuilder.build())
                        .build()
                        .writeTo(testDir)
                }
            }
    }

    private fun createWebhookInterface(
        name: String,
        webhook: PathItem,
        openAPI: OpenAPI,
        restPackage: String,
    ): Triple<String, MethodSpec?, TypeSpec?> {
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
        val subcategory = getSubcategory(operation) ?: throw RuntimeException("Missing subcategory for $name")
        val methodName = "process" + operation.operationId.split('/').last().pascalCase()
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
                                            ParameterSpec.builder(ClassName.get("java.lang", "String"), it.name.camelCase())
                                                .addAnnotation(parameterAnnotation.build())
                                                .addAnnotation(generated(0))
                                                .build(),
                                        )
                                }
                            }
                            val className = ClassName.get("${restPackage}.schemas", type)
                            Context.withSchemaStack("requestBody") {
                                methodSpecBuilder.addParameter(
                                    ParameterSpec.builder(className, "requestBody")
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
                                        val ref = value.`$ref`
                                        val example = openAPI.components.examples.entries.firstOrNull { it.key == ref.replace("#/components/examples/", "") }
                                        if (example != null) {
                                            tests.add(TestBuilder.buildTest(key, example.value.value, className))
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

        val testClass =
            if (tests.isNotEmpty()) {
                TypeSpec.classBuilder(name.pascalCase())
                    .addMethods(tests)
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build())
                    .build()
            } else {
                null
            }

        return Triple(subcategory, methodSpecBuilder.build(), testClass)
    }

    private fun getSubcategory(operation: Operation): String? {
        val xGitHub = operation.extensions["x-github"] ?: throw RuntimeException("Missing x-github extension")
        val subcategory = (xGitHub as Map<*, *>)["subcategory"] as String?
        return subcategory
    }
}