package pt.receiptai.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.receiptai.domain.Bucket
import pt.receiptai.domain.Money
import pt.receiptai.ui.theme.EaseOutStrong

/** Etiqueta para encontrar o gráfico de barras nos testes. */
const val BarChartTag = "grafico_barras"

/**
 * Gráfico de BARRAS (o original), desenhado à mão com Canvas.
 * A barra mais alta fica em verde forte; as outras em verde claro.
 *
 * Tocar ou arrastar o dedo escolhe uma barra: ela fica em verde forte e abre-se um popup
 * com os recibos dessa barra ([popupTitle] dá o título). Barras sem compras não abrem popup.
 *
 * @param scrolling true enquanto a página está a fazer scroll: o popup fecha-se.
 */
@Composable
fun BarChart(
    buckets: List<Bucket>,
    popupTitle: (Bucket) -> String,
    onOpenReceipt: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scrolling: Boolean = false,
) {
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    LaunchedEffect(scrolling) { if (scrolling) selected = null }

    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    val max = (buckets.maxOfOrNull { it.cents } ?: 0L).coerceAtLeast(1L)
    // Ao mudar de período as barras crescem (220 ms, ease-out) — ajuda a perceber que os dados mudaram.
    val progress = remember(buckets) { Animatable(0f) }
    LaunchedEffect(buckets) { progress.animateTo(1f, tween(durationMillis = 220, easing = EaseOutStrong)) }

    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = buckets.joinToString { "${it.label}: ${Money.format(it.cents)}" }

    /** Escolhe a barra debaixo do dedo (só se tiver compras); vibra levemente ao mudar de barra. */
    fun select(x: Float, width: Float) {
        if (buckets.isEmpty()) return
        val index = ChartGeometry.barIndexAt(x, width, buckets.size)
        val target = index.takeIf { buckets[index].receipts.isNotEmpty() }
        if (target != selected) {
            selected = target
            if (target != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(modifier.semantics { contentDescription = description }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .testTag(BarChartTag),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .pointerInput(buckets) { detectTapGestures { select(it.x, size.width.toFloat()) } }
                    .pointerInput(buckets) {
                        detectHorizontalDragGestures(onDragStart = { select(it.x, size.width.toFloat()) }) { change, _ ->
                            change.consume()
                            select(change.position.x, size.width.toFloat())
                        }
                    },
            ) {
                if (buckets.isEmpty()) return@Canvas
                val slot = size.width / buckets.size
                val barWidth = slot * 0.56f
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                val sel = selected
                buckets.forEachIndexed { i, b ->
                    val h = size.height * (b.cents.toFloat() / max) * progress.value
                    // Com uma barra escolhida, só ela fica em verde forte.
                    val color = when {
                        sel != null -> if (i == sel) strong else soft
                        b.cents == max && b.cents > 0 -> strong
                        else -> soft
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(8f, 8f),
                    )
                }
            }

            val sel = selected
            if (sel != null && sel in buckets.indices) {
                ReceiptsPopup(
                    anchorX = ChartGeometry.barCenter(sel, widthPx, buckets.size),
                    title = popupTitle(buckets[sel]),
                    receipts = buckets[sel].receipts,
                    highlightedId = null,
                    onDismiss = { selected = null },
                    onOpen = { id ->
                        selected = null
                        onOpenReceipt(id)
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            buckets.forEach {
                Text(
                    it.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
