package pt.receiptai.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.receiptai.R
import pt.receiptai.domain.ChartStyle

/** Etiqueta para encontrar o botão do menu nos testes. */
const val ChartStyleMenuTag = "menu_tipo_grafico"

/**
 * Botão "Linha atual: Reta ▾" por baixo do gráfico. Ao tocar, abre um menu com os
 * 3 estilos (Barras, Reta, Curva); o escolhido tem um ✓.
 */
@Composable
fun ChartStyleMenu(style: ChartStyle, onStyle: (ChartStyle) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    // O botão de texto tem 8 dp de margem por dentro; puxamo-lo 8 dp para a esquerda
    // para o texto ficar alinhado com o resto do cartão.
    Box(modifier.offset(x = (-8).dp)) {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(start = 8.dp, end = 4.dp),
            modifier = Modifier.testTag(ChartStyleMenuTag),
        ) {
            Text(stringResource(R.string.stats_chart_style_current, style.label()), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.stats_chart_style_change))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ChartStyle.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    leadingIcon = { ChartStyleIcon(option, MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (option == style) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        open = false
                        onStyle(option)
                    },
                )
            }
        }
    }
}

@Composable
fun ChartStyle.label(): String = stringResource(
    when (this) {
        ChartStyle.BARRAS -> R.string.chart_style_bars
        ChartStyle.RETA -> R.string.chart_style_straight
        ChartStyle.CURVA -> R.string.chart_style_curved
    },
)

/** Ícone pequeno (20×16 dp) desenhado à mão: 3 barras, uma linha em zigue-zague ou uma curva. */
@Composable
private fun ChartStyleIcon(style: ChartStyle, color: Color) {
    Canvas(Modifier.size(width = 20.dp, height = 16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // As mesmas 4 alturas nos 3 ícones, para se ver que é o mesmo gráfico desenhado de outra forma.
        val heights = listOf(0.35f, 0.85f, 0.2f, 0.65f)
        when (style) {
            ChartStyle.BARRAS -> {
                val slot = w / heights.size
                heights.forEachIndexed { i, f ->
                    drawRoundRect(
                        color,
                        topLeft = Offset(i * slot + slot * 0.2f, h * (1 - f)),
                        size = Size(slot * 0.6f, h * f),
                        cornerRadius = CornerRadius(2f, 2f),
                    )
                }
            }
            ChartStyle.RETA, ChartStyle.CURVA -> {
                val xs = heights.indices.map { 1.dp.toPx() + it * (w - 2.dp.toPx()) / (heights.size - 1) }
                val ys = heights.map { h * (1 - it) + 1.dp.toPx() * it }
                val path = Path().apply {
                    moveTo(xs[0], ys[0])
                    for (i in 1 until xs.size) {
                        if (style == ChartStyle.CURVA) {
                            val midX = (xs[i - 1] + xs[i]) / 2f
                            cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i])
                        } else {
                            lineTo(xs[i], ys[i])
                        }
                    }
                }
                drawPath(path, color, style = stroke)
            }
        }
    }
}
