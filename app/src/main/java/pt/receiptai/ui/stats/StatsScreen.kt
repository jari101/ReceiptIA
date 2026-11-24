package pt.receiptai.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.receiptai.R
import pt.receiptai.domain.Money
import pt.receiptai.domain.StatsPeriod
import pt.receiptai.domain.TopItem
import pt.receiptai.domain.formatQuantity
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.components.Dates
import pt.receiptai.ui.components.EmptyState
import pt.receiptai.ui.components.ScreenPadding
import pt.receiptai.ui.components.ScreenTitle
import pt.receiptai.ui.components.SectionTitle
import pt.receiptai.ui.components.appViewModel
import pt.receiptai.ui.components.label
import pt.receiptai.ui.theme.LocalStatusColors

@Composable
fun StatsRoute() {
    val vm = appViewModel { StatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    StatsScreen(state, vm::setPeriod, vm::previous, vm::next)
}

@Composable
fun StatsScreen(state: StatsUiState, onPeriod: (StatsPeriod) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val summary = state.summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle(stringResource(R.string.stats_title), stringResource(R.string.stats_subtitle)) }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { onPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, StatsPeriod.entries.size),
                    ) { Text(p.label()) }
                }
            }
        }

        if (summary == null) return@LazyColumn

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stats_previous))
                }
                Text(
                    Dates.periodLabel(state.period, summary.from, summary.to),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onNext, enabled = state.canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stats_next))
                }
            }
        }

        item {
            AppCard {
                Text(
                    stringResource(
                        when (state.period) {
                            StatsPeriod.SEMANA -> R.string.stats_spent_week
                            StatsPeriod.MES -> R.string.stats_spent_month
                            StatsPeriod.ANO -> R.string.stats_spent_year
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Money.format(summary.totalCents), style = MaterialTheme.typography.displaySmall)
                summary.changePercent?.let { change -> ChangeRow(change, state.period) }
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.purchases_count, summary.receiptCount, summary.receiptCount) +
                        if (summary.receiptCount > 0) " · " + stringResource(R.string.stats_average, Money.format(summary.averagePerReceiptCents)) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                BarChart(summary.buckets)
            }
        }

        item { SectionTitle(stringResource(R.string.stats_top_title)) }
        if (summary.topItems.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.BarChart, stringResource(R.string.stats_empty_title), stringResource(R.string.stats_empty_message))
            }
        } else {
            item {
                AppCard {
                    val maxTimes = summary.topItems.maxOf { it.timesBought }
                    summary.topItems.forEachIndexed { i, top ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        TopItemRow(i + 1, top, maxTimes)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: Int, period: StatsPeriod) {
    val status = LocalStatusColors.current
    // Gastar MAIS é mau (vermelho); gastar menos é bom (verde).
    val up = change > 0
    val color = if (up) status.expired else status.ok
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(
                when (period) {
                    StatsPeriod.SEMANA -> R.string.stats_change_week
                    StatsPeriod.MES -> R.string.stats_change_month
                    StatsPeriod.ANO -> R.string.stats_change_year
                },
                (if (up) "+" else "") + change,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TopItemRow(rank: Int, item: TopItem, maxTimes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                pluralStringResource(R.plurals.times_bought, item.timesBought, item.timesBought) + " · " +
                    stringResource(R.string.stats_quantity, formatQuantity(Math.round(item.quantity * 100) / 100.0)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(
                    Modifier.fillMaxWidth(item.timesBought.toFloat() / maxTimes).height(6.dp)
                        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(Money.format(item.totalCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}
