package io.github.pulpogato.restcodegen

import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.CodeBlock
import com.palantir.javapoet.JavaFile
import com.palantir.javapoet.MethodSpec
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import com.palantir.javapoet.TypeSpec
import io.github.pulpogato.restcodegen.Annotations.generated
import io.github.pulpogato.restcodegen.ext.pascalCase
import io.github.pulpogato.restcodegen.ext.referenceAndDefinition
import java.io.File
import javax.lang.model.element.Modifier

class SchemasBuilder {
    private companion object {
        // jackson-annotations keeps this package for both Jackson 2 and Jackson 3.
        const val PACKAGE_JACKSON_ANNOTATION = "com.fasterxml.jackson.annotation"
        const val PACKAGE_PULPOGATO_JACKSON = "io.github.pulpogato.common.jackson"
        const val TYPE_CLASS_FORMAT = $$"$T.class"
    }

    fun buildSchemas(
        context: Context,
        mainDir: File,
        packageName: String,
        enumConverters: MutableSet<ClassName>,
    ) {
        val openAPI = context.openAPI

        // Webhook events that share a subcategory (e.g. all `check_run` events) get a generated
        // sealed supertype so callers can pattern match. Work out which body schemas belong to a
        // group before generating, so each member class can be tagged as a permitted subtype.
        val supertypeGroups = WebhookSupertypes.compute(openAPI, packageName)
        val singleEventWebhookBodyKeys = WebhookSupertypes.singleEventBodyKeys(openAPI)
        val discriminatedGroups = context.discriminatedOneOfGroups
        val nonDiscriminatedGroups = context.nonDiscriminatedOneOfGroups
        // A body schema can belong to more than one subcategory (e.g. the `exemption-request-*` bodies
        // are shared by several `*_request_*` events), so a member may be permitted by multiple sealed
        // interfaces and must implement all of them. Discriminated and non-discriminated REST API oneOf
        // groups are included alongside the webhook supertypes.
        val schemaKeyToSupertypes =
            (
                supertypeGroups.flatMap { g -> g.memberSchemaKeys.map { it to g.supertype } } +
                    discriminatedGroups.flatMap { g -> g.memberSchemaKeys.map { it to g.supertype } } +
                    nonDiscriminatedGroups.flatMap { g -> g.memberSchemaKeys.map { it to g.supertype } }
            ).groupBy({ it.first }, { it.second })

        // Captured per member schema so we can later derive the getters common to every member.
        val memberFieldsByKey = mutableMapOf<String, Map<String, TypeName>>()

        // Members of a non-discriminated group need their inherited @JsonDeserialize canceled (see
        // enrichMember): the interface carries the deserializer and would otherwise recurse into itself.
        val nonDiscriminatedMemberKeys = nonDiscriminatedGroups.flatMap { it.memberSchemaKeys }.toSet()

        openAPI.components.schemas.forEach { entry ->
            val (typeName, definition) =
                referenceAndDefinition(context.withSchemaStack("#", "components", "schemas", entry.key), entry, "", null)!!
            definition?.let {
                val supertypes = schemaKeyToSupertypes[entry.key]
                val typeSpec =
                    if (!supertypes.isNullOrEmpty()) {
                        val (accessible, enriched) = enrichMember(it, supertypes, entry.key in nonDiscriminatedMemberKeys)
                        memberFieldsByKey[entry.key] = accessible
                        enriched
                    } else {
                        it
                    }
                // Single-event webhook bodies aren't a permitted subtype of any generated sealed
                // interface, so they need tagging here directly rather than via enrichMember.
                val taggedTypeSpec =
                    if (entry.key in singleEventWebhookBodyKeys) {
                        typeSpec.toBuilder().addSuperinterface(ClassName.get(Types.COMMON_PACKAGE, "WebhookEvent")).build()
                    } else {
                        typeSpec
                    }

                JavaFile.builder(packageName, taggedTypeSpec).build().writeTo(mainDir)

                // If this is an enum, add its converter to the set
                if (it.enumConstants().isNotEmpty() && typeName is ClassName) {
                    val converterClassName = typeName.nestedClass("${typeName.simpleName()}Converter")
                    enumConverters.add(converterClassName)
                }
            }
        }

        writeWebhookSupertypeInterfaces(context, mainDir, packageName, supertypeGroups, memberFieldsByKey)
        writeDiscriminatedOneOfInterfaces(context, mainDir, packageName, discriminatedGroups, memberFieldsByKey)
        writeNonDiscriminatedOneOfInterfaces(context, mainDir, packageName, nonDiscriminatedGroups, memberFieldsByKey)
    }

