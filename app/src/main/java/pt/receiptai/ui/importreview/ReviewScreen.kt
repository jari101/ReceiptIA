package pt.receiptai.ui.importreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.receiptai.R
import pt.receiptai.domain.Money
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.recipesAddedText

/**
 * "Rever recibo": o OCR nunca é perfeito, por isso o utilizador confirma
 * (e corrige) a loja, a data e os produtos antes de guardar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(state: ImportUiState.Review, vm: ImportViewModel) {
    var showRaw by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = { IconButton(onClick = vm::cancel) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp)) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row {
                        OutlinedButton(onClick = vm::cancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                        Spacer(Modifier.size(12.dp))
                        Button(onClick = vm::save, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (state.summary != null || state.recipes != null) {
                    ImportSummaryCard(state)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.review_hint, state.type.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.drafts.forEachIndexed { index, draft ->
                item(key = "r$index") { DraftHeader(index, draft, state.drafts.size, vm) }
                items(draft.items, key = { it.key }) { item -> DraftItemRow(index, item, vm) }
                item(key = "a$index") {
                    TextButton(onClick = { vm.addItem(index) }) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.review_add_item))
                    }
                    if (draft.items.isEmpty()) {
                        Text(stringResource(R.string.review_no_items), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state.rawText.isNotBlank()) {
                item {
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(stringResource(if (showRaw) R.string.review_hide_text else R.string.review_show_text))
                    }
                    if (showRaw) {
                        AppCard {
                            Text(state.rawText.take(4000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftHeader(index: Int, draft: DraftReceipt, count: Int, vm: ImportViewModel) {
    AppCard {
        if (count > 1) Text(stringResource(R.string.review_receipt_n, index + 1, count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            draft.storeName, { v -> vm.updateReceipt(index) { it.copy(storeName = v) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.review_store)) }, singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            draft.date, { v -> vm.updateReceipt(index) { it.copy(date = v) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.review_date)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.total), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(Money.format(draft.totalCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        draft.printedTotalCents?.takeIf { it != draft.totalCents }?.let {
            Text(stringResource(R.string.review_printed_total, Money.format(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DraftItemRow(receipt: Int, item: DraftItem, vm: ImportViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            item.name, { v -> vm.updateItem(receipt, item.key) { it.copy(name = v) } }, Modifier.weight(1f),
            label = { Text(stringResource(R.string.review_product)) }, singleLine = true,
        )
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            item.quantity, { v -> vm.updateItem(receipt, item.key) { it.copy(quantity = v) } }, Modifier.width(64.dp),
            label = { Text(stringResource(R.string.review_qty)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            item.price, { v -> vm.updateItem(receipt, item.key) { it.copy(price = v) } }, Modifier.width(84.dp),
            label = { Text("€") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = Money.parseToCents(item.price) == null,
        )
        IconButton(onClick = { vm.removeItem(receipt, item.key) }) { Icon(Icons.Outlined.Close, stringResource(R.string.delete)) }
    }
}

/**
 * Resumo de uma importação com vários ficheiros ou um .zip:
 * de onde vieram, quantos foram lidos, as receitas que já entraram e os ficheiros ignorados.
 */
@Composable
private fun ImportSummaryCard(state: ImportUiState.Review) {
    val summary = state.summary
    AppCard {
        if (summary != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (summary.zipName != null) Icons.Outlined.FolderZip else Icons.Outlined.FileCopy,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    summary.zipName ?: pluralStringResource(R.plurals.files_chosen, summary.fileCount, summary.fileCount),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.import_summary_read, summary.readCount, summary.fileCount) + " · " +
                    pluralStringResource(R.plurals.receipts_to_review, state.drafts.size, state.drafts.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.recipes?.let { recipes ->
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(recipesAddedText(recipes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (summary != null && summary.ignored.isNotEmpty()) {
            // Mostra no máximo 5 nomes, para o cartão não ficar enorme.
            val shown = summary.ignored.take(5).joinToString(", ")
            val more = summary.ignored.size - 5
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.import_ignored, if (more > 0) "$shown ${stringResource(R.string.import_ignored_more, more)}" else shown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.review_hint_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
