package pt.receiptai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.receiptai.AppContainer
import pt.receiptai.domain.StatsCalculator
import pt.receiptai.domain.StatsPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AlertItem(val id: Long, val name: String, val daysLeft: Long)

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
    ) { count, totals, latest, pantry ->
        val today = container.today()
        val month = StatsCalculator.range(StatsPeriod.MES, today)
        val inMonth = totals.filter { it.first in month }
        HomeUiState(
            loading = false,
            isEmpty = count == 0,
            monthTotalCents = inMonth.sumOf { it.second },
            monthReceipts = inMonth.size,
            lastPurchase = latest?.let { LastPurchase(it.receipt.storeName, it.receipt.purchasedOn, it.receipt.totalCents, it.items.size) },
            alerts = pantry.map { AlertItem(it.id, it.name, ChronoUnit.DAYS.between(today, it.expiresOn)) }
                .filter { it.daysLeft <= 2 }
                .take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun loadDemoData() {
        viewModelScope.launch { container.receipts.loadDemoData() }
    }
}
