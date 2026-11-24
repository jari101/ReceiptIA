package pt.receiptai.parser

/**
 * Descobre o tipo de um ficheiro pelo tipo MIME, pela extensão do nome e,
 * se for preciso, pelos primeiros bytes ("assinatura" do ficheiro).
 */
object FileTypeDetector {

    fun detect(mimeType: String?, fileName: String?, bytes: ByteArray): ReceiptFileType? {
        sniff(bytes)?.let { return it } // os bytes não mentem: um PDF começa sempre por "%PDF"
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when {
            mime == "application/pdf" || ext == "pdf" -> ReceiptFileType.PDF
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "bmp") -> ReceiptFileType.IMAGEM
            mime.contains("csv") || ext == "csv" -> ReceiptFileType.CSV
            mime.contains("json") || ext == "json" -> ReceiptFileType.JSON
            mime.contains("xml") || ext == "xml" -> ReceiptFileType.XML
            mime.contains("html") || ext in setOf("html", "htm") -> ReceiptFileType.HTML
            mime.startsWith("text/") || ext in setOf("txt", "text") -> textSniff(bytes)
            else -> null
        }
    }

    private fun sniff(bytes: ByteArray): ReceiptFileType? {
        if (bytes.size < 4) return null
        fun starts(vararg b: Int) = b.indices.all { bytes.size > it && (bytes[it].toInt() and 0xFF) == b[it] }
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> ReceiptFileType.PDF // %PDF
            starts(0xFF, 0xD8, 0xFF) -> ReceiptFileType.IMAGEM // JPEG
            starts(0x89, 0x50, 0x4E, 0x47) -> ReceiptFileType.IMAGEM // PNG
            starts(0x52, 0x49, 0x46, 0x46) && bytes.size > 11 && String(bytes, 8, 4) == "WEBP" -> ReceiptFileType.IMAGEM
            else -> null
        }
    }

    /** Ficheiros de texto: vê se o conteúdo parece JSON, XML, HTML ou CSV. */
    fun textSniff(bytes: ByteArray): ReceiptFileType {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val lower = head.lowercase()
        return when {
            head.startsWith("{") || head.startsWith("[") -> ReceiptFileType.JSON
            lower.startsWith("<!doctype html") || lower.startsWith("<html") -> ReceiptFileType.HTML
            head.startsWith("<") -> ReceiptFileType.XML
            looksLikeCsv(head) -> ReceiptFileType.CSV
            else -> ReceiptFileType.TEXTO
        }
    }

    private fun looksLikeCsv(head: String): Boolean {
        val lines = head.lines().filter { it.isNotBlank() }.take(5)
        if (lines.size < 2) return false
        return listOf(';', '\t', ',').any { sep ->
            val n = lines.first().count { it == sep }
            n >= 2 && lines.all { it.count { c -> c == sep } == n }
        }
    }
}
