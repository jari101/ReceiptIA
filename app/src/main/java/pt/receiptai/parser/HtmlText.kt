package pt.receiptai.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import org.jsoup.safety.Safelist

/** Recibos em páginas web (HTML) -> texto, mantendo cada linha da tabela numa linha (produto ... preço). */
object HtmlText {
    fun toText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.outputSettings(Document.OutputSettings().prettyPrint(false))
        doc.select("script, style, head").remove()
        doc.select("br").after("\\n")
        doc.select("p, div, tr, li, h1, h2, h3, h4, table").before("\\n")
        doc.select("td, th").append("   ")
        val cleaned = Jsoup.clean(doc.html(), "", Safelist.none(), Document.OutputSettings().prettyPrint(false))
        return Parser.unescapeEntities(cleaned.replace("\\n", "\n"), false)
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }
}
