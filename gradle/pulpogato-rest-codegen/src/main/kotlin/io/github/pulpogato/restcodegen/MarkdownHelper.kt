package io.github.pulpogato.restcodegen

import org.apache.commons.text.WordUtils
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object MarkdownHelper {
    private val parser: Parser =
        Parser
            .builder()
            .extensions(listOf(TablesExtension.create()))
            .build()

    private val renderer: HtmlRenderer =
        HtmlRenderer
            .builder()
            .extensions(listOf(TablesExtension.create()))
            .build()

    private val newParagraphRegex = Regex("</p>\\s*<p>")

    /**
     * Converts a Markdown string to an HTML string.
     *
     * @param md The Markdown string to convert.
     * @return The HTML string.
     */
    fun mdToHtml(md: String?): String {
        if (md == null) {
            return ""
        }
        val document = parser.parse(md)
        return renderer
            .render(document)
            .replace("*/", "*&#47;")
            .replace(newParagraphRegex, "\n<br/>\n")
            .replace("<p>", "")
            .replace("</p>", "")
            .replace(" <a href=", "\n<a href=")
            .replace("&quot;<a href=", "\n&quot;<a href=")
            .replace("</a> ", "</a>\n")
            .replace("</a>. ", "</a>.\n")
            .replace("</a>, ", "</a>,\n")
            .replace("</a>&quot; ", "</a>&quot;\n")
            .split("\n")
            .dropLastWhile { it.isBlank() }
            .joinToString("\n") {
                when {
                    it.contains("<a href") -> it
                    else -> WordUtils.wrap(it, 120)
                }
            }.trim()
    }
}