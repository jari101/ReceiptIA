package pt.receiptai.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pt.receiptai.domain.Bucket
import pt.receiptai.domain.Money
import pt.receiptai.ui.theme.EaseOutStrong

/**
 * Gráfico de barras simples desenhado à mão com Canvas (sem bibliotecas).
 * A barra mais alta fica em verde forte; as outras em verde claro.
 */
@Composable
fun BarChart(buckets: List<Bucket>, modifier: Modifier = Modifier) {
    val max = (buckets.maxOfOrNull { it.cents } ?: 0L).coerceAtLeast(1L)
    // Ao mudar de período as barras crescem (200 ms, ease-out) — ajuda a perceber que os dados mudaram.
    val progress = remember(buckets) { Animatable(0f) }
    LaunchedEffect(buckets) { progress.animateTo(1f, tween(durationMillis = 220, easing = EaseOutStrong)) }

    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = buckets.joinToString { "${it.label}: ${Money.format(it.cents)}" }

    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / buckets.size
            val barWidth = slot * 0.56f
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            buckets.forEachIndexed { i, b ->
                val h = size.height * (b.cents.toFloat() / max) * progress.value
                drawRoundRect(
                    color = if (b.cents == max && b.cents > 0) strong else soft,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(8f, 8f),
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
