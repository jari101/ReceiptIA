package pt.receiptai.parser

/** Uma linha de texto encontrada pelo OCR, com a sua posição na imagem. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * O ML Kit devolve o nome do produto e o preço como linhas SEPARADAS (colunas diferentes).
 * Aqui juntamos as linhas que estão à mesma altura, da esquerda para a direita,
 * para reconstruir as linhas do recibo: "LEITE 1L        0,89".
 */
object OcrLineAssembler {

    fun assemble(lines: List<OcrLine>): String {
        if (lines.isEmpty()) return ""
        val sorted = lines.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val tolerance = (line.height.coerceAtLeast(1) * 0.6).toInt()
            if (row != null && kotlin.math.abs(row.map { it.centerY }.average().toInt() - line.centerY) <= tolerance) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString("   ") { it.text } }
    }
}
