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
    /** Recibo para onde a lista deve fazer scroll (vindo do gráfico das Estatísticas). */
    val scrollTo: Long? = null,
)

class RecordsViewModel(private val container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())
    private val scrollTo = MutableStateFlow<Long?>(null)

    val state: StateFlow<RecordsUiState> = combine(container.receipts.receipts, query, expanded, scrollTo) { all, q, open, target ->
        RecordsUiState(loading = false, query = q, groups = group(filter(all, q)), expanded = open, totalReceipts = all.size, scrollTo = target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    fun setQuery(q: String) { query.value = q }

    /** Abre/fecha o menu com os produtos de uma compra. */
    fun toggle(id: Long) = expanded.update { if (id in it) it - id else it + id }

    /**
     * Vindo do gráfico das Estatísticas: limpa a pesquisa (para o recibo não ficar escondido),
     * abre o menu do recibo e pede à lista para fazer scroll até ele.
     */
    fun focus(id: Long) {
        query.value = ""
        expanded.update { it + id }
        scrollTo.value = id
    }

    /** A lista já fez scroll: não volta a fazer. */
    fun consumeScroll() { scrollTo.value = null }

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

        /**
         * Posição do recibo [id] na LazyColumn do ecrã Registos, ou null se não estiver na lista.
         * Tem de seguir a MESMA ordem dos itens do ecrã: título, pesquisa e, por cada mês,
         * o cabeçalho do mês seguido dos recibos.
         */
        fun indexOf(groups: List<MonthGroup>, id: Long): Int? {
            var index = 2 // 0 = título, 1 = pesquisa
            for (g in groups) {
                index++ // cabeçalho do mês
                val position = g.receipts.indexOfFirst { it.receipt.id == id }
                if (position >= 0) return index + position
                index += g.receipts.size
            }
            return null
        }

        fun group(receipts: List<ReceiptWithItems>): List<MonthGroup> =
            receipts.groupBy { YearMonth.from(it.receipt.purchasedOn) }
                .map { (month, list) -> MonthGroup(month, list.sumOf { it.receipt.totalCents }, list) }
                .sortedByDescending { it.month }
    }
}
