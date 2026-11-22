package pt.receiptai.ui.recipes

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
import pt.receiptai.data.repo.toInfo
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.domain.RecipeMatch
import pt.receiptai.domain.RecipeMatcher

/** Uma receita pronta a mostrar: comparação com a despensa + quantidades + passos. */
data class RecipeCardData(
    val match: RecipeMatch,
    val amounts: Map<String, String>,
    val steps: List<String>,
    val isCustom: Boolean,
)

data class RecipesUiState(
    val loading: Boolean = true,
    val recipes: List<RecipeCardData> = emptyList(),
    val mealType: MealType? = null,
    val diets: Set<DietTag> = emptySet(),
    val cookMode: Boolean = false,
    val expanded: Set<Long> = emptySet(),
    val pantryCount: Int = 0,
)

private data class Filters(val mealType: MealType?, val diets: Set<DietTag>, val cookMode: Boolean, val expanded: Set<Long>)

class RecipesViewModel(private val container: AppContainer) : ViewModel() {
    private val mealType = MutableStateFlow<MealType?>(null)
    private val cookMode = MutableStateFlow(false)
    private val expanded = MutableStateFlow<Set<Long>>(emptySet())

    private val filters = combine(mealType, container.settings.diets, cookMode, expanded, ::Filters)

    val state: StateFlow<RecipesUiState> = combine(container.recipes.recipes, container.pantry.active, filters) { recipes, pantry, f ->
        val today = container.today()
        // Só contam ingredientes que ainda estão dentro da validade.
        val available = pantry.filter { !it.expiresOn.isBefore(today) }.map { it.name }
        val byId = recipes.associateBy { it.recipe.id }
        var matches = RecipeMatcher.match(recipes.map { it.toInfo() }, available, f.mealType, f.diets)
        // "O que posso cozinhar?": só receitas com pelo menos metade dos ingredientes em casa.
        matches = if (f.cookMode) matches.filter { it.coverage >= 0.5f } else matches.sortedBy { it.recipe.name }
        RecipesUiState(
            loading = false,
            recipes = matches.map { m ->
                val r = byId.getValue(m.recipe.id)
                RecipeCardData(m, r.ingredients.associate { it.name to it.amount }, r.recipe.steps.lines().filter { it.isNotBlank() }, r.recipe.isCustom)
            },
            mealType = f.mealType,
            diets = f.diets,
            cookMode = f.cookMode,
            expanded = f.expanded,
            pantryCount = available.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun toggleCookMode() = cookMode.update { !it }
    fun setMealType(type: MealType?) { mealType.value = type }
    fun toggleDiet(tag: DietTag) {
        val next = state.value.diets.let { if (tag in it) it - tag else it + tag }
        viewModelScope.launch { container.settings.setDiets(next) }
    }
    fun toggleExpanded(id: Long) = expanded.update { if (id in it) it - id else it + id }

    fun addMissingToShopping(card: RecipeCardData) {
        viewModelScope.launch { container.shopping.addAll(card.match.missing, isAuto = false) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.recipes.delete(id) }
    }
}
