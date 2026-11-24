package pt.receiptai.ui.records

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.receiptai.R
import pt.receiptai.data.db.ReceiptWithItems
import pt.receiptai.domain.Money
import pt.receiptai.domain.formatQuantity
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.components.Dates
import pt.receiptai.ui.components.EmptyState
import pt.receiptai.ui.components.ScreenPadding
import pt.receiptai.ui.components.ScreenTitle
import pt.receiptai.ui.components.appViewModel
import pt.receiptai.ui.theme.EaseOutStrong

@Composable
fun RecordsRoute() {
    val vm = appViewModel { RecordsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    RecordsScreen(state, vm::setQuery, vm::toggle, vm::delete)
}

@Composable
fun RecordsScreen(state: RecordsUiState, onQuery: (String) -> Unit, onToggle: (Long) -> Unit, onDelete: (Long) -> Unit) {
    var toDelete by remember { mutableStateOf<ReceiptWithItems?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScreenTitle(
                stringResource(R.string.records_title),
                pluralStringResource(R.plurals.purchases_saved, state.totalReceipts, state.totalReceipts),
            )
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.records_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        }

        if (state.groups.isEmpty() && !state.loading) {
            item {
                EmptyState(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    stringResource(if (state.query.isBlank()) R.string.records_empty_title else R.string.records_no_results),
                    stringResource(if (state.query.isBlank()) R.string.records_empty_message else R.string.records_no_results_message),
                )
            }
        }

        state.groups.forEach { group ->
            item(key = "m-${group.month}") {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
                    Text(Dates.monthYear(group.month.atDay(1)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(group.totalCents), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(group.receipts, key = { it.receipt.id }) { r ->
                ReceiptCard(r, expanded = r.receipt.id in state.expanded, onToggle = { onToggle(r.receipt.id) }, onDelete = { toDelete = r })
            }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.records_delete_title)) },
            text = { Text(stringResource(R.string.records_delete_message, r.receipt.storeName, Dates.full(r.receipt.purchasedOn))) },
            confirmButton = {
                TextButton(onClick = { onDelete(r.receipt.id); toDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Uma compra. Ao tocar, abre o "menu" com todos os produtos comprados. */
@Composable
fun ReceiptCard(r: ReceiptWithItems, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200, easing = EaseOutStrong), label = "seta")
    val stateText = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    AppCard(
        onClick = onToggle,
        modifier = Modifier.semantics { role = Role.Button; stateDescription = stateText },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.receipt.storeName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    Dates.dayMonth(r.receipt.purchasedOn) + " · " + pluralStringResource(R.plurals.products_count, r.items.size, r.items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(r.receipt.totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Abertura do menu: 200 ms com ease-out forte (skill animate).
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220, easing = EaseOutStrong)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = EaseOutStrong)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                r.items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${formatQuantity(item.quantity)} × ${Money.format(item.unitPriceCents)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(item.totalCents), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.records_delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.total), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.size(8.dp))
                    Text(Money.format(r.receipt.totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
