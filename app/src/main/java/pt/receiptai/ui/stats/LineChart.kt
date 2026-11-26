package pt.receiptai.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import pt.receiptai.R
import pt.receiptai.domain.Money
import pt.receiptai.domain.ReceiptSummary
import pt.receiptai.domain.StatsCalculator
import pt.receiptai.ui.components.Dates
import pt.receiptai.ui.theme.EaseOutStrong
import kotlin.math.roundToInt

/** Altura da zona do gráfico (sem as datas por baixo). */
private val ChartHeight = 160.dp

/** Espaço livre em cima, para o ponto mais alto não ficar cortado. */
private val TopPadding = 12.dp

/** Com mais pontos do que isto (ex.: um ano inteiro), só se desenha a linha, sem bolinhas. */
private const val MaxDots = 40

/** Etiqueta para encontrar o gráfico nos testes. */
const val LineChartTag = "grafico_linha"

/**
 * Gráfico de LINHA desenhado à mão com Canvas (sem bibliotecas).
 *
 * - Cada ponto é UM recibo, por ordem de compra, com o mesmo espaço entre pontos
 *   (como nos gráficos da bolsa). A altura do ponto é o total desse recibo.
 * - Arrastar o dedo (ou tocar) escolhe o ponto mais próximo e abre um popup por cima do
 *   gráfico com TODOS os recibos desse dia. Tocar num recibo do popup chama [onOpenReceipt].
 *
 * @param scrolling true enquanto a página está a fazer scroll: o popup fecha-se.
 */
