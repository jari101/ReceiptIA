package pt.receiptai.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.receiptai.R
import pt.receiptai.ReceiptAiApp
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.ui.components.SectionTitle
import pt.receiptai.ui.components.label

/** Formulário "Nova receita": nome, tipo, tempo, dieta, ingredientes e passos. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewRecipeScreen(onClose: () -> Unit) {
    val container = (LocalContext.current.applicationContext as ReceiptAiApp).container
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("20") }
    var mealType by rememberSaveable { mutableStateOf(MealType.JANTAR) }
    val tags = remember { mutableStateListOf<DietTag>() }
    val ingredients = remember { mutableStateListOf("" to "") }
    val steps = remember { mutableStateListOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val validIngredients = ingredients.filter { it.first.isNotBlank() }
    val validSteps = steps.filter { it.isNotBlank() }
    val isValid = name.isNotBlank() && validIngredients.isNotEmpty() && validSteps.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_recipe_title)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.new_recipe_name)) },
                    isError = showErrors && name.isBlank(), singleLine = true,
                )
            }
            item {
                Text(stringResource(R.string.new_recipe_meal), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { t -> FilterChip(mealType == t, { mealType = t }, label = { Text(t.label()) }) }
                }
            }
            item {
                OutlinedTextField(
                    minutes, { v -> minutes = v.filter(Char::isDigit).take(3) }, Modifier.width(160.dp),
                    label = { Text(stringResource(R.string.new_recipe_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
            }
            item {
                Text(stringResource(R.string.recipes_diet), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DietTag.entries.forEach { t ->
                        FilterChip(t in tags, { if (t in tags) tags.remove(t) else tags.add(t) }, label = { Text(t.label()) })
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.recipes_ingredients)) }
            itemsIndexed(ingredients) { i, (ing, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        ing, { ingredients[i] = it to amount }, Modifier.weight(1.4f),
                        label = { Text(stringResource(R.string.new_recipe_ingredient)) }, singleLine = true,
                        isError = showErrors && validIngredients.isEmpty() && i == 0,
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(amount, { ingredients[i] = ing to it }, Modifier.weight(1f), label = { Text(stringResource(R.string.new_recipe_amount)) }, singleLine = true)
                    IconButton(onClick = { if (ingredients.size > 1) ingredients.removeAt(i) else ingredients[0] = "" to "" }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.delete))
                    }
                }
            }
            item {
                TextButton(onClick = { ingredients.add("" to "") }) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.new_recipe_add_ingredient))
                }
            }

            item { SectionTitle(stringResource(R.string.recipes_steps)) }
            itemsIndexed(steps) { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        step, { steps[i] = it }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.new_recipe_step, i + 1)) },
                        isError = showErrors && validSteps.isEmpty() && i == 0,
                    )
                    IconButton(onClick = { if (steps.size > 1) steps.removeAt(i) else steps[0] = "" }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.delete))
                    }
                }
            }
            item {
                TextButton(onClick = { steps.add("") }) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.new_recipe_add_step))
                }
            }
            item {
                if (showErrors && !isValid) {
                    Text(stringResource(R.string.new_recipe_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!isValid) { showErrors = true; return@Button }
                        scope.launch {
                            container.recipes.add(name, mealType, minutes.toIntOrNull() ?: 0, tags.toSet(), validIngredients, validSteps)
                            onClose()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.new_recipe_save)) }
            }
        }
    }
}
