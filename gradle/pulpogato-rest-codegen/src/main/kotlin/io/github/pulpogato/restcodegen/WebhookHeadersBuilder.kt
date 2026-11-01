package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.Annotations.nullable
import io.github.pulpogato.restcodegen.WebhooksBuilder.HeaderField
import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.pascalCase
import javax.lang.model.element.Modifier

internal class WebhookHeadersBuilder(
    private val webhookHeadersType: ClassName,
    private val webhookHeadersResolverType: ClassName,
) {
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
    fun collectHeaderFields(context: Context): List<HeaderField> {
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

    fun buildWebhookHeadersType(
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

    fun buildWebhookHeadersResolverType(
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
}