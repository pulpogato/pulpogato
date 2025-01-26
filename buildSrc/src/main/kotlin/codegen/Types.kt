package codegen

import codegen.Annotations.jsonFormat
import com.palantir.javapoet.ArrayTypeName
import com.palantir.javapoet.ClassName
import com.palantir.javapoet.ParameterizedTypeName
import com.palantir.javapoet.TypeName

object Types {
    // Almost Primitives
    val BOOLEAN = ClassName.get("java.lang", "Boolean")
    val INTEGER = ClassName.get("java.lang", "Integer")
    val LONG = ClassName.get("java.lang", "Long")
    val FLOAT = ClassName.get("java.lang", "Float")
    val DOUBLE = ClassName.get("java.lang", "Double")
    val STRING = ClassName.get("java.lang", "String")
    val BIG_DECIMAL = ClassName.get("java.math", "BigDecimal")
    val BYTE_ARRAY = ArrayTypeName.of(TypeName.BYTE)
    val URI = ClassName.get("java.net", "URI")
    val UUID = ClassName.get("java.util", "UUID")
    val VOID = ClassName.get("java.lang", "Void")

    // Time types
    val EPOCH_TIME =
        ClassName.get("java.time", "OffsetDateTime")
            .annotated(jsonFormat("NUMBER_INT"))
    val LOCAL_DATE =
        ClassName.get("java.time", "LocalDate")
            .annotated(jsonFormat("STRING", "yyyy-MM-dd"))
    val OFFSET_DATE_TIME =
        ClassName.get("java.time", "OffsetDateTime")
            .annotated(jsonFormat("STRING", "yyyy-MM-dd'T'HH:mm:ssXXX"))

    // Common Types
    val EMPTY_OBJECT = ClassName.get("io.github.pulpogato.common", "EmptyObject")
    val OBJECT = ClassName.get("java.lang", "Object")
    val STRING_OBJECT_OR_INTEGER = ClassName.get("io.github.pulpogato.common", "StringObjectOrInteger")
    val STRING_OR_INTEGER = ClassName.get("io.github.pulpogato.common", "StringOrInteger")
    val STRING_OR_OBJECT = ClassName.get("io.github.pulpogato.common", "StringOrObject")
    val TODO = ClassName.get("io.github.pulpogato.common", "Todo")

    // Utility types
    val LIST = ClassName.get("java.util", "List")
    val MAP = ClassName.get("java.util", "Map")

    // Common Parameterized Types
    val LIST_OF_STRINGS = ParameterizedTypeName.get(LIST, ClassName.get("java.lang", "String"))
    val MAP_STRING_OBJECT = ParameterizedTypeName.get(MAP, ClassName.get("java.lang", "String"), ClassName.get("java.lang", "Object"))
}