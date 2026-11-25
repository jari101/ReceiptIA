package pt.receiptai.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class FileTypeAndOcrTest {

    @Test
    fun `deteta o tipo pelos bytes mesmo com o mime errado`() {
        val pdf = "%PDF-1.7 ...".toByteArray()
        assertEquals(ReceiptFileType.PDF, FileTypeDetector.detect("application/octet-stream", "x.bin", pdf))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
        assertEquals(ReceiptFileType.IMAGEM, FileTypeDetector.detect(null, null, png))
    }

    @Test
    fun `deteta formatos de texto`() {
        fun t(s: String, mime: String? = "text/plain", name: String? = null) = FileTypeDetector.detect(mime, name, s.toByteArray())
        assertEquals(ReceiptFileType.JSON, t("""{"loja":"x"}"""))
        assertEquals(ReceiptFileType.XML, t("<?xml version=\"1.0\"?><Invoice/>"))
        assertEquals(ReceiptFileType.HTML, t("<!DOCTYPE html><html></html>"))
        assertEquals(ReceiptFileType.CSV, t("a;b;c\n1;2;3\n4;5;6"))
        assertEquals(ReceiptFileType.TEXTO, t("LEITE 0,89\nTOTAL 0,89"))
        assertEquals(ReceiptFileType.CSV, t("a,b", mime = "text/csv", name = "x.csv"))
    }

    @Test
    fun `deteta zips mas nao os documentos do Office (que tambem sao zips por dentro)`() {
        val zip = zipOf("a.txt" to "LEITE 0,89".toByteArray())
        assertEquals(ReceiptFileType.ZIP, FileTypeDetector.detect("application/octet-stream", "recibos.bin", zip))
        assertEquals(ReceiptFileType.ZIP, FileTypeDetector.detect(null, null, zipOf()))
        assertEquals(ReceiptFileType.ZIP, FileTypeDetector.detect("application/x-zip-compressed", "x.zip", "??".toByteArray()))
        assertEquals(null, FileTypeDetector.detect(null, "notas.docx", zip))
        assertEquals(null, FileTypeDetector.detect(null, "contas.XLSX", zip))
    }

    @Test
    fun `junta colunas do ocr na mesma linha`() {
        val lines = listOf(
            OcrLine("0,89", 400, 102, 450, 122),
            OcrLine("LEITE 1L", 10, 100, 120, 120),
            OcrLine("1,49", 400, 141, 450, 161),
            OcrLine("PAO", 10, 140, 60, 160),
        )
        assertEquals("LEITE 1L   0,89\nPAO   1,49", OcrLineAssembler.assemble(lines))
    }
}
