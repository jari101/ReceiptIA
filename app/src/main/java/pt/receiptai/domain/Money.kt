package pt.receiptai.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Dinheiro é sempre guardado em CÊNTIMOS (Long) para evitar erros de arredondamento
 * (ex.: 0.1 + 0.2 = 0.30000000000000004 com Double).
 */
object Money {
    val PT: Locale = Locale.forLanguageTag("pt-PT")

    /** 1234 -> "12,34 €" */
    fun format(cents: Long): String = NumberFormat.getCurrencyInstance(PT).apply {
        currency = java.util.Currency.getInstance("EUR")
    }.format(BigDecimal(cents).movePointLeft(2))

    /** "12,34" / "12.34" / "1.234,56" / "-0,50" -> cêntimos; null se não for um número. */
    fun parseToCents(text: String): Long? {
        var t = text.trim().replace("€", "").replace("EUR", "", ignoreCase = true).replace(" ", "")
        if (t.isEmpty()) return null
        // Se tiver vírgula e ponto, o último é o separador decimal.
        val lastComma = t.lastIndexOf(',')
        val lastDot = t.lastIndexOf('.')
        t = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 -> t.replace(',', '.')
            else -> t
        }
        return t.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()
    }

    fun fromDecimal(value: Double): Long =
        BigDecimal.valueOf(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

    /** Cêntimos -> texto editável "12,34" (sem símbolo). */
    fun toPlain(cents: Long): String = BigDecimal(cents).movePointLeft(2).setScale(2).toPlainString().replace('.', ',')
}

/** Quantidades: "2" / "0,850" -> Double. */
fun parseQuantity(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** 2.0 -> "2"; 0.85 -> "0,85" */
fun formatQuantity(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString()
    else BigDecimal.valueOf(q).stripTrailingZeros().toPlainString().replace('.', ',')
