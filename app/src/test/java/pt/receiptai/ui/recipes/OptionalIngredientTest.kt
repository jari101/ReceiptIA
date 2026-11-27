package pt.receiptai.ui.recipes

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.receiptai.domain.MealType
import pt.receiptai.domain.RecipeInfo
import pt.receiptai.domain.RecipeMatch
import pt.receiptai.ui.theme.ReceiptAiTheme

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class OptionalIngredientTest {
    @get:Rule val compose = createComposeRule()

    private val omelete = RecipeInfo(7, "Omelete de espinafres", MealType.PEQUENO_ALMOCO, 15, emptySet(), listOf("Ovos", "Queijo"), optional = setOf("Queijo"))
    private val card = RecipeCardData(
        RecipeMatch(omelete, have = listOf("Ovos"), missing = listOf("Queijo")),
        amounts = mapOf("Ovos" to "3", "Queijo" to "2 fatias"),
        steps = listOf("Bate os ovos."),
        isCustom = false,
    )

    @Test
    fun `tocar no opcional em falta mostra a nota`() {
        val state = RecipesUiState(loading = false, recipes = listOf(card), expanded = setOf(7))
        compose.setContent { ReceiptAiTheme { RecipesScreen(state, {}, {}, {}, {}, {}, {}, {}) } }

        compose.onNodeWithText("(opcional)").assertIsDisplayed()
        compose.onNodeWithText("Podes fazer esta receita mesmo sem queijo.").assertDoesNotExist()

        compose.onNodeWithText("Queijo").performClick()
        compose.onNodeWithText("Podes fazer esta receita mesmo sem queijo.").assertIsDisplayed()
    }
}
