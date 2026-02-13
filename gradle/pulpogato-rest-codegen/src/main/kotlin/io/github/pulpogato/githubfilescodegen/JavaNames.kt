package io.github.pulpogato.githubfilescodegen

import io.github.pulpogato.restcodegen.ext.camelCase
import io.github.pulpogato.restcodegen.ext.unkeywordize

private val JAVA_KEYWORDS =
    setOf(
        "boolean",
        "if",
    )

/**
 * Converts a JSON property name to a safe Java field name.
 * Handles reserved words and applies camelCase conversion.
 */
fun String.toSafeFieldName(): String {
    val fieldName = this.unkeywordize().camelCase()
    return if (fieldName in JAVA_KEYWORDS) "${fieldName}_" else fieldName
}