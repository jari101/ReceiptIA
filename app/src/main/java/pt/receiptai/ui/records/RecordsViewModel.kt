package pt.receiptai.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.receiptai.AppContainer
import pt.receiptai.data.db.ReceiptWithItems
import pt.receiptai.domain.Normalizer
import java.time.YearMonth

data class MonthGroup(val month: YearMonth, val totalCents: Long, val receipts: List<ReceiptWithItems>)

data class RecordsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val groups: List<MonthGroup> = emptyList(),
    val expanded: Set<Long> = emptySet(),
    val totalReceipts: Int = 0,
)

class RecordsViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<RecordsUiState> = combine(container.receipts.receipts, query, expanded) { all, q, open ->
        RecordsUiState(loading = false, query = q, groups = group(filter(all, q)), expanded = open, totalReceipts = all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    fun setQuery(q: String) { query.value = q }

    /** Abre/fecha o menu com os produtos de uma compra. */
    fun toggle(id: Long) = expanded.update { if (id in it) it - id else it + id }

    fun delete(id: Long) {
        viewModelScope.launch { container.receipts.delete(id) }
    }

    companion object {
        /** Pesquisa pelo nome da loja ou de qualquer produto da compra. */
        fun filter(all: List<ReceiptWithItems>, q: String): List<ReceiptWithItems> {
            val key = Normalizer.normalize(q)
            if (key.isBlank()) return all
            return all.filter { r ->
                Normalizer.normalize(r.receipt.storeName).contains(key) || r.items.any { it.normalizedName.contains(key) }
            }
        }

        fun group(receipts: List<ReceiptWithItems>): List<MonthGroup> =
            receipts.groupBy { YearMonth.from(it.receipt.purchasedOn) }
                .map { (month, list) -> MonthGroup(month, list.sumOf { it.receipt.totalCents }, list) }
                .sortedByDescending { it.month }
    }
}
