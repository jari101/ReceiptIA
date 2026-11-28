package pt.receiptai.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

/**
 * O popup dos gráficos (barras e linhas): aparece POR CIMA do gráfico, centrado em [anchorX]
 * (posição em píxeis dentro do gráfico), com um [title] e a lista de recibos.
 * Cada recibo é um botão que chama [onOpen]. Se forem muitos, a lista faz scroll.
 *
 * Tem de ser chamado DENTRO da caixa do gráfico: é essa caixa que serve de âncora.
 */
@Composable
internal fun ReceiptsPopup(
    anchorX: Float,
    title: String,
    receipts: List<ReceiptSummary>,
    highlightedId: Long?,
    onDismiss: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    if (receipts.isEmpty()) return
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Popup(
        popupPositionProvider = AbovePoint(anchorX.roundToInt(), gap),
        onDismissRequest = onDismiss,
        // Não "rouba" o teclado nem o botão voltar; tocar fora fecha o popup.
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 220.dp, max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                val header = title +
                    if (receipts.size > 1) " · " + pluralStringResource(R.plurals.receipts_on_day, receipts.size, receipts.size) else ""
                Text(
                    header,
                    Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Até ~4 recibos cabem sem scroll; com mais (ex.: um mês inteiro nas barras) desliza-se.
                Column(Modifier.heightIn(max = 264.dp).verticalScroll(rememberScrollState())) {
                    receipts.forEach { r ->
                        PopupReceiptRow(r, highlighted = receipts.size > 1 && r.id == highlightedId, onClick = { onOpen(r.id) })
                    }
                }
            }
        }
    }
}

/** Uma linha do popup: loja, nº de produtos e total. O recibo debaixo do dedo fica com fundo verde-claro. */
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
 * Diz ao popup ONDE aparecer: centrado no ponto/barra escolhido e por cima do gráfico.
 * Se não couber (ex.: muito à direita), encosta à margem do ecrã.
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
