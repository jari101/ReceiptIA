package pt.receiptai.parser

import pt.receiptai.domain.Money
import pt.receiptai.domain.parseQuantity
import java.text.Normalizer as JavaNormalizer
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Lê recibos em CSV (ex.: exportados de uma folha de cálculo ou do site do supermercado).
 * Formato recomendado (separador ";"):
 * ```
 * data;loja;produto;quantidade;preco
 * 2026-11-18;Mercado do Bairro;Leite meio gordo 1L;2;0,89
 * ```
 * "preco" é o preço unitário. Se existir uma coluna "total", é usada como total da linha.
 * Linhas com a mesma data + loja formam um só recibo.
 */
object CsvReceiptParser {

    private val dateCols = setOf("data", "date", "dia")
    private val storeCols = setOf("loja", "store", "supermercado", "comerciante", "estabelecimento", "shop")
    private val nameCols = setOf("produto", "artigo", "item", "descricao", "nome", "product", "name", "description")
    private val qtyCols = setOf("quantidade", "qtd", "qty", "quantity", "qtde")
    private val priceCols = setOf("preco", "preco_unitario", "preco unitario", "price", "unit_price", "pvp", "valor_unitario")
    private val totalCols = setOf("total", "valor", "total_linha", "amount", "line_total")

    private fun key(s: String) = JavaNormalizer.normalize(s.trim().lowercase(), JavaNormalizer.Form.NFD)
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").trim('"')

    fun parse(text: String): List<ParsedReceipt> {
        val rows = text.lines().filter { it.isNotBlank() }
        if (rows.size < 2) return emptyList()
        val sep = listOf(';', '\t', ',').maxBy { c -> rows.first().count { it == c } }
        val header = splitLine(rows.first(), sep).map(::key)

        fun idx(names: Set<String>) = header.indexOfFirst { it in names }
        val iDate = idx(dateCols)
        val iStore = idx(storeCols)
        val iName = idx(nameCols)
        val iQty = idx(qtyCols)
        val iPrice = idx(priceCols)
        val iTotal = idx(totalCols)
        require(iName >= 0 && (iPrice >= 0 || iTotal >= 0)) {
            "O CSV precisa de colunas \"produto\" e \"preco\" (ou \"total\")."
        }

        data class Row(val date: LocalDate?, val store: String, val item: ParsedItem)

        val parsed = rows.drop(1).mapNotNull { line ->
            val cols = splitLine(line, sep)
            fun col(i: Int) = cols.getOrNull(i)?.trim()?.trim('"').orEmpty()
            val name = col(iName).ifBlank { return@mapNotNull null }
            val qty = if (iQty >= 0) parseQuantity(col(iQty)) ?: 1.0 else 1.0
            val unit = if (iPrice >= 0) Money.parseToCents(col(iPrice)) else null
            val total = if (iTotal >= 0) Money.parseToCents(col(iTotal)) else null
            val lineTotal = total ?: unit?.let { (it * qty).roundToLong() } ?: return@mapNotNull null
            val unitPrice = unit ?: if (qty > 0) (lineTotal / qty).roundToLong() else lineTotal
            Row(
                date = if (iDate >= 0) DateParsing.find(col(iDate)) else null,
                store = if (iStore >= 0) col(iStore).ifBlank { "Loja desconhecida" } else "Loja desconhecida",
                item = ParsedItem(name, qty, unitPrice, lineTotal),
            )
        }

        return parsed.groupBy { it.date to it.store }.map { (k, group) ->
            ParsedReceipt(storeName = k.second, date = k.first, items = group.map { it.item })
        }
    }

    /** Divide uma linha CSV respeitando aspas: a;"b;c";d -> [a, b;c, d] */
    fun splitLine(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == sep && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
