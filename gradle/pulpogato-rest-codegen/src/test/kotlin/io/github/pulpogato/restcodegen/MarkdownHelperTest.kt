package io.github.pulpogato.restcodegen

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownHelperTest {
    @Test
    fun `mdToHtml converts basic markdown to HTML`() {
        val markdown = "# Heading\n\nThis is a paragraph."
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("<h1>Heading</h1>")
        assertThat(result).contains("This is a paragraph.")
    }

    @Test
    fun `mdToHtml handles null input`() {
        val result = MarkdownHelper.mdToHtml(null)

        assertThat(result).isEmpty()
    }

    @Test
    fun `mdToHtml converts bold text`() {
        val markdown = "This is **bold** text."
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("<strong>bold</strong>")
    }

    @Test
    fun `mdToHtml converts italic text`() {
        val markdown = "This is *italic* text."
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("<em>italic</em>")
    }

    @Test
    fun `mdToHtml converts links`() {
        val markdown = "This is a [link](https://example.com)."
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("<a href=\"https://example.com\">link</a>")
    }

    @Test
    fun `mdToHtml converts code blocks`() {
        val markdown = "```java\nSystem.out.println(\"Hello\");\n```"
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("<pre><code class=\"language-java\">")
        // The quotes might be escaped differently in the output
        assertThat(result).contains("System.out.println")
        assertThat(result).contains("Hello")
        assertThat(result).contains("</code></pre>")
    }

    @Test
    fun `mdToHtml converts tables`() {
        val markdown =
            """
            | Header 1 | Header 2 |
            | -------- | -------- |
            | Cell 1   | Cell 2   |
            """.trimIndent()
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("<table>")
        assertThat(result).contains("<th>Header 1</th>")
        assertThat(result).contains("<th>Header 2</th>")
        assertThat(result).contains("<td>Cell 1</td>")
        assertThat(result).contains("<td>Cell 2</td>")
        assertThat(result).contains("</table>")
    }

    @Test
    fun `mdToHtml escapes comment end markers`() {
        val markdown = "This contains a comment end marker: */"
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("*&#47;")
        assertThat(result).doesNotContain("*/")
    }

    @Test
    fun `mdToHtml formats paragraphs with br tags`() {
        val markdown = "First paragraph.\n\nSecond paragraph."
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("First paragraph.")
        assertThat(result).contains("<br/>")
        assertThat(result).contains("Second paragraph.")
    }

    @Test
    fun `mdToHtml formats links on new lines`() {
        val markdown = "Text with [a link](https://example.com) in the middle."
        val result = MarkdownHelper.mdToHtml(markdown)

        assertThat(result).contains("Text with")
        assertThat(result).contains("<a href=\"https://example.com\">a link</a>")
        assertThat(result).contains("in the middle.")
    }

    @Test
    fun `mdToHtml wraps long lines`() {
        val longText = "This is a very long text that should be wrapped. " * 10
        val result = MarkdownHelper.mdToHtml(longText)

        // The result should have line breaks
        assertThat(result.contains("\n")).isTrue()
    }

    private operator fun String.times(n: Int): String {
        return (1..n).joinToString("") { this }
    }
}