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
import pt.receiptai.parser.exampleBytes
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
        val summary = result.summary!!
        assertEquals("recibos.zip", summary.zipName)
        assertEquals(6, summary.fileCount)
        assertEquals(3, summary.readCount)
        assertEquals(setOf("notas.docx", "vazio.txt", "outro.zip"), summary.ignored.toSet())
        assertTrue(result.rawText.contains("===== talao.txt ====="))
    }

    @Test
    fun `zip sem recibos, vazio ou estragado da uma mensagem clara`() {
        val noReceipts = import(zipOf("notas.docx" to byteArrayOf(1, 2, 3))) as ImportResult.Failure
        assertTrue(noReceipts.message, noReceipts.message.startsWith("Não encontrei recibos nem receitas dentro de recibos.zip"))

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
        assertEquals(null, result.summary)
    }

    // ---------------- Fase 4: vários ficheiros e receitas ----------------

    private fun register(name: String, bytes: ByteArray, mime: String? = null): Uri {
        val uri = Uri.parse("content://teste/$name")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        return uri
    }

    @Test
    fun `zip de exemplo com recibos e receitas separa os dois`() {
        val result = import(exampleBytes("recibos-e-receitas-exemplo.zip")) as ImportResult.Success
        assertEquals(4, result.receipts.size) // 2 do CSV + 1 do JSON + 1 do talão
        assertEquals(setOf("Bacalhau à Brás", "Arroz de pato", "Torradas com abacate"), result.recipes.map { it.name }.toSet())
        val summary = result.summary!!
        assertEquals(5, summary.fileCount)
        assertEquals(5, summary.readCount)
        assertEquals(emptyList<String>(), summary.ignored)
    }

    @Test
    fun `um ficheiro de receitas sozinho nao tem recibos para rever`() {
        val result = import(exampleFile("receitas-exemplo.json").toByteArray()) as ImportResult.Success
        assertEquals(emptyList<Any>(), result.receipts)
        assertEquals(2, result.recipes.size)
    }

    @Test
    fun `varios ficheiros escolhidos de uma vez`() {
        val uris = listOf(
            register("a.csv", exampleFile("recibos-exemplo.csv").toByteArray()),
            register("b.json", exampleFile("receita-exemplo.json").toByteArray()),
            register("c.zip", zipOf(example("recibo-exemplo.txt"))), // um zip no meio também é aberto
            register("d.docx", byteArrayOf(1, 2, 3)),
            Uri.parse("content://teste/nao-existe"), // não abre: fica nos ignorados
        )
        val progress = mutableListOf<ImportProgress>()
        val result = runBlocking { importer.fromUris(uris) { progress += it } } as ImportResult.Success

        assertEquals(ReceiptFileType.VARIOS, result.type)
        assertEquals(3, result.receipts.size) // 2 do CSV + 1 do talão dentro do zip
        assertEquals(listOf("Bacalhau à Brás"), result.recipes.map { it.name })
        val summary = result.summary!!
        assertEquals(null, summary.zipName)
        assertEquals(5, summary.fileCount)
        assertEquals(3, summary.readCount)
        assertEquals(2, summary.ignored.size)
        // O ecrã de espera recebeu "A ler 1 de 5", "2 de 5"…
        assertEquals(listOf(1, 2, 3, 4), progress.map { it.index })
        assertTrue(progress.all { it.total == 5 })
    }

    @Test
    fun `varios ficheiros sem nada que se aproveite`() {
        val uris = listOf(register("x.docx", byteArrayOf(1)), register("y.txt", ByteArray(0)))
        val result = runBlocking { importer.fromUris(uris) } as ImportResult.Failure
        assertTrue(result.message, result.message.startsWith("Não consegui ler nenhum dos 2 ficheiros"))
    }

    @Test
    fun `ficheiros a mais de uma vez`() {
        val uris = List(ReceiptImporter.MAX_FILES + 1) { Uri.parse("content://teste/$it") }
        val result = runBlocking { importer.fromUris(uris) } as ImportResult.Failure
        assertEquals("Escolheste ficheiros a mais (máx. 50 de cada vez).", result.message)
    }
}
