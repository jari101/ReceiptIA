package pt.receiptai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pt.receiptai.R
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.domain.Money
import pt.receiptai.domain.StatsPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Formatação de datas em português: "18 nov 2026", "sáb., 18 nov". */
object Dates {
    private val full = DateTimeFormatter.ofPattern("d MMM yyyy", Money.PT)
    private val dayMonth = DateTimeFormatter.ofPattern("EEE, d MMM", Money.PT)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Money.PT)
    private val dayShort = DateTimeFormatter.ofPattern("d MMM", Money.PT)
    private val input = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun full(d: LocalDate): String = full.format(d).replace(".", "")
    fun dayMonth(d: LocalDate): String = dayMonth.format(d).replace(".", "").replaceFirstChar { it.uppercase() }
    /** "18 nov" (por baixo do gráfico). */
    fun dayShort(d: LocalDate): String = dayShort.format(d).replace(".", "")
    fun monthYear(d: LocalDate): String = monthYear.format(d).replaceFirstChar { it.uppercase() }
    fun input(d: LocalDate): String = input.format(d)
    fun parseInput(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), input) }.getOrNull()

    /** Título do período nas estatísticas: "17 – 23 nov 2026", "Novembro 2026", "2026". */
    fun periodLabel(period: StatsPeriod, from: LocalDate, to: LocalDate): String = when (period) {
        StatsPeriod.SEMANA -> "${from.dayOfMonth} ${if (from.month != to.month) DateTimeFormatter.ofPattern("MMM", Money.PT).format(from).replace(".", "") + " " else ""}– ${full(to)}"
        StatsPeriod.MES -> monthYear(from)
        StatsPeriod.ANO -> from.year.toString()
    }
}

@Composable
fun MealType.label(): String = stringResource(
    when (this) {
        MealType.PEQUENO_ALMOCO -> R.string.meal_breakfast
        MealType.LANCHE -> R.string.meal_snack
        MealType.ALMOCO -> R.string.meal_lunch
        MealType.JANTAR -> R.string.meal_dinner
    },
)

@Composable
fun DietTag.label(): String = stringResource(
    when (this) {
        DietTag.DIABETICOS -> R.string.diet_diabetic
        DietTag.GINASIO -> R.string.diet_gym
        DietTag.SEM_GLUTEN -> R.string.diet_gluten_free
        DietTag.SEM_LACTOSE -> R.string.diet_lactose_free
        DietTag.VEGETARIANO -> R.string.diet_vegetarian
    },
)

@Composable
fun StatsPeriod.label(): String = stringResource(
    when (this) {
        StatsPeriod.SEMANA -> R.string.period_week
        StatsPeriod.MES -> R.string.period_month
        StatsPeriod.ANO -> R.string.period_year
    },
)

/** "Expirou há 2 dias" / "Expira hoje" / "Expira amanhã" / "Expira em 5 dias" */
@Composable
fun expiryText(daysLeft: Long): String = when {
    daysLeft < 0 -> pluralStringResource(R.plurals.expired_days_ago, (-daysLeft).toInt(), (-daysLeft).toInt())
    daysLeft == 0L -> stringResource(R.string.expires_today)
    daysLeft == 1L -> stringResource(R.string.expires_tomorrow)
    else -> pluralStringResource(R.plurals.expires_in_days, daysLeft.toInt(), daysLeft.toInt())
}
