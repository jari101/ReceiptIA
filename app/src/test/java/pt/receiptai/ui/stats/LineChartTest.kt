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
import pt.receiptai.domain.Bucket
import pt.receiptai.domain.ChartStyle
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
    fun `linha curva tambem mostra o popup`() {
        compose.setContent { ReceiptAiTheme { LineChart(points, onOpenReceipt = {}, curved = true) } }
        compose.onNodeWithTag(LineChartTag).performTouchInput { click(Offset(5f, centerY)) }
        compose.onNodeWithText("Continente").assertIsDisplayed()
    }

    @Test
    fun `tocar numa barra mostra os recibos dessa barra`() {
        val buckets = listOf(
            Bucket("1–7", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), points.take(1)),
            Bucket("8–14", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14), emptyList()),
            Bucket("15–21", LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 21), points.drop(1)),
        )
        var opened: Long? = null
        compose.setContent {
            ReceiptAiTheme { BarChart(buckets, popupTitle = { "Barra ${it.label}" }, onOpenReceipt = { opened = it }) }
        }
        // 3.ª barra (15–21): tem 3 recibos.
        compose.onNodeWithTag(BarChartTag).performTouchInput { click(Offset(width * 0.85f, centerY)) }
        compose.onNodeWithText("Barra 15–21 · 3 recibos").assertIsDisplayed()
        compose.onNodeWithText("Lidl").performClick()
        assertEquals(4L, opened)
    }

    @Test
    fun `barra sem compras nao abre popup`() {
        val buckets = listOf(
            Bucket("Seg", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), emptyList()),
            Bucket("Ter", LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 17), points.take(1)),
        )
        compose.setContent { ReceiptAiTheme { BarChart(buckets, popupTitle = { it.label }, onOpenReceipt = {}) } }
        compose.onNodeWithTag(BarChartTag).performTouchInput { click(Offset(width * 0.25f, centerY)) }
        compose.onNodeWithText("Continente").assertDoesNotExist()
    }

    @Test
    fun `menu mostra o tipo atual e muda para curva`() {
        var chosen: ChartStyle? = null
        compose.setContent { ReceiptAiTheme { ChartStyleMenu(ChartStyle.RETA, onStyle = { chosen = it }) } }
        compose.onNodeWithText("Linha atual: Reta").assertIsDisplayed()
        compose.onNodeWithTag(ChartStyleMenuTag).performClick()
        compose.onNodeWithText("Barras").assertIsDisplayed()
        compose.onNodeWithText("Curva").performClick()
        assertEquals(ChartStyle.CURVA, chosen)
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

        // Barras: 4 barras em 400 px = uma "fatia" de 100 px por barra.
        assertEquals(0, ChartGeometry.barIndexAt(99f, 400f, 4))
        assertEquals(1, ChartGeometry.barIndexAt(100f, 400f, 4))
        assertEquals(3, ChartGeometry.barIndexAt(999f, 400f, 4))
        assertEquals(150f, ChartGeometry.barCenter(1, 400f, 4))
    }
}
