package pt.receiptai.parser

import pt.receiptai.domain.Money
import pt.receiptai.domain.parseQuantity
import java.text.Normalizer as JavaNormalizer
import kotlin.math.roundToLong

/**
 * Lê o TEXTO de um recibo (vindo do OCR, de um PDF ou de uma página HTML)
 * e tenta descobrir a loja, a data, os produtos e o total.
 *
 * Exemplo de recibo português:
 * ```
 * MERCADO DO BAIRRO
 * 18-11-2026 18:42
 * LEITE MEIO GORDO 1L      C   0,89
 * IOGURTE NATURAL
 *   2 X 1,49                   2,98
 * BANANA
 *   0,850 kg x 1,19 EUR/kg     1,01
 * TOTAL                        4,88
 * ```
 */
object ReceiptTextParser {

    /** Preço no fim da linha, opcionalmente seguido de "€", "EUR" e do código de IVA ("A", "C", "23%"). */
    private val trailingPrice =
        "(-?\\d{1,5}[.,]\\d{2})\\s*(?:€|eur)?\\s*(?:[a-d]|\\(?\\d{1,2}\\s?%\\)?)?\\s*$".toRegex(RegexOption.IGNORE_CASE)

    /** "2 X 1,49" ou "0,850 kg x 1,19 EUR/kg" (quantidade × preço unitário). */
    private val quantityLine =
        "^(\\d+(?:[.,]\\d{1,3})?)\\s*(?:un|uni|kg|g)?\\s*[x×*]\\s*(\\d+[.,]\\d{2})".toRegex(RegexOption.IGNORE_CASE)

    /** Mesma ideia, mas com o nome antes: "IOGURTE 2 X 1,49 2,98". */
    private val inlineQuantity =
        "\\s(\\d+(?:[.,]\\d{1,3})?)\\s*(?:un|uni|kg)?\\s*[x×*]\\s*(\\d+[.,]\\d{2})(?:\\s*(?:€|eur)(?:/kg)?)?".toRegex(RegexOption.IGNORE_CASE)

    private val totalLine = "^(total|a pagar|total a pagar|valor a pagar|total eur)\\b".toRegex(RegexOption.IGNORE_CASE)

    /** Linhas que não são produtos (pagamento, impostos, cabeçalhos...). Comparadas como palavras inteiras. */
    private val ignoreWords = listOf(
        "subtotal", "sub-total", "iva", "nif", "contribuinte", "multibanco", "troco", "numerario", "dinheiro",
        "cartao", "visa", "mastercard", "pagamento", "pago", "obrigado", "volte sempre", "atendido", "operador",
        "fatura", "factura", "fatura simplificada", "recibo", "talao", "documento", "original", "processado",
        "certificado", "www", "http", "https", "tel", "telefone", "morada", "rua", "codigo postal",
        "base tributavel", "taxa", "incidencia", "poupanca", "saldo", "pontos", "cliente", "data:", "hora:",
        "artigos", "qtd", "descricao", "atcud", "mbway", "mb way", "entregue", "a transportar", "n.º", "nº",
    )
    private val ignoreRegex = ignoreWords.joinToString("|", prefix = "(?<![a-z0-9])(", postfix = ")(?![a-z0-9])") {
        Regex.escape(it)
    }.toRegex()

    private fun plain(s: String): String =
        JavaNormalizer.normalize(s.lowercase(), JavaNormalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

    private fun isIgnored(line: String): Boolean = ignoreRegex.containsMatchIn(plain(line))

    private fun hasLetters(s: String) = s.count { it.isLetter() } >= 2

    /** Limpa o nome do produto: tira códigos de IVA, códigos de barras, asteriscos... */
    private fun cleanName(raw: String): String = raw.trim()
        .replace("^[\\s*#>(\\[]*[a-d][)\\]]\\s+".toRegex(RegexOption.IGNORE_CASE), "") // "(C) LEITE"
        .replace("^\\d{5,}\\s+".toRegex(), "") // código do artigo
        .replace("\\s+[a-d]$".toRegex(RegexOption.IGNORE_CASE), "") // "LEITE C"
        .replace("[*#]+".toRegex(), "")
        .replace("\\s{2,}".toRegex(), " ")
        .trim(' ', '-', '.', ':')

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val store = lines.take(5).firstOrNull { line ->
            hasLetters(line) && !isIgnored(line) && trailingPrice.find(line) == null &&
                line.count { it.isDigit() } < line.length / 3
        }?.let(::titleCase) ?: "Loja desconhecida"

        val date = DateParsing.find(text)

        val items = mutableListOf<ParsedItem>()
        var pendingName: String? = null
        var total: Long? = null

        for (line in lines) {
            if (totalLine.containsMatchIn(plain(line)) && !plain(line).startsWith("subtotal")) {
                trailingPrice.find(line)?.let { total = Money.parseToCents(it.groupValues[1]) }
                if (total != null) break // depois do TOTAL já não há produtos
                continue
            }
            if (isIgnored(line)) { pendingName = null; continue }

            val qty = quantityLine.find(line)
            if (qty != null) {
                val quantity = parseQuantity(qty.groupValues[1]) ?: 1.0
                val unit = Money.parseToCents(qty.groupValues[2]) ?: 0
                val rest = line.substring(qty.range.last + 1)
                val lineTotal = trailingPrice.find(rest)?.let { Money.parseToCents(it.groupValues[1]) }
                    ?: (quantity * unit).roundToLong()
                val name = pendingName
                if (name != null) {
                    items += ParsedItem(name, quantity, unit, lineTotal)
                    pendingName = null
                } else if (items.isNotEmpty()) {
                    // A linha da quantidade vem DEPOIS do produto que já tinha preço.
                    val last = items.removeAt(items.lastIndex)
                    items += last.copy(quantity = quantity, unitPriceCents = unit, totalCents = lineTotal)
                }
                continue
            }

            val price = trailingPrice.find(line)
            if (price != null) {
                val cents = Money.parseToCents(price.groupValues[1]) ?: continue
                var namePart = line.substring(0, price.range.first)
                var quantity = 1.0
                var unit = cents
                inlineQuantity.find(namePart)?.let { m ->
                    quantity = parseQuantity(m.groupValues[1]) ?: 1.0
                    unit = Money.parseToCents(m.groupValues[2]) ?: cents
                    namePart = namePart.removeRange(m.range)
                }
                val name = cleanName(namePart)
                when {
                    cents < 0 -> if (items.isNotEmpty()) { // desconto: abate ao último produto
                        val last = items.removeAt(items.lastIndex)
                        items += last.copy(totalCents = (last.totalCents + cents).coerceAtLeast(0))
                    }
                    hasLetters(name) -> items += ParsedItem(titleCase(name), quantity, unit, cents)
                    pendingName != null -> items += ParsedItem(pendingName, 1.0, cents, cents)
                }
                pendingName = null
                continue
            }

            // Linha só com texto: pode ser o nome de um produto cujo preço vem na linha seguinte.
            pendingName = if (hasLetters(line) && line != lines.firstOrNull()) titleCase(cleanName(line)) else null
        }

        return ParsedReceipt(store, date, items, total)
    }

    /** "LEITE MEIO GORDO" -> "Leite meio gordo" */
    fun titleCase(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return t
        val isShouting = t.count { it.isUpperCase() } > t.count { it.isLowerCase() }
        val base = if (isShouting) t.lowercase() else t
        return base.replaceFirstChar { it.uppercase() }
    }
}
