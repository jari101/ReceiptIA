package pt.receiptai.ui.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.receiptai.R
import pt.receiptai.data.db.ShoppingItemEntity
import pt.receiptai.domain.ShoppingSuggestion
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.components.Dates
import pt.receiptai.ui.components.EmptyState
import pt.receiptai.ui.components.ExpiryBadge
import pt.receiptai.ui.components.ScreenPadding
import pt.receiptai.ui.components.ScreenTitle
import pt.receiptai.ui.components.SectionTitle
import pt.receiptai.ui.components.appViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun PantryRoute(initialTab: Int = 0) {
    val vm = appViewModel { PantryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    PantryScreen(
        state = state,
        initialTab = initialTab,
        actions = PantryActions(
            onUsed = vm::markUsed, onExpiry = vm::updateExpiry, onAddIngredient = vm::addIngredient,
            onAddShopping = vm::addShopping, onAddSuggestion = vm::addSuggestion, onAddAll = vm::addAllSuggestions,
            onToggle = vm::toggle, onDelete = vm::delete, onClearChecked = vm::clearChecked,
        ),
    )
}

class PantryActions(
    val onUsed: (Long) -> Unit = {},
    val onExpiry: (Long, LocalDate) -> Unit = { _, _ -> },
    val onAddIngredient: (String) -> Unit = {},
    val onAddShopping: (String) -> Unit = {},
    val onAddSuggestion: (ShoppingSuggestion) -> Unit = {},
    val onAddAll: () -> Unit = {},
    val onToggle: (ShoppingItemEntity) -> Unit = {},
    val onDelete: (ShoppingItemEntity) -> Unit = {},
    val onClearChecked: () -> Unit = {},
)

@Composable
fun PantryScreen(state: PantryUiState, initialTab: Int = 0, actions: PantryActions = PantryActions()) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            stringResource(R.string.pantry_title),
            stringResource(R.string.pantry_subtitle),
            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.pantry_tab_ingredients)) },
                icon = { Icon(Icons.Outlined.Kitchen, null) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.pantry_tab_shopping)) },
                icon = { Icon(Icons.Outlined.ShoppingCart, null) })
        }
        if (tab == 0) IngredientsTab(state, actions) else ShoppingTab(state, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientsTab(state: PantryUiState, actions: PantryActions) {
    var editing by remember { mutableStateOf<PantryRow?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.ingredients_count, state.items.size, state.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { adding = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.add))
                }
            }
        }
        if (state.items.isEmpty() && !state.loading) {
            item { EmptyState(Icons.Outlined.Kitchen, stringResource(R.string.pantry_empty_title), stringResource(R.string.pantry_empty_message)) }
        }
        items(state.items, key = { it.id }) { row ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.size(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExpiryBadge(row.daysLeft)
                            Spacer(Modifier.size(8.dp))
                            Text(Dates.full(row.expiresOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { editing = row }) { Icon(Icons.Outlined.EditCalendar, stringResource(R.string.pantry_edit_date)) }
                    IconButton(onClick = { actions.onUsed(row.id) }) {
                        Icon(Icons.Outlined.CheckCircle, stringResource(R.string.pantry_mark_used), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    editing?.let { row ->
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = row.expiresOn.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        actions.onExpiry(row.id, Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState, title = { Text(stringResource(R.string.pantry_expiry_of, row.name), Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }

    if (adding) {
        TextInputDialog(
            title = stringResource(R.string.pantry_add_title),
            label = stringResource(R.string.pantry_add_label),
            onDismiss = { adding = false },
            onConfirm = { actions.onAddIngredient(it); adding = false },
        )
    }
}

@Composable
private fun ShoppingTab(state: PantryUiState, actions: PantryActions) {
    var newItem by rememberSaveable { mutableStateOf("") }
    val pending = state.shopping.filter { !it.checked }
    val done = state.shopping.filter { it.checked }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.suggestions.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.shopping_suggestions)) {
                    TextButton(onClick = actions.onAddAll) { Text(stringResource(R.string.shopping_add_all)) }
                }
                Text(
                    stringResource(R.string.shopping_suggestions_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    state.suggestions.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.shopping_every_days, Math.round(s.averageIntervalDays).toInt(), s.daysSinceLast.toInt()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { actions.onAddSuggestion(s) }) { Icon(Icons.Outlined.Add, stringResource(R.string.add)) }
                        }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.shopping_to_buy)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.shopping_new_item)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                FilledTonalButton(onClick = { actions.onAddShopping(newItem); newItem = "" }, enabled = newItem.isNotBlank()) {
                    Text(stringResource(R.string.add))
                }
            }
        }
        if (state.shopping.isEmpty()) {
            item { EmptyState(Icons.Outlined.ShoppingCart, stringResource(R.string.shopping_empty_title), stringResource(R.string.shopping_empty_message)) }
        }
        items(pending + done, key = { it.id }) { item ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.checked, onCheckedChange = { actions.onToggle(item) })
                    Text(
                        item.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.isAuto) Icon(Icons.Outlined.AutoAwesome, stringResource(R.string.shopping_auto), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { actions.onDelete(item) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                }
            }
        }
        if (done.isNotEmpty()) {
            item { TextButton(onClick = actions.onClearChecked) { Text(stringResource(R.string.shopping_clear_checked)) } }
        }
    }
}

@Composable
fun TextInputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, initial: String = "") {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
