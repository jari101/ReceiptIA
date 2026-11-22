package pt.receiptai.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import pt.receiptai.domain.Money
import kotlin.math.roundToLong

/**
 * Lê recibos em JSON. O formato oficial é o "receiptai-v1" (ver docs/formato-recibo.md),
 * que é também o que o robô ReceiptAI envia para a app:
 * ```json
 * {
 *   "formato": "receiptai-v1",
 *   "loja": "Mercado do Bairro",
 *   "data": "2026-11-18",
 *   "total": 4.88,
 *   "itens": [ { "nome": "Leite meio gordo 1L", "quantidade": 2, "preco_unitario": 0.89, "total": 1.78 } ]
 * }
 * ```
 * Também aceita uma lista de recibos, {"recibos": [...]} e chaves em inglês (store, date, items...).
 */
object JsonReceiptParser {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(text: String): List<ParsedReceipt> {
        val root = json.parseToJsonElement(text)
        val receipts: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> (root.field("recibos", "receipts") as? JsonArray)?.toList() ?: listOf(root)
            else -> emptyList()
        }
        return receipts.filterIsInstance<JsonObject>().map(::parseReceipt).filter { it.items.isNotEmpty() }
    }

    private fun parseReceipt(o: JsonObject): ParsedReceipt {
        val items = (o.field("itens", "items", "produtos", "linhas") as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .mapNotNull { item ->
                val name = item.text("nome", "name", "produto", "descricao", "description") ?: return@mapNotNull null
                val qty = item.number("quantidade", "quantity", "qtd", "qty") ?: 1.0
                val unit = item.money("preco_unitario", "preco", "unit_price", "price")
                val total = item.money("total", "valor", "amount", "line_total")
                    ?: unit?.let { (it * qty).roundToLong() } ?: return@mapNotNull null
                ParsedItem(name, qty, unit ?: (total / qty).roundToLong(), total)
            }
        return ParsedReceipt(
            storeName = o.text("loja", "store", "comerciante", "merchant") ?: "Loja desconhecida",
            date = o.text("data", "date")?.let(DateParsing::find),
            items = items,
            totalCents = o.money("total", "valor_total", "total_amount"),
        )
    }

    private fun JsonObject.field(vararg names: String): JsonElement? = names.firstNotNullOfOrNull { this[it] }
    private fun JsonObject.text(vararg names: String): String? =
        (field(*names) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.number(vararg names: String): Double? {
        val p = field(*names) as? JsonPrimitive ?: return null
        return p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
    }
    /** Preços podem vir como número (0.89) ou texto ("0,89 €"). */
    private fun JsonObject.money(vararg names: String): Long? {
        val p = field(*names) as? JsonPrimitive ?: return null
        return if (p.isString) p.contentOrNull?.let(Money::parseToCents) else p.doubleOrNull?.let(Money::fromDecimal)
    }
}
