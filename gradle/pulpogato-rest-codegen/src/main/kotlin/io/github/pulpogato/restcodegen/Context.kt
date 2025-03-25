package io.github.pulpogato.restcodegen

import io.swagger.v3.oas.models.OpenAPI

class Context {
    lateinit var openAPI: OpenAPI

    lateinit var version: String

    val schemaStack = mutableListOf<String>()

    private fun getSchemaStackRef() = schemaStack.joinToString("/") { it.replace("/", "~1") }

    inline fun <T> withSchemaStack(
        vararg elements: String,
        block: () -> T,
    ): T {
        val backupStack = schemaStack.toList()
        if (elements.isNotEmpty() && elements.first() == "#") {
            schemaStack.clear()
        }
        if (elements.isNotEmpty() && schemaStack.isNotEmpty() && schemaStack.last() == elements.first()) {
            schemaStack.removeLast()
        }
        schemaStack.addAll(elements)
        val returnValue = block()
        schemaStack.clear()
        schemaStack.addAll(backupStack)
        return returnValue
    }

    companion object {
        val instance = ThreadLocal.withInitial { Context() }!!

        inline fun <T> withSchemaStack(
            vararg element: String,
            block: () -> T,
        ): T = instance.get().withSchemaStack(*element, block = block)

        fun getSchemaStackRef() = instance.get().getSchemaStackRef()
    }
}