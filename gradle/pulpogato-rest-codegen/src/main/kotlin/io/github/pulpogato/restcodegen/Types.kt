package io.github.pulpogato.restcodegen

import com.fasterxml.jackson.annotation.JsonFormat
import com.palantir.javapoet.AnnotationSpec
import com.palantir.javapoet.ArrayTypeName
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName
import io.github.pulpogato.restcodegen.Annotations.jsonFormat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

object Types {
    private const val COMMON_PACKAGE = "io.github.pulpogato.common"

    // Almost Primitives
    val BOOLEAN: ClassName = ClassName.get(java.lang.Boolean::class.java)
    val INTEGER: ClassName = ClassName.get(Integer::class.java)
    val LONG: ClassName = ClassName.get(java.lang.Long::class.java)
    val FLOAT: ClassName = ClassName.get(java.lang.Float::class.java)
    val DOUBLE: ClassName = ClassName.get(java.lang.Double::class.java)
    val STRING: ClassName = ClassName.get(java.lang.String::class.java)
    val BIG_DECIMAL: ClassName = ClassName.get(BigDecimal::class.java)
    val BYTE_ARRAY: ArrayTypeName = ArrayTypeName.of(TypeName.BYTE)
    val URI: ClassName = ClassName.get(java.net.URI::class.java)
    val UUID: ClassName = ClassName.get(java.util.UUID::class.java)
    val VOID: ClassName = ClassName.get(Void::class.java)

    // Time types
    val EPOCH_TIME: TypeName =
        ClassName
            .get(OffsetDateTime::class.java)
            .annotated(jsonFormat(JsonFormat.Shape.NUMBER_INT))
    val LOCAL_DATE: TypeName =
        ClassName
            .get(LocalDate::class.java)
            .annotated(jsonFormat(JsonFormat.Shape.STRING, "yyyy-MM-dd"))
    val OFFSET_DATE_TIME: TypeName =
        ClassName
            .get(OffsetDateTime::class.java)
            .annotated(jsonFormat(JsonFormat.Shape.STRING, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
            .annotated(
                AnnotationSpec
                    .builder(ClassName.get("com.fasterxml.jackson.databind.annotation", "JsonDeserialize"))
                    .addMember("using", $$"$T.class", ClassName.get(COMMON_PACKAGE, "OffsetDateTimeDeserializer"))
                    .build(),
            )

    // Common Types
    val EMPTY_OBJECT: ClassName = ClassName.get(COMMON_PACKAGE, "EmptyObject")
    val EXCEPTION: ClassName = ClassName.get(java.lang.Exception::class.java)
    val OBJECT: ClassName = ClassName.get(Object::class.java)
    val STRING_OR_INTEGER: ClassName = ClassName.get(COMMON_PACKAGE, "StringOrInteger")
    val TODO: ClassName = ClassName.get(COMMON_PACKAGE, "Todo")

    // Utility types
    val LIST: ClassName = ClassName.get(List::class.java)
    val MAP: ClassName = ClassName.get(Map::class.java)
    val CODE_BUILDER: ClassName = ClassName.get(COMMON_PACKAGE, "CodeBuilder")

    // Common Parameterized Types
    val MAP_STRING_OBJECT: ParameterizedTypeName = ParameterizedTypeName.get(MAP, STRING, OBJECT)
    val SINGULAR_OR_PLURAL: ClassName = ClassName.get(COMMON_PACKAGE, "SingularOrPlural")
}