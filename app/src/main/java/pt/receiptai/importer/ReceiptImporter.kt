package pt.receiptai.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pt.receiptai.parser.CsvReceiptParser
import pt.receiptai.parser.FileTypeDetector
import pt.receiptai.parser.HtmlText
import pt.receiptai.parser.JsonReceiptParser
import pt.receiptai.parser.ParsedReceipt
import pt.receiptai.parser.ReceiptFileType
import pt.receiptai.parser.ReceiptTextParser
import pt.receiptai.parser.XmlReceiptParser
import pt.receiptai.parser.ZipExtractor
import java.io.File
import java.util.concurrent.TimeUnit

/** Resultado de uma importação. */
sealed interface ImportResult {
    data class Success(
        val receipts: List<ParsedReceipt>,
        val rawText: String,
        val type: ReceiptFileType,
        /** Só existe quando os recibos vieram de um ficheiro .zip. */
        val zip: ZipSummary? = null,
    ) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/** Resumo de um .zip importado, para mostrar no ecrã Rever. */
data class ZipSummary(
    val zipName: String,
    /** Ficheiros que estavam no zip (sem contar o lixo do macOS e do Windows). */
    val fileCount: Int,
    /** Ficheiros que deram pelo menos um recibo com produtos. */
    val readCount: Int,
    /** Nomes dos ficheiros que não eram recibos ou que não deu para ler. */
    val ignored: List<String>,
)

/**
 * Recebe um recibo em QUALQUER formato suportado e devolve os dados lidos:
 *  ficheiro (Uri) · link (URL) · fotos da câmara · texto partilhado.
 */
class ReceiptImporter(private val context: Context) {

    private val ocr = ReceiptOcr()
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    private var pdfBoxReady = false

    // ---------------- Entradas ----------------

