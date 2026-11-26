package pt.receiptai.ui.stats

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
import pt.receiptai.domain.PeriodSummary
import pt.receiptai.domain.StatsCalculator
import pt.receiptai.domain.StatsPeriod

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MES,
    val summary: PeriodSummary? = null,
    val canGoForward: Boolean = false,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {
    /** Um dia dentro do período que está a ser visto (começa hoje). */
    private val anchor = MutableStateFlow(container.today())

    val state: StateFlow<StatsUiState> = combine(
        container.settings.statsPeriod,
        anchor,
        container.receipts.receiptSummaries,
        container.receipts.purchaseLines,
    ) { period, day, receipts, lines ->
        val summary = StatsCalculator.summarize(period, day, receipts, lines, container.today())
        StatsUiState(period, summary, canGoForward = summary.to < container.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(period: StatsPeriod) {
        anchor.value = container.today()
        viewModelScope.launch { container.settings.setStatsPeriod(period) }
    }

    fun previous() = anchor.update { StatsCalculator.shift(state.value.period, it, -1) }
    fun next() = anchor.update { StatsCalculator.shift(state.value.period, it, 1) }
}