    /**
     * Emits one sealed interface per webhook supertype group. The interface permits each member body
     * class and declares the getters whose name and type are identical across every member.
     */
    private fun writeWebhookSupertypeInterfaces(
        context: Context,
        mainDir: File,
        packageName: String,
        groups: List<WebhookSupertypes.Group>,
        memberFieldsByKey: Map<String, Map<String, TypeName>>,
    ) {
        groups.forEach { group ->
            val annotations =
                if (group.discriminable) jsonSubTypesAnnotations(packageName, group) else emptyList()
            writeSealedInterface(
                context,
                mainDir,
                packageName,
                group.supertype,
                group.memberSchemaKeys,
                "A sealed supertype for the <code>${group.subcategory}</code> webhook payloads, " +
                    "exposing the fields common to every event variant.\n" +
                    "<br/>Use pattern matching over the permitted subtypes to handle individual variants.",
                annotations,
                memberFieldsByKey,
                markerInterface = ClassName.get(Types.COMMON_PACKAGE, "WebhookEvent"),
            )
        }
    }

    /**
     * Emits one sealed interface per discriminated REST API oneOf group.
     *
     * Each interface is annotated with `@JsonTypeInfo` and `@JsonSubTypes` so Jackson can pick the
     * correct concrete subtype directly from the discriminator property in the payload.
     */
    private fun writeDiscriminatedOneOfInterfaces(
        context: Context,
        mainDir: File,
        packageName: String,
        groups: List<DiscriminatedOneOfGroups.Group>,
        memberFieldsByKey: Map<String, Map<String, TypeName>>,
    ) {
        groups.forEach { group ->
            writeSealedInterface(
                context,
                mainDir,
                packageName,
                group.supertype,
                group.memberSchemaKeys,
                "A sealed supertype for the <code>${group.discriminatorProperty}</code>-discriminated oneOf, " +
                    "deserializable directly via Jackson using the discriminator property.\n" +
                    "<br/>Use pattern matching over the permitted subtypes to handle each variant.",
                jsonDiscriminatorAnnotations(packageName, group),
                memberFieldsByKey,
            )
        }
    }

    private fun writeSealedInterface(
        context: Context,
        mainDir: File,
        packageName: String,
        supertype: ClassName,
        memberSchemaKeys: List<String>,
        javadoc: String,
        annotations: List<AnnotationSpec>,
        memberFieldsByKey: Map<String, Map<String, TypeName>>,
        nestedTypes: List<TypeSpec> = emptyList(),
        markerInterface: ClassName = ClassName.get(Types.COMMON_PACKAGE, "PulpogatoType"),
    ) {
        val memberFields = memberSchemaKeys.map { memberFieldsByKey[it] }
        // Skip if any member did not generate as a class; otherwise `permits` would dangle.
        if (memberFields.any { it == null }) return
        val present = memberFields.filterNotNull()
        val commonFields = present.first().filter { (name, type) -> present.all { it[name] == type } }

        val builder =
            TypeSpec
                .interfaceBuilder(supertype)
                .addModifiers(Modifier.PUBLIC, Modifier.SEALED)
                .addSuperinterface(markerInterface)
                .addAnnotation(generated(0, context.withSchemaStack("#", "synthetic")))
                .addJavadoc($$"$L", javadoc)

        annotations.forEach { builder.addAnnotation(it) }

        memberSchemaKeys.forEach { key ->
            builder.addPermittedSubclass(ClassName.get(packageName, key.pascalCase()))
        }

        commonFields.forEach { (name, type) ->
            builder.addMethod(
                MethodSpec
                    .methodBuilder("get${name.pascalCase()}")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(type)
                    .build(),
            )
        }

        nestedTypes.forEach { builder.addType(it) }

        JavaFile.builder(packageName, builder.build()).build().writeTo(mainDir)
    }

