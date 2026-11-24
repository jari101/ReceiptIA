package pt.receiptai.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.receiptai.R
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.ui.components.AppCard
import pt.receiptai.ui.components.EmptyState
import pt.receiptai.ui.components.Pill
import pt.receiptai.ui.components.ScreenTitle
import pt.receiptai.ui.components.appViewModel
import pt.receiptai.ui.components.label
import pt.receiptai.ui.theme.EaseOutStrong

@Composable
fun RecipesRoute(onNewRecipe: () -> Unit) {
    val vm = appViewModel { RecipesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    RecipesScreen(
        state, onNewRecipe, vm::toggleCookMode, vm::setMealType, vm::toggleDiet, vm::toggleExpanded, vm::addMissingToShopping, vm::delete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipesScreen(
    state: RecipesUiState,
    onNewRecipe: () -> Unit,
    onToggleCookMode: () -> Unit,
    onMealType: (MealType?) -> Unit,
    onDiet: (DietTag) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    onAddMissing: (RecipeCardData) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenTitle(stringResource(R.string.recipes_title), stringResource(R.string.recipes_subtitle)) }

            item {
                // O botão principal: examina a despensa e mostra o que dá para cozinhar.
                if (state.cookMode) {
                    OutlinedButton(onClick = onToggleCookMode, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.recipes_show_all))
                    }
                } else {
                    Button(onClick = onToggleCookMode, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Outlined.SoupKitchen, null); Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.recipes_what_can_i_cook), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (state.cookMode) {
                item {
                    Text(
                        stringResource(R.string.recipes_cook_result, state.recipes.size, state.pantryCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = state.mealType == null, onClick = { onMealType(null) }, label = { Text(stringResource(R.string.all)) }) }
                    items(MealType.entries) { type ->
                        FilterChip(selected = state.mealType == type, onClick = { onMealType(if (state.mealType == type) null else type) }, label = { Text(type.label()) })
                    }
                }
            }
            item {
                Text(stringResource(R.string.recipes_diet), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DietTag.entries.forEach { tag ->
                        FilterChip(selected = tag in state.diets, onClick = { onDiet(tag) }, label = { Text(tag.label()) })
                    }
                }
            }

            if (state.recipes.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        Icons.Outlined.RestaurantMenu,
                        stringResource(R.string.recipes_empty_title),
                        stringResource(if (state.cookMode) R.string.recipes_empty_cook else R.string.recipes_empty_filters),
                    )
                }
            }
            items(state.recipes, key = { it.match.recipe.id }) { card ->
                RecipeCard(
                    card,
                    expanded = card.match.recipe.id in state.expanded,
                    onToggle = { onToggleExpanded(card.match.recipe.id) },
                    onAddMissing = { onAddMissing(card) },
                    onDelete = { onDelete(card.match.recipe.id) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewRecipe,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            icon = { Icon(Icons.Outlined.Add, null) },
            text = { Text(stringResource(R.string.recipes_new)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeCard(card: RecipeCardData, expanded: Boolean, onToggle: () -> Unit, onAddMissing: () -> Unit, onDelete: () -> Unit) {
    val recipe = card.match.recipe
    AppCard(onClick = onToggle) {
        Text(recipe.name, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(recipe.mealType.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" ${recipe.minutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recipe.tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recipe.tags.sortedBy { it.ordinal }.forEach { Pill(it.label()) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.recipes_have_of, card.match.have.size, recipe.ingredients.size),
            style = MaterialTheme.typography.labelLarge,
            color = if (card.match.missing.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { card.match.coverage },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220, easing = EaseOutStrong)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180, easing = EaseOutStrong)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.recipes_ingredients), style = MaterialTheme.typography.titleSmall)
                recipe.ingredients.forEach { ing ->
                    val have = ing in card.match.have
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (have) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            stringResource(if (have) R.string.recipes_have else R.string.recipes_missing),
                            Modifier.size(18.dp),
                            tint = if (have) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(ing, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(card.amounts[ing].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (card.match.missing.isNotEmpty()) {
                    TextButton(onClick = onAddMissing) {
                        Icon(Icons.Outlined.AddShoppingCart, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.recipes_add_missing))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.recipes_steps), style = MaterialTheme.typography.titleSmall)
                card.steps.forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(8.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (card.isCustom) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.recipes_delete))
                    }
                }
            }
        }
    }
}