    suspend fun fromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytesLimited() }
                ?: return@runCatching ImportResult.Failure("Não foi possível abrir o ficheiro.")
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            process(bytes, resolver.getType(uri), name, imageUri = uri)
        }.getOrElse { failure(it) }
    }

    suspend fun fromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val clean = url.trim()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                return@runCatching ImportResult.Failure("O link tem de começar por http:// ou https://")
            }
            val request = Request.Builder().url(clean).header("User-Agent", "ReceiptAI-Android").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching ImportResult.Failure("O servidor respondeu com erro ${response.code}.")
                val body = response.body ?: return@runCatching ImportResult.Failure("O link não devolveu nenhum conteúdo.")
                val bytes = body.byteStream().readBytesLimited()
                process(bytes, body.contentType()?.toString(), clean.substringBefore('?').substringAfterLast('/'))
            }
        }.getOrElse { failure(it) }
    }

    /** Páginas digitalizadas pela câmara (uma imagem por página). */
    suspend fun fromScannedPages(pages: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = pages.map { ocr.read(context, it) }.joinToString("\n")
            fromPlainText(text, ReceiptFileType.IMAGEM)
        }.getOrElse { failure(it) }
    }

    /** Texto partilhado de outra app (ou um link). */
    suspend fun fromSharedText(text: String): ImportResult {
        val trimmed = text.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) fromUrl(trimmed)
        else withContext(Dispatchers.Default) { process(trimmed.toByteArray(), "text/plain", null) }
    }

    // ---------------- Processamento ----------------

    private suspend fun process(bytes: ByteArray, mime: String?, name: String?, imageUri: Uri? = null): ImportResult {
        val type = FileTypeDetector.detect(mime, name, bytes)
            ?: return ImportResult.Failure("Formato não suportado ($mime). Usa PDF, imagem, CSV, JSON, XML, HTML, TXT ou ZIP.")
        if (type == ReceiptFileType.ZIP) return fromZip(bytes, name)
        val result = readOne(type, bytes, imageUri)
        return if (result is ImportResult.Success && result.receipts.isEmpty()) {
            // Mesmo sem produtos encontrados, deixamos o utilizador preenchê-los à mão.
            result.copy(receipts = listOf(ParsedReceipt("Loja desconhecida", null, emptyList())))
        } else result
    }

    /**
     * Um .zip com vários recibos: lê cada ficheiro de dentro com [readOne] e junta
     * todos os recibos num só ecrã Rever. O que não for recibo fica na lista de ignorados.
     */
    private suspend fun fromZip(bytes: ByteArray, zipName: String?): ImportResult {
        val name = zipName ?: "recibos.zip"
        val contents = ZipExtractor().extract(bytes)
        val fileCount = contents.files.size + contents.skipped.size
        val receipts = mutableListOf<ParsedReceipt>()
        val ignored = contents.skipped.toMutableList()
        val rawText = StringBuilder()
        var readCount = 0

        for (file in contents.files) {
            val type = FileTypeDetector.detect(null, file.name, file.bytes)
            val result = if (type == null || type == ReceiptFileType.ZIP) null
            else try {
                readOne(type, file.bytes)
            } catch (e: CancellationException) {
                throw e // o utilizador saiu: paramos tudo
            } catch (e: Exception) {
                null // um ficheiro estragado não estraga o zip todo
            }
            // Só contam os recibos com produtos: um ficheiro sem nada que se aproveite é ignorado.
            val found = (result as? ImportResult.Success)?.receipts.orEmpty().filter { it.items.isNotEmpty() }
            if (result !is ImportResult.Success || found.isEmpty()) {
                ignored += file.name
                continue
            }
            readCount++
            receipts += found
            rawText.appendLine("===== ${file.name} =====").appendLine(result.rawText)
        }

        if (receipts.isEmpty()) {
            return ImportResult.Failure(
                if (fileCount == 0) "O ficheiro $name está vazio."
                else "Não encontrei recibos dentro de $name. Usa PDF, imagem, CSV, JSON, XML, HTML ou TXT.",
            )
        }
        val summary = ZipSummary(name, fileCount, readCount, ignored)
        return ImportResult.Success(receipts, rawText.toString().trimEnd(), ReceiptFileType.ZIP, summary)
    }

    /** Lê UM ficheiro de que já sabemos o tipo. */
    private suspend fun readOne(type: ReceiptFileType, bytes: ByteArray, imageUri: Uri? = null): ImportResult {
        val text by lazy { bytes.decodeToString().trimStart('\uFEFF') }
        return when (type) {
            ReceiptFileType.PDF -> fromPlainText(pdfText(bytes), type)
            ReceiptFileType.IMAGEM -> {
                val ocrText = if (imageUri != null) ocr.read(context, imageUri) else ocr.read(decodeBitmap(bytes))
                fromPlainText(ocrText, type)
            }
            ReceiptFileType.HTML -> fromPlainText(HtmlText.toText(text), type)
            ReceiptFileType.TEXTO -> fromPlainText(text, type)
            ReceiptFileType.CSV -> ImportResult.Success(CsvReceiptParser.parse(text), text, type)
            ReceiptFileType.JSON -> ImportResult.Success(JsonReceiptParser.parse(text), text, type)
            ReceiptFileType.XML -> ImportResult.Success(XmlReceiptParser.parse(text), text, type)
            ReceiptFileType.ZIP -> ImportResult.Failure("Não abro um zip dentro de outro zip.")
        }
    }

    private fun fromPlainText(text: String, type: ReceiptFileType): ImportResult =
        if (text.isBlank()) ImportResult.Failure("Não encontrei texto neste recibo. Tenta uma foto mais nítida e bem iluminada.")
        else ImportResult.Success(listOf(ReceiptTextParser.parse(text)), text, type)

    /** PDF digital: tira o texto diretamente. PDF digitalizado (só imagem): desenha as páginas e usa OCR. */
    private suspend fun pdfText(bytes: ByteArray): String {
        if (!pdfBoxReady) { PDFBoxResourceLoader.init(context); pdfBoxReady = true }
        val direct = runCatching {
            PDDocument.load(bytes).use { doc -> PDFTextStripper().apply { sortByPosition = true }.getText(doc) }
        }.getOrDefault("")
        if (direct.count { it.isLetterOrDigit() } > 20) return direct

        val file = File(context.cacheDir, "recibo.pdf").apply { writeBytes(bytes) }
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    buildString {
                        for (index in 0 until minOf(renderer.pageCount, 5)) {
                            val bmp = renderer.openPage(index).use { page ->
                                val scale = 2
                                createBitmap(page.width * scale, page.height * scale).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            appendLine(ocr.read(bmp))
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Imagem inválida")
    }

    private fun failure(t: Throwable): ImportResult.Failure = ImportResult.Failure(
        when (t) {
            is java.net.UnknownHostException -> "Sem ligação à Internet ou endereço errado."
            is java.net.SocketTimeoutException -> "O servidor demorou demasiado a responder."
            is TooLargeException -> "O ficheiro é demasiado grande (máx. 15 MB)."
            is java.util.zip.ZipException ->
                if (t.message.orEmpty().contains("encrypt", ignoreCase = true)) {
                    "Este zip tem palavra-passe. Descompacta-o primeiro e importa os ficheiros um a um."
                } else "O ficheiro .zip está danificado."
            is IllegalArgumentException -> t.message ?: "Ficheiro inválido."
            else -> "Não foi possível ler o recibo: ${t.message ?: t.javaClass.simpleName}"
        },
    )

    private class TooLargeException : Exception()

    private fun java.io.InputStream.readBytesLimited(max: Int = 15 * 1024 * 1024): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw TooLargeException()
        }
        return out.toByteArray()
    }
}