    /**
     * Emits one sealed interface per non-discriminated REST API oneOf group.
     *
     * Without a discriminator there is nothing in the payload to route on, so the interface carries a
     * custom `@JsonDeserialize` (one per Jackson runtime) backed by nested deserializer classes that try
     * each permitted subtype in turn. The annotation lives on the interface type — not the field — so it
     * is reachable wherever the interface is used, including through `NullableOptional<Interface>` and
     * collections. To stop the permitted subtypes from inheriting it (which would recurse infinitely),
     * each member resets its own deserializer to the default; see [enrichMember]. Serialization needs no
     * annotation: a field holds a concrete subtype, serialized by its runtime type.
     */
    private fun writeNonDiscriminatedOneOfInterfaces(
        context: Context,
        mainDir: File,
        packageName: String,
        groups: List<NonDiscriminatedOneOfGroups.Group>,
        memberFieldsByKey: Map<String, Map<String, TypeName>>,
    ) {
        groups.forEach { group ->
            writeSealedInterface(
                context,
                mainDir,
                packageName,
                group.supertype,
                group.memberSchemaKeys,
                "A sealed supertype for a non-discriminated oneOf of " +
                    group.memberSchemaKeys.joinToString(", ") { "<code>$it</code>" } + ".\n" +
                    "<br/>With no discriminator, each variant is tried in turn during deserialization.\n" +
                    "<br/>Use pattern matching over the permitted subtypes to handle each variant.",
                nonDiscriminatedDeserializeAnnotations(group),
                memberFieldsByKey,
                nonDiscriminatedDeserializerTypes(packageName, group),
            )
        }
    }

