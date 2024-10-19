package codegen

import codegen.ext.camelCase
import codegen.ext.className
import codegen.ext.pascalCase
import com.palantir.javapoet.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import java.io.File
import javax.lang.model.element.Modifier

object WebhooksBuilder {
    fun buildWebhooks(openAPI: OpenAPI, mainDir: File, restPackage: String, webhooksPackage: String, testDir: File) {
        openAPI.webhooks
            .map { (name, webhook) -> createWebhookInterface(name, webhook, openAPI, restPackage) }
            .groupBy { it.first }
            .forEach { k, v ->
                val interfaceBuilder = TypeSpec.interfaceBuilder(k.pascalCase() + "Webhooks")
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(TypeVariableName.get("T"))
                    .addMethods(v.map { it.second })
                JavaFile.builder(webhooksPackage, interfaceBuilder.build()).build().writeTo(mainDir)

                val testBuilder = TypeSpec.classBuilder(k.pascalCase() + "WebhooksTest")
                    .addTypes(v.map { it.third })
                JavaFile.builder(webhooksPackage, testBuilder.build())
                    .addStaticImport(ClassName.get("org.assertj.core.api", "Assertions"), "*")
                    .build()
                    .writeTo(testDir)
            }
    }

    private fun createWebhookInterface(name: String, webhook: PathItem, openAPI: OpenAPI, restPackage: String): Triple<String, MethodSpec?, TypeSpec?> {
        if (webhook.readOperationsMap().size != 1) {
            throw RuntimeException("Webhook $name has more than one operation")
        }

        val testBuilder = TestBuilder()
        val tests = mutableListOf<MethodSpec>()
        val (_, operation) = webhook.readOperationsMap().entries.first()

        val javadoc = mutableListOf(
            CodegenHelper.mdToHtml(operation.summary) +
                    if (operation.description != null) "\n<br/>" + CodegenHelper.mdToHtml(operation.description) else "",
            "\n"
        )
        val subcategory = getSubcategory(operation)
        if (subcategory == null) {
            throw RuntimeException("Missing subcategory for $name")
        }
        val methodName = "process" + operation.operationId.split('/').last().pascalCase()
        val methodSpecBuilder = MethodSpec.methodBuilder(methodName)
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("io.github.pulpogato.common", "GHGenerated"))
                    .addMember("from", "\$S", "#/webhooks/$name")
                    .addMember("by", "\$S", CodegenHelper.codeRef())
                    .build()
            )
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "PostMapping"))
                    .addMember("headers", "\$S", "X-Github-Event=${name}")
                    .build()
            )
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(
                ParameterizedTypeName.get(ClassName.get("org.springframework.http", "ResponseEntity"), TypeVariableName.get("T"))
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

        if (requestBody.content.firstEntry().value.schema.`$ref` != null) {
            val ref = requestBody.content.firstEntry().value.schema.`$ref`.replace("#/components/schemas/", "")
            val examples = requestBody.content.firstEntry().value.examples
            val schema = openAPI.components.schemas.entries.first { it.key == ref }
            val type = schema.className()
            operation.parameters.filter { it.`in` == "header" }.forEach {
                val required = when {
                    it.name.startsWith("X-Hub-Signature") -> false
                    it.name.startsWith("X-GitHub-Enterprise") -> false
                    else -> true
                }
                methodSpecBuilder
                    .addParameter(
                        ParameterSpec.builder(ClassName.get("java.lang", "String"), it.name.camelCase())
                            .addAnnotation(
                                AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestHeader"))
                                    .addMember("required", "\$L", required)
                                    .addMember("name", "\$S", it.name)
                                    .build()
                            )
                            .build()
                    )
            }
            methodSpecBuilder.addParameter(
                ParameterSpec.builder(ClassName.get(restPackage + ".schemas", type), "requestBody")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("org.springframework.web.bind.annotation", "RequestBody")).build())
                    .build()
            )

            methodSpecBuilder.addJavadoc(javadoc.filterNotNull().joinToString("\n"))

            examples?.forEach { (key, value) ->
                val ref = value.`$ref`
                val example = openAPI.components.examples.entries.firstOrNull { it.key == ref.replace("#/components/examples/", "") }
                if (example != null) {
                    tests.add(testBuilder.buildTest(key, example.value.value.toString(), type))
                }
            }

        } else {
            throw RuntimeException("Unknown type for ${requestBody.content.firstEntry().value.schema}")
        }

        val testClass = TypeSpec.classBuilder(name.pascalCase())
            .addMethods(tests)
            .addAnnotation(
                AnnotationSpec.builder(ClassName.get("org.junit.jupiter.api", "Nested")).build()
            )
            .build()

        return Triple(subcategory, methodSpecBuilder.build(), testClass)
    }

    private fun getSubcategory(operation: Operation): String? {
        val xGitHub = operation.extensions.get("x-github") ?: throw RuntimeException("Missing x-github extension")
        val subcategory = (xGitHub as Map<*, *>).get("subcategory") as String?
        return subcategory
    }
}
