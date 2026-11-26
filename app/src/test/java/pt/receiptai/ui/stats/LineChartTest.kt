package pt.receiptai.ui.stats

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.receiptai.domain.ReceiptSummary
import pt.receiptai.ui.theme.ReceiptAiTheme
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class LineChartTest {
    @get:Rule val compose = createComposeRule()

    private val nov18 = LocalDate.of(2026, 11, 18)
    private val points = listOf(
        ReceiptSummary(1, LocalDate.of(2026, 11, 3), "Continente", 890, 4),
        ReceiptSummary(2, nov18, "Mercado do bairro", 1442, 7),
        ReceiptSummary(3, nov18, "Pingo Doce", 810, 3),
        ReceiptSummary(4, LocalDate.of(2026, 11, 21), "Lidl", 300, 1),
    )

    @Test
    fun `arrastar o dedo mostra os recibos do dia e tocar abre o recibo`() {
        var opened: Long? = null
        compose.setContent { ReceiptAiTheme { LineChart(points, onOpenReceipt = { opened = it }) } }

        // Arrasta da esquerda até ~40 % da largura: o dedo fica no 2.º ponto (18 nov).
        compose.onNodeWithTag(LineChartTag).performTouchInput {
            down(Offset(10f, centerY))
            moveTo(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.4f, centerY))
            up()
        }

        // O popup mostra os DOIS recibos de 18 nov.
        compose.onNodeWithText("Mercado do bairro").assertIsDisplayed()
        compose.onNodeWithText("Pingo Doce").assertIsDisplayed()

        compose.onNodeWithText("Pingo Doce").performClick()
        assertEquals(3L, opened)
    }

    @Test
    fun `tocar num ponto sozinho mostra so esse recibo`() {
        compose.setContent { ReceiptAiTheme { LineChart(points, onOpenReceipt = {}) } }
        compose.onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width - 5f, centerY)) }
        compose.onNodeWithText("Lidl").assertIsDisplayed()
        compose.onNodeWithText("1 produto").assertIsDisplayed()
    }

    @Test
    fun `contas de posicao dos pontos`() {
        // 5 pontos numa largura de 416 px com 8 px de margem: um ponto a cada 100 px.
        assertEquals(8f, ChartGeometry.xOf(0, 416f, 5, 8f))
        assertEquals(408f, ChartGeometry.xOf(4, 416f, 5, 8f))
        assertEquals(208f, ChartGeometry.xOf(2, 416f, 5, 8f))
        // Um ponto sozinho fica no meio.
        assertEquals(208f, ChartGeometry.xOf(0, 416f, 1, 8f))

        // O dedo escolhe o ponto mais perto (e nunca sai dos limites).
        assertEquals(0, ChartGeometry.indexAt(0f, 416f, 5, 8f))
        assertEquals(1, ChartGeometry.indexAt(140f, 416f, 5, 8f))
        assertEquals(2, ChartGeometry.indexAt(170f, 416f, 5, 8f))
        assertEquals(4, ChartGeometry.indexAt(999f, 416f, 5, 8f))
        assertEquals(0, ChartGeometry.indexAt(300f, 416f, 1, 8f))
    }
}