    /**
     * The `@JsonDeserialize` pair (Jackson 3 then Jackson 2) placed on a non-discriminated interface,
     * pointing at the nested deserializer classes from [nonDiscriminatedDeserializerTypes].
     */
    private fun nonDiscriminatedDeserializeAnnotations(group: NonDiscriminatedOneOfGroups.Group): List<AnnotationSpec> {
        val base = group.supertype.simpleName()
        return listOf(
            AnnotationSpec
                .builder(ClassName.get("tools.jackson.databind.annotation", "JsonDeserialize"))
                .addMember("using", TYPE_CLASS_FORMAT, group.supertype.nestedClass("${base}Jackson3Deserializer"))
                .build(),
            AnnotationSpec
                .builder(ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonDeserialize"))
                .addMember("using", TYPE_CLASS_FORMAT, group.supertype.nestedClass("${base}Jackson2Deserializer"))
                .build(),
        )
    }

    /**
     * The `@JsonDeserialize(using = None.class)` pair (Jackson 3 then Jackson 2) that cancels the
     * deserializer a member would otherwise inherit from a non-discriminated supertype, restoring
     * default bean deserialization for the concrete member.
     */
    private fun deserializerResetAnnotations(): List<AnnotationSpec> =
        listOf(
            AnnotationSpec
                .builder(ClassName.get("tools.jackson.databind.annotation", "JsonDeserialize"))
                .addMember("using", TYPE_CLASS_FORMAT, ClassName.get("tools.jackson.databind", "ValueDeserializer", "None"))
                .build(),
            AnnotationSpec
                .builder(ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonDeserialize"))
                .addMember("using", TYPE_CLASS_FORMAT, ClassName.get("com.fasterxml.jackson.databind", "JsonDeserializer", "None"))
                .build(),
        )

    /**
     * Nested no-arg deserializer classes for both Jackson runtimes. Each extends the shared
     * `JacksonNOneOfDeserializer` and passes the interface type plus the ordered list of candidate
     * subtypes, since `@JsonDeserialize(using = ...)` requires a no-arg-constructable deserializer.
     */
    private fun nonDiscriminatedDeserializerTypes(
        packageName: String,
        group: NonDiscriminatedOneOfGroups.Group,
    ): List<TypeSpec> {
        val base = group.supertype.simpleName()
        val memberClasses = group.memberSchemaKeys.map { ClassName.get(packageName, it.pascalCase()) }
        return listOf(
            oneOfDeserializerType(
                "${base}Jackson3Deserializer",
                ClassName.get(PACKAGE_PULPOGATO_JACKSON, "Jackson3OneOfDeserializer"),
                group.supertype,
                memberClasses,
            ),
            oneOfDeserializerType(
                "${base}Jackson2Deserializer",
                ClassName.get(PACKAGE_PULPOGATO_JACKSON, "Jackson2OneOfDeserializer"),
                group.supertype,
                memberClasses,
            ),
        )
    }

    private fun oneOfDeserializerType(
        simpleName: String,
        baseClass: ClassName,
        supertype: ClassName,
        memberClasses: List<ClassName>,
    ): TypeSpec {
        val candidates = CodeBlock.builder().add($$"$T.of(", ClassName.get(List::class.java))
        memberClasses.forEachIndexed { index, member ->
            if (index > 0) candidates.add(", ")
            candidates.add(TYPE_CLASS_FORMAT, member)
        }
        candidates.add(")")

        val constructor =
            MethodSpec
                .constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement($$"super($T.class, $L)", supertype, candidates.build())
                .build()

        return TypeSpec
            .classBuilder(simpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .superclass(ParameterizedTypeName.get(baseClass, supertype))
            .addMethod(constructor)
            .build()
    }

    /**
     * Builds the `@JsonTypeInfo` / `@JsonSubTypes` pair for a discriminated REST API oneOf group.
     *
     * Uses `EXISTING_PROPERTY` so the discriminator field stays in the payload and each subtype
     * continues to deserialize it into its own field.
     */
    private fun jsonDiscriminatorAnnotations(
        packageName: String,
        group: DiscriminatedOneOfGroups.Group,
    ): List<AnnotationSpec> {
        val jsonTypeInfo = ClassName.get(PACKAGE_JACKSON_ANNOTATION, "JsonTypeInfo")
        val jsonSubTypes = ClassName.get(PACKAGE_JACKSON_ANNOTATION, "JsonSubTypes")
        val subTypesType = ClassName.get(PACKAGE_JACKSON_ANNOTATION, "JsonSubTypes", "Type")

        val defaultImplClass = ClassName.get(packageName, group.memberSchemaKeys.first().pascalCase())
        val typeInfo =
            AnnotationSpec
                .builder(jsonTypeInfo)
                .addMember("use", $$"$T.Id.NAME", jsonTypeInfo)
                .addMember("include", $$"$T.As.EXISTING_PROPERTY", jsonTypeInfo)
                .addMember("property", $$"$S", group.discriminatorProperty)
                .addMember("defaultImpl", TYPE_CLASS_FORMAT, defaultImplClass)
                .build()

        val subTypes = AnnotationSpec.builder(jsonSubTypes)
        group.memberSchemaKeys.forEach { key ->
            val memberClass = ClassName.get(packageName, key.pascalCase())
            group.valuesByKey[key].orEmpty().forEach { value ->
                subTypes.addMember(
                    "value",
                    $$"$L",
                    AnnotationSpec
                        .builder(subTypesType)
                        .addMember("value", TYPE_CLASS_FORMAT, memberClass)
                        .addMember("name", $$"$S", value)
                        .build(),
                )
            }
        }

        return listOf(typeInfo, subTypes.build())
    }

    /**
     * Builds the `@JsonTypeInfo` / `@JsonSubTypes` pair that lets Jackson deserialize the supertype
     * directly to the correct member based on the `action` discriminator. Only called for groups that
     * are [discriminable][WebhookSupertypes.Group.discriminable].
     *
     * The Jackson 2 and Jackson 3 runtimes both read these `com.fasterxml.jackson.annotation` types,
     * so a single annotation pair covers both.
     */
    private fun jsonSubTypesAnnotations(
        packageName: String,
        group: WebhookSupertypes.Group,
    ): List<AnnotationSpec> {
        val jsonTypeInfo = ClassName.get(PACKAGE_JACKSON_ANNOTATION, "JsonTypeInfo")
        val jsonSubTypes = ClassName.get(PACKAGE_JACKSON_ANNOTATION, "JsonSubTypes")
        val subTypesType = ClassName.get(PACKAGE_JACKSON_ANNOTATION, "JsonSubTypes", "Type")

        // EXISTING_PROPERTY: the `action` value already lives in the payload, so it is not consumed and
        // each subtype keeps populating its own `action` field as usual.
        val typeInfo =
            AnnotationSpec
                .builder(jsonTypeInfo)
                .addMember("use", $$"$T.Id.NAME", jsonTypeInfo)
                .addMember("include", $$"$T.As.EXISTING_PROPERTY", jsonTypeInfo)
                .addMember("property", $$"$S", "action")
                .build()

        val subTypes = AnnotationSpec.builder(jsonSubTypes)
        group.memberSchemaKeys.forEach { key ->
            val memberClass = ClassName.get(packageName, key.pascalCase())
            group.actionsByKey[key].orEmpty().forEach { value ->
                subTypes.addMember(
                    "value",
                    $$"$L",
                    AnnotationSpec
                        .builder(subTypesType)
                        .addMember("value", TYPE_CLASS_FORMAT, memberClass)
                        .addMember("name", $$"$S", value)
                        .build(),
                )
            }
        }

        return listOf(typeInfo, subTypes.build())
    }

    /**
     * Marks a member body class as a permitted subtype of its sealed supertype(s) and returns the
     * fields callers can read off the supertype.
     *
     * A plain object exposes its own instance fields. A composite (`oneOf`/`anyOf`) body stores its
     * variants as branch objects rather than flattened fields, so we expose the fields common to every
     * branch and add delegating getters that read from whichever branch is populated — letting those
     * fields participate in the supertype's common getters.
     */
    private fun enrichMember(
        typeSpec: TypeSpec,
        supertypes: List<ClassName>,
        cancelInheritedDeserializer: Boolean,
    ): Pair<Map<String, TypeName>, TypeSpec> {
        // A permitted subtype of a sealed interface must declare its own sealing; these classes are
        // open POJOs, so mark them non-sealed.
        val builder = typeSpec.toBuilder().addModifiers(Modifier.NON_SEALED)
        supertypes.forEach { builder.addSuperinterface(it) }

        // A non-discriminated supertype carries a custom @JsonDeserialize that this member would inherit,
        // sending the member's own deserialization back through the interface deserializer and recursing
        // forever. Reset it to the default bean deserializer so reading the concrete member is direct.
        if (cancelInheritedDeserializer) {
            deserializerResetAnnotations().forEach { builder.addAnnotation(it) }
        }

        val branches = inlineBranches(typeSpec)
        val accessible =
            if (branches.isEmpty()) {
                instanceFields(typeSpec)
            } else {
                val common = branchCommonFields(branches)
                val branchGetters = branches.map { it.first }
                common.forEach { (name, type) -> builder.addMethod(delegatingGetter(name, type, branchGetters)) }
                common
            }
        return accessible to builder.build()
    }

    private fun instanceFields(typeSpec: TypeSpec): Map<String, TypeName> =
        typeSpec
            .fieldSpecs()
            .filter { Modifier.STATIC !in it.modifiers() }
            .associate { it.name() to it.type().withoutAnnotations() }

    /**
     * For a composite (`oneOf`/`anyOf`) body, the (getter-name, branch-type) pairs of its inline branch
     * classes, in declaration order. Empty when the type isn't composite or any variant isn't an inline
     * nested branch (e.g. a `oneOf` of `$ref`s), in which case branch fields can't be read locally.
     */
    private fun inlineBranches(typeSpec: TypeSpec): List<Pair<String, TypeSpec>> {
        val isComposite = typeSpec.typeSpecs().any { it.name()?.endsWith("Jackson3Deserializer") == true }
        if (!isComposite) return emptyList()

        val nestedByName = typeSpec.typeSpecs().associateBy { it.name() }
        val fields = typeSpec.fieldSpecs().filter { Modifier.STATIC !in it.modifiers() }
        if (fields.isEmpty()) return emptyList()
        return fields.map { field ->
            val branchName = (field.type().withoutAnnotations() as? ClassName)?.simpleName()
            val branch = branchName?.let { nestedByName[it] } ?: return emptyList()
            "get${field.name().pascalCase()}" to branch
        }
    }

    private fun branchCommonFields(branches: List<Pair<String, TypeSpec>>): Map<String, TypeName> {
        val perBranch = branches.map { (_, branch) -> instanceFields(branch) }
        return perBranch.first().filter { (name, type) -> perBranch.all { it[name] == type } }
    }

    private fun delegatingGetter(
        name: String,
        type: TypeName,
        branchGetters: List<String>,
    ): MethodSpec {
        val code = CodeBlock.builder()
        branchGetters.forEach { branchGetter ->
            code.beginControlFlow($$"if ($L() != null)", branchGetter)
            code.addStatement($$"return $L().get$L()", branchGetter, name.pascalCase())
            code.endControlFlow()
        }
        code.addStatement("return null")
        return MethodSpec
            .methodBuilder("get${name.pascalCase()}")
            .addModifiers(Modifier.PUBLIC)
            .returns(type)
            .addCode(code.build())
            .build()
    }
}