@Composable
fun LineChart(
    points: List<ReceiptSummary>,
    onOpenReceipt: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scrolling: Boolean = false,
) {
    // Índice do ponto escolhido (null = nenhum). Volta a null quando os pontos mudam (outro período).
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    LaunchedEffect(scrolling) { if (scrolling) selected = null }

    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { ChartGeometry.EDGE.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }

    // Ao mudar de período, a linha "desenha-se" da esquerda para a direita (400 ms, ease-out).
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { progress.animateTo(1f, tween(durationMillis = 400, easing = EaseOutStrong)) }

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val description = stringResource(
        R.string.stats_chart_description,
        points.joinToString { "${Dates.dayShort(it.date)} ${Money.format(it.totalCents)}" },
    )

    /** Escolhe o ponto mais perto do dedo; vibra levemente quando passa para outro ponto. */
    fun select(x: Float, width: Float) {
        if (points.isEmpty()) return
        val index = ChartGeometry.indexAt(x, width, points.size, edge)
        if (index != selected) {
            selected = index
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .semantics { contentDescription = description }
                .testTag(LineChartTag),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    // Tocar: escolhe um ponto.
                    .pointerInput(points) { detectTapGestures { select(it.x, size.width.toFloat()) } }
                    // Arrastar na horizontal: o ponto segue o dedo. (Arrastar na vertical continua a fazer scroll.)
                    .pointerInput(points) {
                        detectHorizontalDragGestures(onDragStart = { select(it.x, size.width.toFloat()) }) { change, _ ->
                            change.consume()
                            select(change.position.x, size.width.toFloat())
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                // Fundo igual ao de antes: 4 linhas horizontais da grelha.
                for (i in 0..3) {
                    val y = h * i / 3f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                if (points.isEmpty()) return@Canvas

                val max = points.maxOf { it.totalCents }.coerceAtLeast(1L)
                val top = TopPadding.toPx()
                val xs = points.indices.map { ChartGeometry.xOf(it, w, points.size, edge) }
                val ys = points.map { h - (h - top) * (it.totalCents.toFloat() / max) }
                val sel = selected?.takeIf { it in points.indices }

                // Linha vertical tracejada no ponto escolhido (liga o ponto ao popup).
                if (sel != null) {
                    drawLine(
                        guideColor.copy(alpha = 0.6f),
                        Offset(xs[sel], 0f),
                        Offset(xs[sel], h),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                }

                // A linha e as bolinhas (cortadas pela animação de entrada).
                clipRect(right = w * progress.value) {
                    if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(xs[0], ys[0])
                            for (i in 1 until xs.size) lineTo(xs[i], ys[i])
                        }
                        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    if (points.size <= MaxDots) {
                        xs.indices.forEach { drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(xs[it], ys[it])) }
                    }
                }

                // Recibos do mesmo dia do ponto escolhido: anel verde. O ponto debaixo do dedo tem um halo.
                if (sel != null) {
                    val day = points[sel].date
                    points.indices.filter { points[it].date == day }.forEach { i ->
                        val center = Offset(xs[i], ys[i])
                        if (i == sel) drawCircle(lineColor.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = center)
                        drawCircle(lineColor, radius = 6.dp.toPx(), center = center)
                        drawCircle(cardColor, radius = 3.dp.toPx(), center = center)
                    }
                }
            }

            if (points.isEmpty()) {
                Text(
                    stringResource(R.string.stats_empty_title),
                    Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val sel = selected
            if (sel != null && sel in points.indices) {
                val x = ChartGeometry.xOf(sel, widthPx, points.size, edge)
                val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
                Popup(
                    popupPositionProvider = AbovePoint(x.roundToInt(), gap),
                    onDismissRequest = { selected = null },
                    // Não "rouba" o teclado nem o botão voltar; tocar fora fecha o popup.
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                ) {
                    DayReceiptsPopup(
                        receipts = StatsCalculator.sameDay(points, sel),
                        highlightedId = points[sel].id,
                        onOpen = { id ->
                            selected = null
                            onOpenReceipt(id)
                        },
                    )
                }
            }
        }

        DateLabels(points)

        if (points.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.TouchApp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.stats_chart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Datas por baixo do gráfico: a primeira compra, a do meio e a última. */
@Composable
private fun DateLabels(points: List<ReceiptSummary>) {
    if (points.isEmpty()) return
    val first = points.first().date
    val last = points.last().date
    val middle = points[(points.size - 1) / 2].date
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (first == last) {
            Text(Dates.dayShort(first), Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
        } else {
            Text(Dates.dayShort(first), Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Start)
            Text(
                if (middle != first && middle != last) Dates.dayShort(middle) else "",
                Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center,
            )
            Text(Dates.dayShort(last), Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
        }
    }
}

/**
 * O popup: a data e todos os recibos desse dia. Cada recibo é um botão que abre o recibo nos Registos.
 * Quando há vários recibos no dia, o que está debaixo do dedo fica com fundo verde-claro.
 */
@Composable
private fun DayReceiptsPopup(receipts: List<ReceiptSummary>, highlightedId: Long, onOpen: (Long) -> Unit) {
    if (receipts.isEmpty()) return
    Surface(
        modifier = Modifier.widthIn(min = 220.dp, max = 300.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val header = Dates.dayMonth(receipts.first().date) +
                if (receipts.size > 1) " · " + pluralStringResource(R.plurals.receipts_on_day, receipts.size, receipts.size) else ""
            Text(
                header,
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            receipts.forEach { r ->
                PopupReceiptRow(r, highlighted = receipts.size > 1 && r.id == highlightedId, onClick = { onOpen(r.id) })
            }
        }
    }
}

@Composable
private fun PopupReceiptRow(r: ReceiptSummary, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClickLabel = stringResource(R.string.stats_open_receipt), role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Storefront, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.store, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.products_count, r.itemCount, r.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(Money.format(r.totalCents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Diz ao popup ONDE aparecer: centrado no ponto escolhido e por cima do gráfico.
 * Se não couber (ex.: ponto muito à direita), encosta à margem do ecrã.
 */
private data class AbovePoint(private val pointX: Int, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)
        val x = (anchorBounds.left + pointX - popupContentSize.width / 2).coerceIn(gap, maxX)
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(gap)
        return IntOffset(x, y)
    }
}

/** As contas de posição do gráfico, separadas para se poderem testar sem ecrã. */
internal object ChartGeometry {
    /** Margem à esquerda e à direita, para as bolinhas das pontas não ficarem cortadas. */
    val EDGE = 8.dp

    /** Posição horizontal (em píxeis) do ponto [index]. Um só ponto fica no meio. */
    fun xOf(index: Int, width: Float, count: Int, edge: Float): Float =
        if (count <= 1) width / 2f else edge + index * (width - 2 * edge) / (count - 1)

    /** O ponto mais perto da posição [x] do dedo. */
    fun indexAt(x: Float, width: Float, count: Int, edge: Float): Int {
        if (count <= 1) return 0
        val step = (width - 2 * edge) / (count - 1)
        return ((x - edge) / step).roundToInt().coerceIn(0, count - 1)
    }
}
