package io.github.pulpogato.restcodegen

import com.palantir.javapoet.ClassName
import com.palantir.javapoet.FieldSpec
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeSpec
import com.palantir.javapoet.TypeVariableName
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.ext.pascalCase
import org.intellij.lang.annotations.Language
import java.io.File
import javax.lang.model.element.Modifier

private const val PACKAGE_SPRING_FORMAT_SUPPORT = "org.springframework.format.support"
private const val PACKAGE_SPRING_WEBCLIENT = "org.springframework.web.reactive.function.client"
private const val PACKAGE_SPRING_WEBCLIENT_ADAPTER = "org.springframework.web.reactive.function.client.support"
private const val PACKAGE_SPRING_RESTCLIENT = "org.springframework.web.client"
private const val PACKAGE_SPRING_RESTCLIENT_ADAPTER = "org.springframework.web.client.support"
private const val PACKAGE_SPRING_HTTP_CLIENT = "org.springframework.http.client"
private const val PACKAGE_PULPOGATO_CLIENT = "io.github.pulpogato.common.client"

/**
 * Captures the handful of ways a generated `RestClients` container class differs depending on
 * the underlying Spring HTTP client - {@code WebClient} (blocking or reactive) or the synchronous
 * {@code RestClient} - so [ClientContainerBuilder.build] can emit all three from one
 * `TypeSpec`-building function instead of duplicating it per client technology.
 */
internal interface ClientContainerSpec {
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

internal object WebClientContainerSpec : ClientContainerSpec {
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

    @Language("java")
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

internal object RestClientContainerSpec : ClientContainerSpec {
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

    @Language("java")
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

internal class ClientContainerBuilder {
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
    fun build(
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
                    listOf(
                        "Central entry point for accessing all GitHub REST APIs.",
                        "",
                        """
                        <p>Construct with a pre-configured {@link ${clientType.simpleName()}}, then call
                        {@code get<Xxx>Api()} to obtain a typed API interface. Example:
                        """.trimIndent(),
                        "",
                        "<pre>{@code",
                        spec.exampleSnippet,
                        "}</pre>",
                    ).joinToString("\n"),
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
                    listOf(
                        "Constructs a client with an explicit ${spec.chainNoun} chain.",
                        "",
                        """
                        <p>The given {@code ${spec.filterParamName}} are applied, in order, to {@code $fieldName}.
                        Use this constructor to customize, reorder, or omit the default ${spec.chainNoun}s
                        applied by {@link #RestClients(${clientType.simpleName()})}.
                        """.trimIndent(),
                        "",
                        defaultFilterSeeDoc,
                    ).joinToString("\n"),
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
                    listOf(
                        "Constructs a client with the default ${spec.chainNoun} chain:",
                        "<ul>",
                        defaultFilterDoc,
                        "</ul>",
                        "",
                        "@see #RestClients(${clientType.simpleName()}, List)",
                    ).joinToString("\n"),
                ).addStatement(
                    $$"this($N, $T.of(new $T(), new $T(), new $T()))",
                    fieldName,
                    ClassName.get("java.util", "List"),
                    spec.defaultFilters[0].first,
                    spec.defaultFilters[1].first,
                    spec.defaultFilters[2].first,
                ).build(),
        )

        JavaFile
            .builder(packageName, restClients.build())
            .skipJavaLangImports(true)
            .build()
            .writeTo(mainDir)
    }
}