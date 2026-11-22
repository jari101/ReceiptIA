package pt.receiptai.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ShoppingSuggestion(
    val name: String,
    val normalizedName: String,
    val averageIntervalDays: Double,
    val daysSinceLast: Long,
)

/**
 * Lista de compras automática (Cartão de Identidade, ponto 3):
 * se um produto foi comprado em pelo menos [minPurchases] dias diferentes e já passou
 * quase o intervalo médio entre compras, sugerimos comprá-lo outra vez.
 */
object ShoppingListPlanner {

    fun suggest(
        history: List<PurchaseLine>,
        today: LocalDate,
        minPurchases: Int = 3,
        factor: Double = 0.9,
    ): List<ShoppingSuggestion> =
        history.groupBy { it.normalizedName.ifBlank { it.name.lowercase() } }
            .mapNotNull { (key, lines) ->
                val dates = lines.map { it.date }.distinct().sorted()
                if (dates.size < minPurchases) return@mapNotNull null
                val intervals = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
                val average = intervals.average()
                val since = ChronoUnit.DAYS.between(dates.last(), today)
                if (since < factor * average) return@mapNotNull null
                val displayName = lines.groupingBy { it.name }.eachCount().maxBy { it.value }.key
                ShoppingSuggestion(displayName, key, average, since)
            }
            // Os mais "atrasados" primeiro.
            .sortedByDescending { it.daysSinceLast / it.averageIntervalDays.coerceAtLeast(1.0) }
}
