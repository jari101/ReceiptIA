package pt.receiptai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.receiptai.R
import pt.receiptai.domain.Money
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.components.BrandHeader
import pt.receiptai.ui.components.Dates
import pt.receiptai.ui.components.ExpiryBadge
import pt.receiptai.ui.components.ScreenPadding
import pt.receiptai.ui.components.SectionTitle
import pt.receiptai.ui.components.appViewModel

@Composable
fun HomeRoute(onImport: () -> Unit, onOpenRecords: () -> Unit, onOpenPantry: () -> Unit, onOpenStats: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(state, onImport, vm::loadDemoData, onOpenRecords, onOpenPantry, onOpenStats)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onImport: () -> Unit,
    onLoadDemo: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenPantry: () -> Unit,
    onOpenStats: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BrandHeader(Modifier.padding(top = 8.dp, bottom = 4.dp)) }
        item { ImportCard(onImport) }

        if (state.isEmpty && !state.loading) {
            item {
                AppCard {
                    Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.home_empty_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    OutlinedButton(onClick = onLoadDemo) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.home_load_demo))
                    }
                }
            }
            return@LazyColumn
        }

        item {
            AppCard(onClick = onOpenStats) {
                Text(stringResource(R.string.home_spent_this_month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(state.monthTotalCents), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    pluralStringResource(R.plurals.purchases_count, state.monthReceipts, state.monthReceipts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.home_alerts_title)) {
                TextButton(onClick = onOpenPantry) { Text(stringResource(R.string.home_see_pantry)) }
            }
        }
        if (state.alerts.isEmpty()) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.home_no_alerts), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            item {
                AppCard(onClick = onOpenPantry) {
                    state.alerts.forEachIndexed { i, alert ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(alert.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            ExpiryBadge(alert.daysLeft)
                        }
                    }
                }
            }
        }

        state.lastPurchase?.let { last ->
            item { SectionTitle(stringResource(R.string.home_last_purchase)) }
            item {
                AppCard(onClick = onOpenRecords) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(last.store, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Dates.dayMonth(last.date) + " · " + pluralStringResource(R.plurals.products_count, last.itemCount, last.itemCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(last.totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    Card(
        onClick = onImport,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_import_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.home_import_subtitle), style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, Modifier.size(40.dp))
                Icon(Icons.Outlined.AddCircle, null, Modifier.size(20.dp).align(Alignment.BottomEnd))
            }
        }
    }
}
