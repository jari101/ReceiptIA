package pt.receiptai.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.receiptai.AppContainer
import pt.receiptai.data.db.PantryItemEntity
import pt.receiptai.data.db.ShoppingItemEntity
import pt.receiptai.domain.PurchaseLine
import pt.receiptai.domain.ShoppingListPlanner
import pt.receiptai.domain.ShoppingSuggestion
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PantryRow(val id: Long, val name: String, val expiresOn: LocalDate, val daysLeft: Long)

data class PantryUiState(
    val loading: Boolean = true,
    val items: List<PantryRow> = emptyList(),
    val shopping: List<ShoppingItemEntity> = emptyList(),
    val suggestions: List<ShoppingSuggestion> = emptyList(),
)

class PantryViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<PantryUiState> = combine(
        container.pantry.active,
        container.shopping.items,
        container.receipts.purchaseLines,
    ) { pantry, shopping, history -> buildState(pantry, shopping, history, container.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    fun markUsed(id: Long) = launch { container.pantry.markUsed(id) }
    fun updateExpiry(id: Long, date: LocalDate) = launch { container.pantry.updateExpiry(id, date) }
    fun addIngredient(name: String) = launch { if (name.isNotBlank()) container.pantry.addManual(name) }

    fun addShopping(name: String) = launch { container.shopping.add(name) }
    fun addSuggestion(s: ShoppingSuggestion) = launch { container.shopping.add(s.name, isAuto = true) }
    fun addAllSuggestions() = launch { container.shopping.addAll(state.value.suggestions.map { it.name }, isAuto = true) }
    fun toggle(item: ShoppingItemEntity) = launch { container.shopping.toggle(item) }
    fun delete(item: ShoppingItemEntity) = launch { container.shopping.delete(item) }
    fun clearChecked() = launch { container.shopping.clearChecked() }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    companion object {
        fun buildState(
            pantry: List<PantryItemEntity>,
            shopping: List<ShoppingItemEntity>,
            history: List<PurchaseLine>,
            today: LocalDate,
        ): PantryUiState {
            val inList = shopping.filter { !it.checked }.map { it.normalizedName }.toSet()
            return PantryUiState(
                loading = false,
                items = pantry.map { PantryRow(it.id, it.name, it.expiresOn, ChronoUnit.DAYS.between(today, it.expiresOn)) },
                shopping = shopping,
                // Não sugerir o que já está na lista.
                suggestions = ShoppingListPlanner.suggest(history, today).filter { it.normalizedName !in inList }.take(10),
            )
        }
    }
}
