package pt.receiptai.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Período escolhido na página de Estatísticas. */
enum class StatsPeriod { SEMANA, MES, ANO }

/** Tipo de gráfico escolhido no menu "Linha atual" (fica guardado no telemóvel). */
enum class ChartStyle { BARRAS, RETA, CURVA }

/** Uma compra simplificada (para cálculos que não precisam da base de dados). */
data class PurchaseLine(
    val receiptId: Long,
    val date: LocalDate,
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val totalCents: Long,
)

/**
 * Um recibo resumido: tudo o que o gráfico precisa para desenhar um ponto
 * e para mostrar o popup (loja, data, total e nº de produtos).
 */
data class ReceiptSummary(
    val id: Long,
    val date: LocalDate,
    val store: String,
    val totalCents: Long,
    val itemCount: Int,
)

/**
 * Uma barra do gráfico de barras: os dias [from]..[to] e os recibos desses dias.
 * Semana = 1 dia por barra; mês = blocos de 7 dias; ano = 1 mês por barra.
 */
data class Bucket(val label: String, val from: LocalDate, val to: LocalDate, val receipts: List<ReceiptSummary>) {
    val cents: Long get() = receipts.sumOf { it.totalCents }
}

data class TopItem(val name: String, val timesBought: Int, val quantity: Double, val totalCents: Long)

data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    val totalCents: Long,
    val previousTotalCents: Long,
    val receiptCount: Int,
    /** Os pontos do gráfico de linha: um por recibo, do mais antigo para o mais recente. */
    val points: List<ReceiptSummary>,
    /** As barras do gráfico de barras. */
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

    /**
     * Pontos do gráfico: um por recibo dentro do período, por ordem de compra.
     * Recibos do mesmo dia ficam lado a lado (o mais antigo, com id menor, primeiro).
     */
    fun chartPoints(range: ClosedRange<LocalDate>, receipts: List<ReceiptSummary>): List<ReceiptSummary> =
        receipts.filter { it.date in range }.sortedWith(compareBy<ReceiptSummary> { it.date }.thenBy { it.id })

    /** Todos os recibos com a MESMA data do ponto escolhido (é o que o popup mostra). */
    fun sameDay(points: List<ReceiptSummary>, index: Int): List<ReceiptSummary> {
        val day = points.getOrNull(index)?.date ?: return emptyList()
        return points.filter { it.date == day }
    }

    /** Barras do gráfico: semana = 7 dias; mês = blocos de 7 dias; ano = 12 meses. */
    fun buckets(period: StatsPeriod, range: ClosedRange<LocalDate>, receipts: List<ReceiptSummary>): List<Bucket> {
        val inRange = chartPoints(range, receipts)
        fun bucket(label: String, from: LocalDate, to: LocalDate) =
            Bucket(label, from, to, inRange.filter { it.date in from..to })
        return when (period) {
            StatsPeriod.SEMANA -> (0L..6L).map { i ->
                val day = range.start.plusDays(i)
                // "segunda" -> "Seg", "sábado" -> "Sáb" (cabe por baixo das barras)
                bucket(day.dayOfWeek.getDisplayName(TextStyle.FULL, Money.PT).take(3).replaceFirstChar { it.uppercase() }, day, day)
            }
            StatsPeriod.MES -> {
                val last = range.endInclusive.dayOfMonth
                (1..last step 7).map { startDay ->
                    val endDay = minOf(startDay + 6, last)
                    bucket("$startDay–$endDay", range.start.withDayOfMonth(startDay), range.start.withDayOfMonth(endDay))
                }
            }
            StatsPeriod.ANO -> (1..12).map { m ->
                val first = range.start.withMonth(m).withDayOfMonth(1)
                bucket(
                    java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Money.PT).trimEnd('.').replaceFirstChar { it.uppercase() },
                    first,
                    first.with(TemporalAdjusters.lastDayOfMonth()),
                )
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
        receipts: List<ReceiptSummary>,
        lines: List<PurchaseLine>,
        today: LocalDate = anchor,
    ): PeriodSummary {
        val current = range(period, anchor)
        val fullPrevious = range(period, shift(period, anchor, -1))
        // Comparação justa: se o período atual ainda não acabou (ex.: dia 19 do mês),
        // comparamos com os MESMOS dias do período anterior (dias 1 a 19).
        val previous = if (today in current) {
            val elapsed = java.time.temporal.ChronoUnit.DAYS.between(current.start, today)
            fullPrevious.start..minOf(fullPrevious.endInclusive, fullPrevious.start.plusDays(elapsed))
        } else fullPrevious
        val inCurrent = chartPoints(current, receipts)
        return PeriodSummary(
            from = current.start,
            to = current.endInclusive,
            totalCents = inCurrent.sumOf { it.totalCents },
            previousTotalCents = receipts.filter { it.date in previous }.sumOf { it.totalCents },
            receiptCount = inCurrent.size,
            points = inCurrent,
            buckets = buckets(period, current, receipts),
            topItems = topItems(lines.filter { it.date in current }),
        )
    }
}
