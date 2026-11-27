package pt.receiptai.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pt.receiptai.R
import pt.receiptai.domain.Money
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.components.BrandHeader
import pt.receiptai.ui.components.Dates
import pt.receiptai.ui.components.ExpiryBadge
import pt.receiptai.ui.components.ScreenPadding
import pt.receiptai.ui.components.SectionTitle
import pt.receiptai.ui.components.appViewModel
import pt.receiptai.ui.theme.EaseOutStrong

/** @param snackbar onde aparece a mensagem "… adicionado à lista de compras  ANULAR". */
@Composable
fun HomeRoute(
    onImport: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenPantry: () -> Unit,
    onOpenStats: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val addedMessage = stringResource(R.string.home_added_to_list) // "%1$s adicionado à lista de compras"
    val undo = stringResource(R.string.undo)
    HomeScreen(
        state, onImport, vm::loadDemoData, onOpenRecords, onOpenPantry, onOpenStats,
        onAddToShopping = { alert ->
            scope.launch {
                val id = vm.addToShopping(alert) ?: return@launch
                snackbar.currentSnackbarData?.dismiss() // uma mensagem de cada vez
                val result = snackbar.showSnackbar(addedMessage.format(alert.name), actionLabel = undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) vm.removeFromShopping(id)
            }
        },
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onImport: () -> Unit,
    onLoadDemo: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenPantry: () -> Unit,
    onOpenStats: () -> Unit,
    onAddToShopping: (AlertItem) -> Unit = {},
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
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(alert.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            ExpiryBadge(alert.daysLeft)
                            AddToListButton(alert, onAdd = { onAddToShopping(alert) })
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

/**
 * Botão 🛒 de um alerta. Depois de tocar, troca para ✓ ("já está na lista") com um
 * pequeno "pop" (cresce e aparece em 180 ms). Se carregares em "Anular", volta a ser 🛒.
 */
@Composable
private fun AddToListButton(alert: AlertItem, onAdd: () -> Unit) {
    AnimatedContent(
        targetState = alert.onList,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(180, easing = EaseOutStrong), initialScale = 0.6f)) togetherWith fadeOut(tween(90))
        },
        label = "carrinho",
    ) { onList ->
        if (onList) {
            // Mesmo tamanho do botão (48 dp), para a linha não "saltar".
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, stringResource(R.string.home_on_list, alert.name), tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            IconButton(onClick = onAdd) {
                Icon(Icons.Outlined.AddShoppingCart, stringResource(R.string.home_add_to_list, alert.name), tint = MaterialTheme.colorScheme.primary)
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
