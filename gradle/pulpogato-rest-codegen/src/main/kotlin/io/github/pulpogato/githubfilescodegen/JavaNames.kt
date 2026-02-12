package io.github.pulpogato.githubfilescodegen

import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.unkeywordize

private val JAVA_KEYWORDS =
    setOf(
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "try",
        "void",
        "volatile",
        "while",
    )

/**
 * Converts a JSON property name to a safe Java field name.
 * Handles reserved words and applies camelCase conversion.
 */
fun String.toSafeFieldName(): String {
    val fieldName = this.unkeywordize().camelCase()
    return if (fieldName in JAVA_KEYWORDS) "${fieldName}_" else fieldName
}