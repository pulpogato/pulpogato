package io.github.pulpogato.restcodegen.ext

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StringExtensionsTest {
    @Test
    fun testPascalCase() {
        assertThat("pascal-case".pascalCase()).isEqualTo("PascalCase")
        assertThat("pascal_case".pascalCase()).isEqualTo("PascalCase")
        assertThat("pascal case".pascalCase()).isEqualTo("PascalCase")
        assertThat("pascal.case".pascalCase()).isEqualTo("PascalCase")
        assertThat("pascal:case".pascalCase()).isEqualTo("PascalCase")
        assertThat("pascal/case".pascalCase()).isEqualTo("PascalCase")
    }

    @Test
    fun testCamelCase() {
        assertThat("camel-case".camelCase()).isEqualTo("camelCase")
        assertThat("camel_case".camelCase()).isEqualTo("camelCase")
        assertThat("camel case".camelCase()).isEqualTo("camelCase")
        assertThat("camel.case".camelCase()).isEqualTo("camelCase")
        assertThat("camel:case".camelCase()).isEqualTo("camelCase")
        assertThat("camel/case".camelCase()).isEqualTo("camelCase")
    }

    @Test
    fun testTrainCase() {
        assertThat("train-case".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("train_case".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("train case".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("train.case".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("train:case".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("1train:case".trainCase()).isEqualTo("_1TRAIN_CASE")
        assertThat("train'case".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("trainCase".trainCase()).isEqualTo("TRAIN_CASE")
        assertThat("trainHTTPCase".trainCase()).isEqualTo("TRAIN_HTTP_CASE")
        assertThat("trainHTTPCaseOne".trainCase()).isEqualTo("TRAIN_HTTP_CASE_ONE")
        assertThat("HTTPCase".trainCase()).isEqualTo("HTTP_CASE")
    }

    @Test
    fun testUnkeywordize() {
        assertThat("*".unkeywordize()).isEqualTo("asterisk")
        assertThat("+1".unkeywordize()).isEqualTo("plus-one")
        assertThat("-1".unkeywordize()).isEqualTo("minus-one")
        assertThat("/".unkeywordize()).isEqualTo("slash")
        assertThat("/docs".unkeywordize()).isEqualTo("slash-docs")
        assertThat("@timestamp".unkeywordize()).isEqualTo("timestamp")
        assertThat("default".unkeywordize()).isEqualTo("is-default")
        assertThat("package".unkeywordize()).isEqualTo("the-package")
        assertThat("private".unkeywordize()).isEqualTo("is-private")
        assertThat("protected".unkeywordize()).isEqualTo("is-protected")
        assertThat("public".unkeywordize()).isEqualTo("is-public")
        assertThat("reactions-+1".unkeywordize()).isEqualTo("reactions-plus-one")
        assertThat("reactions--1".unkeywordize()).isEqualTo("reactions-minus-one")
        assertThat("normal".unkeywordize()).isEqualTo("normal")
    }
}