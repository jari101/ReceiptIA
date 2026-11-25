package pt.receiptai.importer

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pt.receiptai.parser.ReceiptFileType
import pt.receiptai.parser.exampleFile
import pt.receiptai.parser.zipOf
import java.io.ByteArrayInputStream

/** Importa um .zip do princípio ao fim, como se viesse do "Escolher ficheiro". */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ZipImportTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val importer = ReceiptImporter(context)

    private fun import(zip: ByteArray): ImportResult = runBlocking {
        val uri = Uri.parse("content://teste/recibos")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(zip))
        importer.fromUri(uri)
    }

    private fun example(name: String) = name to exampleFile(name).toByteArray()

    @Test
    fun `le todos os recibos do zip e lista os ficheiros ignorados`() {
        val zip = zipOf(
            example("recibos-exemplo.csv"), // 2 recibos
            example("recibo-exemplo.json"), // 1 recibo
            "fotos/" to null,
            "fotos/talao.txt" to exampleFile("recibo-exemplo.txt").toByteArray(), // 1 recibo
            "notas.docx" to byteArrayOf(1, 2, 3), // não é recibo
            "vazio.txt" to ByteArray(0), // sem texto
            "outro.zip" to zipOf(), // zip dentro de zip
            "__MACOSX/._talao.txt" to byteArrayOf(0), // lixo do macOS: nem aparece
        )

        val result = import(zip) as ImportResult.Success

        assertEquals(ReceiptFileType.ZIP, result.type)
        assertEquals(4, result.receipts.size)
        assertTrue(result.receipts.all { it.items.isNotEmpty() })
        val summary = result.zip!!
        assertEquals("recibos.zip", summary.zipName)
        assertEquals(6, summary.fileCount)
        assertEquals(3, summary.readCount)
        assertEquals(setOf("notas.docx", "vazio.txt", "outro.zip"), summary.ignored.toSet())
        assertTrue(result.rawText.contains("===== talao.txt ====="))
    }

    @Test
    fun `zip sem recibos, vazio ou estragado da uma mensagem clara`() {
        val noReceipts = import(zipOf("notas.docx" to byteArrayOf(1, 2, 3))) as ImportResult.Failure
        assertTrue(noReceipts.message, noReceipts.message.startsWith("Não encontrei recibos dentro de recibos.zip"))

        val empty = import(zipOf("__MACOSX/._a.csv" to byteArrayOf(0))) as ImportResult.Failure
        assertEquals("O ficheiro recibos.zip está vazio.", empty.message)

        val broken = import("PK\u0003\u0004 isto não é um zip".toByteArray()) as ImportResult.Failure
        assertEquals("O ficheiro .zip está danificado.", broken.message)
    }

    @Test
    fun `zip com ficheiros a mais para a importacao`() {
        val zip = zipOf(*Array(51) { "recibo$it.txt" to "LEITE 0,89".toByteArray() })
        val result = import(zip) as ImportResult.Failure
        assertEquals("O zip tem demasiados ficheiros (máx. 50).", result.message)
    }

    @Test
    fun `um ficheiro sozinho continua a funcionar como antes`() {
        val result = runBlocking { importer.fromSharedText(exampleFile("recibo-exemplo.json")) } as ImportResult.Success
        assertEquals(ReceiptFileType.JSON, result.type)
        assertEquals(null, result.zip)
    }
}
