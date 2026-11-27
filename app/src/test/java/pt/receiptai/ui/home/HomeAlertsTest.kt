package pt.receiptai.ui.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.receiptai.data.db.PantryItemEntity
import pt.receiptai.data.db.ShoppingItemEntity
import pt.receiptai.domain.Normalizer
import pt.receiptai.ui.theme.ReceiptAiTheme
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class HomeAlertsTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 26)

    private fun pantry(id: Long, name: String, expires: LocalDate) =
        PantryItemEntity(id, name, Normalizer.normalize(name), addedOn = today.minusDays(10), expiresOn = expires)

    private fun shopping(name: String, checked: Boolean = false) =
        ShoppingItemEntity(name = name, normalizedName = Normalizer.normalize(name), checked = checked)

    @Test
    fun `alerta sabe se o produto ja esta por comprar na lista`() {
        val state = HomeViewModel.buildState(
            count = 1,
            totals = emptyList(),
            latest = null,
            pantry = listOf(
                pantry(1, "Bananas", today.minusDays(5)),
                pantry(2, "Leite meio gordo 1L", today.minusDays(7)),
                pantry(3, "Pão de mistura", today.plusDays(1)),
            ),
            // Maiúsculas/acentos não contam; um produto já riscado não conta como "na lista".
            shopping = listOf(shopping("BANANAS"), shopping("Pão de mistura", checked = true)),
            today = today,
        )
        assertEquals(listOf(true, false, false), state.alerts.map { it.onList })
    }

    @Test
    fun `carrinho chama o callback e o produto na lista mostra o visto`() {
        var added: AlertItem? = null
        val alerts = listOf(AlertItem(1, "Bananas", -5), AlertItem(2, "Leite", -7, onList = true))
        compose.setContent {
            ReceiptAiTheme {
                HomeScreen(HomeUiState(loading = false, alerts = alerts), {}, {}, {}, {}, {}, onAddToShopping = { added = it })
            }
        }
        compose.onNodeWithContentDescription("Leite já está na lista de compras").assertIsDisplayed()
        compose.onNodeWithContentDescription("Adicionar Bananas à lista de compras").performClick()
        assertEquals("Bananas", added?.name)
    }
}
