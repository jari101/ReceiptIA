package pt.receiptai.parser

import java.time.LocalDate

/** Resultado da leitura de um recibo, antes de ser revisto e guardado. */
data class ParsedReceipt(
    val storeName: String,
    val date: LocalDate?,
    val items: List<ParsedItem>,
    /** Total impresso no recibo (se foi encontrado). */
    val totalCents: Long? = null,
) {
    val itemsTotalCents: Long get() = items.sumOf { it.totalCents }
}

data class ParsedItem(
    val name: String,
    val quantity: Double,
    val unitPriceCents: Long,
    val totalCents: Long,
)

/** Tipos de ficheiro que a app sabe ler. */
enum class ReceiptFileType { PDF, IMAGEM, HTML, TEXTO, CSV, JSON, XML, ZIP }
