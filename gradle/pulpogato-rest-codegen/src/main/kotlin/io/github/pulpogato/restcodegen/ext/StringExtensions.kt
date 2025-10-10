package io.github.pulpogato.restcodegen.ext

private var pascalCaseCache = mutableMapOf<String, String>()
private var trainCaseCache = mutableMapOf<String, String>()

/**
 * Converts a string to PascalCase.
 */
fun String.pascalCase() =
    pascalCaseCache.getOrPut(this) {
        this
            .replace(".", "-")
            .replace("_", "-")
            .replace(" ", "-")
            .replace(":", "-")
            .replace("/", "-")
            .split('-')
            .joinToString("") { it.replaceFirstChar { x -> x.uppercaseChar() } }
    }

/**
 * Converts a string to camelCase.
 */
fun String.camelCase() = this.pascalCase().replaceFirstChar { it.lowercaseChar() }

private val trainRegex = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
private val multipleHyphens = Regex("-+")

/**
 * Converts a string to TRAIN_CASE.
 */
fun String.trainCase() =
    trainCaseCache.getOrPut(this) {
        this
            .split(trainRegex)
            .joinToString("-")
            .replace(".", "-")
            .replace("_", "-")
            .replace("'", "-")
            .replace(" ", "-")
            .replace(":", "-")
            .replace("/", "-")
            .replace(multipleHyphens, "-")
            .split('-')
            .joinToString("_") { it.uppercase() }
            .let { if (it[0].isDigit()) "_$it" else it }
    }

/**
 * Turns known challenging enums and field names to ones that play well with java.
 */
fun String.unkeywordize() =
    when (this) {
        "*" -> "asterisk"
        "+1" -> "plus-one"
        "-1" -> "minus-one"
        "/" -> "slash"
        "/docs" -> "slash-docs"
        "@timestamp" -> "timestamp"
        "default" -> "is-default"
        "package" -> "the-package"
        "private" -> "is-private"
        "protected" -> "is-protected"
        "public" -> "is-public"
        "reactions-+1" -> "reactions-plus-one"
        "reactions--1" -> "reactions-minus-one"
        else -> this
    }