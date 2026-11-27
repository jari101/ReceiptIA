package pt.receiptai.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
            // Verde quando já dá para fazer (mesmo que faltem ingredientes opcionais).
            color = if (card.match.canCook) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                    IngredientRow(
                        name = ing,
                        amount = card.amounts[ing].orEmpty(),
                        have = ing in card.match.have,
                        optional = ing in recipe.optional,
                    )
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

/**
 * Um ingrediente do cartão: ✓ verde se o tens, ○ vermelho se falta.
 * Os opcionais mostram "(opcional)". Se um opcional FALTA, tem um ⓘ e, ao tocar,
 * aparece um balão por cima a dizer que a receita se faz sem ele.
 */
@Composable
private fun IngredientRow(name: String, amount: String, have: Boolean, optional: Boolean) {
    val hasNote = optional && !have
    var showNote by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (hasNote) {
                        Modifier.clickable(onClickLabel = stringResource(R.string.recipes_optional_show_note), role = Role.Button) { showNote = true }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (have) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                stringResource(if (have) R.string.recipes_have else R.string.recipes_missing),
                Modifier.size(18.dp),
                tint = if (have) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.size(8.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                if (optional) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(R.string.recipes_optional),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasNote) {
                    Spacer(Modifier.size(4.dp))
                    Icon(Icons.Outlined.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(amount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showNote) OptionalNote(name, onDismiss = { showNote = false })
    }
}

/** O balão: aparece por cima do ingrediente, com um "biquinho" a apontar para o ○. Tocar fora fecha-o. */
@Composable
private fun OptionalNote(ingredient: String, onDismiss: () -> Unit) {
    val gap = with(LocalDensity.current) { 2.dp.roundToPx() }
    val bubble = MaterialTheme.colorScheme.surfaceContainerHigh
    val border = MaterialTheme.colorScheme.outline
    Popup(
        popupPositionProvider = AboveStart(gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        Column {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bubble,
                border = BorderStroke(1.dp, border),
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.recipes_optional_note, ingredient.replaceFirstChar { it.lowercase() }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            // O "biquinho": um triângulo da cor do balão, com contorno nos 2 lados inclinados.
            Canvas(Modifier.padding(start = 2.dp).offset(y = (-1).dp).size(width = 14.dp, height = 7.dp)) {
                val tip = Offset(size.width / 2, size.height)
                val triangle = Path().apply {
                    moveTo(0f, 0f); lineTo(tip.x, tip.y); lineTo(size.width, 0f); close()
                }
                drawPath(triangle, bubble)
                drawLine(border, Offset(0f, 0f), tip, strokeWidth = 1.dp.toPx())
                drawLine(border, tip, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

/** Posição do balão: por cima do ingrediente, alinhado à esquerda (onde está o ○). */
private data class AboveStart(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = anchorBounds.left.coerceIn(0, maxX)
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
