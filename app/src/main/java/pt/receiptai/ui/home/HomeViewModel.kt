package pt.receiptai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.receiptai.AppContainer
import pt.receiptai.data.db.PantryItemEntity
import pt.receiptai.data.db.ReceiptWithItems
import pt.receiptai.data.db.ShoppingItemEntity
import pt.receiptai.domain.StatsCalculator
import pt.receiptai.domain.StatsPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Um alerta de validade. [onList] = já está (por comprar) na lista de compras. */
data class AlertItem(val id: Long, val name: String, val daysLeft: Long, val onList: Boolean = false)

data class LastPurchase(val store: String, val date: LocalDate, val totalCents: Long, val itemCount: Int)

data class HomeUiState(
    val loading: Boolean = true,
    val isEmpty: Boolean = false,
    val monthTotalCents: Long = 0,
    val monthReceipts: Int = 0,
    val lastPurchase: LastPurchase? = null,
    val alerts: List<AlertItem> = emptyList(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        container.receipts.count,
        container.receipts.receiptTotals,
        container.receipts.latest,
        container.pantry.active,
        container.shopping.items,
    ) { count, totals, latest, pantry, shopping -> buildState(count, totals, latest, pantry, shopping, container.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun loadDemoData() {
        viewModelScope.launch { container.receipts.loadDemoData() }
    }

    /** Botão 🛒 de um alerta: junta o produto à lista de compras. Devolve o id (para o "Anular"). */
    suspend fun addToShopping(alert: AlertItem): Long? = container.shopping.add(alert.name)

    /** "Anular" na mensagem: tira da lista o produto que acabou de ser adicionado. */
    fun removeFromShopping(id: Long) {
        viewModelScope.launch { container.shopping.deleteById(id) }
    }

    companion object {
        /** Função "pura": transforma os dados da base de dados no estado do ecrã (fácil de testar). */
        fun buildState(
            count: Int,
            totals: List<Pair<LocalDate, Long>>,
            latest: ReceiptWithItems?,
            pantry: List<PantryItemEntity>,
            shopping: List<ShoppingItemEntity>,
            today: LocalDate,
        ): HomeUiState {
            // Produtos que ainda estão por comprar na lista (os já riscados não contam).
            val onList = shopping.filter { !it.checked }.map { it.normalizedName }.toSet()
            val month = StatsCalculator.range(StatsPeriod.MES, today)
            val inMonth = totals.filter { it.first in month }
            return HomeUiState(
                loading = false,
                isEmpty = count == 0,
                monthTotalCents = inMonth.sumOf { it.second },
                monthReceipts = inMonth.size,
                lastPurchase = latest?.let { LastPurchase(it.receipt.storeName, it.receipt.purchasedOn, it.receipt.totalCents, it.items.size) },
                alerts = pantry.map { AlertItem(it.id, it.name, ChronoUnit.DAYS.between(today, it.expiresOn), it.normalizedName in onList) }
                    .filter { it.daysLeft <= 2 }
                    .take(5),
            )
        }
    }
}
