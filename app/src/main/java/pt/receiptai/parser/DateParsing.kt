package pt.receiptai.parser

import java.time.LocalDate

/** Datas em recibos portugueses: 18-11-2026, 18/11/26, 18.11.2026 ou 2026-11-18. */
object DateParsing {
    private val iso = "(\\d{4})-(\\d{1,2})-(\\d{1,2})".toRegex()
    private val pt = "(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})(?!\\d)".toRegex()

    fun find(text: String): LocalDate? {
        iso.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            build(y.toInt(), mo.toInt(), d.toInt())?.let { return it }
        }
        for (m in pt.findAll(text)) {
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
            build(year, mo.toInt(), d.toInt())?.let { return it }
        }
        return null
    }

    private fun build(y: Int, m: Int, d: Int): LocalDate? =
        if (y !in 2000..2100) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
