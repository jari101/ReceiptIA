package pt.receiptai.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Período escolhido na página de Estatísticas. */
enum class StatsPeriod { SEMANA, MES, ANO }

/** Uma compra simplificada (para cálculos que não precisam da base de dados). */
data class PurchaseLine(
    val receiptId: Long,
    val date: LocalDate,
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val totalCents: Long,
)

data class Bucket(val label: String, val cents: Long)

data class TopItem(val name: String, val timesBought: Int, val quantity: Double, val totalCents: Long)

data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    val totalCents: Long,
    val previousTotalCents: Long,
    val receiptCount: Int,
    val buckets: List<Bucket>,
    val topItems: List<TopItem>,
) {
    /** Variação em % face ao período anterior (null se não houver dados antes). */
    val changePercent: Int?
        get() = if (previousTotalCents <= 0) null
        else Math.round((totalCents - previousTotalCents) * 100.0 / previousTotalCents).toInt()

    val averagePerReceiptCents: Long get() = if (receiptCount == 0) 0 else totalCents / receiptCount
}

/** Contas das estatísticas: funções "puras", fáceis de testar. */
object StatsCalculator {

    fun range(period: StatsPeriod, anchor: LocalDate): ClosedRange<LocalDate> = when (period) {
        StatsPeriod.SEMANA -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            start..start.plusDays(6)
        }
        StatsPeriod.MES -> anchor.withDayOfMonth(1)..anchor.with(TemporalAdjusters.lastDayOfMonth())
        StatsPeriod.ANO -> anchor.withDayOfYear(1)..anchor.with(TemporalAdjusters.lastDayOfYear())
    }

    fun shift(period: StatsPeriod, anchor: LocalDate, delta: Long): LocalDate = when (period) {
        StatsPeriod.SEMANA -> anchor.plusWeeks(delta)
        StatsPeriod.MES -> anchor.plusMonths(delta)
        StatsPeriod.ANO -> anchor.plusYears(delta)
    }

    /** Barras do gráfico: semana = 7 dias; mês = blocos de 7 dias; ano = 12 meses. */
    fun buckets(period: StatsPeriod, range: ClosedRange<LocalDate>, receipts: List<Pair<LocalDate, Long>>): List<Bucket> {
        val inRange = receipts.filter { it.first in range }
        return when (period) {
            StatsPeriod.SEMANA -> (0L..6L).map { i ->
                val day = range.start.plusDays(i)
                Bucket(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Money.PT).trimEnd('.').replaceFirstChar { it.uppercase() },
                    inRange.filter { it.first == day }.sumOf { it.second })
            }
            StatsPeriod.MES -> {
                val last = range.endInclusive.dayOfMonth
                (1..last step 7).map { startDay ->
                    val endDay = minOf(startDay + 6, last)
                    Bucket("$startDay–$endDay", inRange.filter { it.first.dayOfMonth in startDay..endDay }.sumOf { it.second })
                }
            }
            StatsPeriod.ANO -> (1..12).map { m ->
                val month = java.time.Month.of(m)
                Bucket(month.getDisplayName(TextStyle.SHORT, Money.PT).trimEnd('.').replaceFirstChar { it.uppercase() },
                    inRange.filter { it.first.monthValue == m }.sumOf { it.second })
            }
        }
    }

    /** Produtos comprados mais vezes (nº de compras diferentes onde aparecem). */
    fun topItems(lines: List<PurchaseLine>, limit: Int = 5): List<TopItem> =
        lines.groupBy { it.normalizedName.ifBlank { it.name.lowercase() } }
            .map { (_, group) ->
                val displayName = group.groupingBy { it.name }.eachCount().maxBy { it.value }.key
                TopItem(
                    name = displayName,
                    timesBought = group.map { it.receiptId }.distinct().size,
                    quantity = group.sumOf { it.quantity },
                    totalCents = group.sumOf { it.totalCents },
                )
            }
            .sortedWith(compareByDescending<TopItem> { it.timesBought }.thenByDescending { it.totalCents })
            .take(limit)

    fun summarize(
        period: StatsPeriod,
        anchor: LocalDate,
        receipts: List<Pair<LocalDate, Long>>,
        lines: List<PurchaseLine>,
    ): PeriodSummary {
        val current = range(period, anchor)
        val previous = range(period, shift(period, anchor, -1))
        val inCurrent = receipts.filter { it.first in current }
        return PeriodSummary(
            from = current.start,
            to = current.endInclusive,
            totalCents = inCurrent.sumOf { it.second },
            previousTotalCents = receipts.filter { it.first in previous }.sumOf { it.second },
            receiptCount = inCurrent.size,
            buckets = buckets(period, current, receipts),
            topItems = topItems(lines.filter { it.date in current }),
        )
    }
}
