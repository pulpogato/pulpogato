package io.github.pulpogato.restcodegen

import io.swagger.v3.oas.models.OpenAPI

class Context {
    lateinit var openAPI: OpenAPI

    lateinit var version: String

    private val schemaStack = mutableListOf<String>()

    private fun getSchemaStackRef() = schemaStack.joinToString("/") { it.replace("/", "~1") }

    fun <T> withSchemaStack(
        vararg element: String,
        block: () -> T,
    ): T {
        val backupStack = schemaStack.toList()
        if (element.isNotEmpty() && element.first() == "#") {
            schemaStack.clear()
        }
        if (element.isNotEmpty() && schemaStack.isNotEmpty() && schemaStack.last() == element.first()) {
            schemaStack.removeLast()
        }
        schemaStack.addAll(element)
        val returnValue = block()
        schemaStack.clear()
        schemaStack.addAll(backupStack)
        return returnValue
    }

    companion object {
        val instance = ThreadLocal.withInitial { Context() }!!

        fun <T> withSchemaStack(
            vararg element: String,
            block: () -> T,
        ): T = instance.get().withSchemaStack(*element, block = block)

        fun getSchemaStackRef() = instance.get().getSchemaStackRef()
    }
}