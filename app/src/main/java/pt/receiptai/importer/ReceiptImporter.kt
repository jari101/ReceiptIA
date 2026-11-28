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
import pt.receiptai.parser.ParsedRecipe
import pt.receiptai.parser.RecipeJsonParser
import pt.receiptai.parser.ZipExtractor
import pt.receiptai.parser.ZipLimitException
import java.util.zip.ZipException
import java.io.File
import java.util.concurrent.TimeUnit

/** Resultado de uma importação. */
sealed interface ImportResult {
    data class Success(
        val receipts: List<ParsedReceipt>,
        val rawText: String,
        val type: ReceiptFileType,
        /** Só existe quando vieram vários ficheiros (escolhidos de uma vez ou dentro de um .zip). */
        val summary: ImportSummary? = null,
        /** Receitas de cozinha encontradas (ficheiros JSON "receiptai-receita-v1"). */
        val recipes: List<ParsedRecipe> = emptyList(),
    ) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/** Resumo de uma importação com vários ficheiros, para mostrar no ecrã Rever. */
data class ImportSummary(
    /** Nome do .zip, ou null quando foram vários ficheiros escolhidos de uma vez. */
    val zipName: String?,
    /** Quantos ficheiros havia (sem contar o lixo do macOS e do Windows). */
    val fileCount: Int,
    /** Ficheiros que deram pelo menos um recibo ou uma receita. */
    val readCount: Int,
    /** Nomes dos ficheiros que não eram recibos nem receitas, ou que não deu para ler. */
    val ignored: List<String>,
)

/** Para o ecrã de espera: "A ler 3 de 12 · talao.jpg". */
data class ImportProgress(val fileName: String, val index: Int, val total: Int)

/**
 * Recebe um recibo em QUALQUER formato suportado e devolve os dados lidos:
 *  ficheiros (Uri, um ou vários, também .zip) · link (URL) · fotos da câmara · texto partilhado.
 * Os ficheiros JSON de receitas de cozinha também são reconhecidos (vão para [ImportResult.Success.recipes]).
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

    suspend fun fromUri(uri: Uri): ImportResult = fromUris(listOf(uri))

    /**
     * Um ou VÁRIOS ficheiros escolhidos de uma vez (ou partilhados de outra app).
     * - Um só ficheiro funciona como sempre (um .zip é aberto e lido por dentro).
     * - Vários: são lidos um a um e juntos num só resultado. Um ficheiro que não dá para ler
     *   não estraga os outros: fica na lista de ignorados. Um .zip no meio também é aberto.
     * Cada ficheiro é lido só quando chega a vez dele, para não ter tudo na memória ao mesmo tempo.
     */
    suspend fun fromUris(uris: List<Uri>, onProgress: (ImportProgress) -> Unit = {}): ImportResult = withContext(Dispatchers.IO) {
        if (uris.size > MAX_FILES) {
            return@withContext ImportResult.Failure("Escolheste ficheiros a mais (máx. $MAX_FILES de cada vez).")
        }
        if (uris.size == 1) {
            return@withContext runCatching {
                val file = open(uris.single())
                onProgress(ImportProgress(file.name ?: "ficheiro", 1, 1))
                process(file.bytes, file.mime, file.name, file.uri, onProgress)
            }.getOrElse { failure(it) }
        }
        runCatching {
            val batch = Batch()
            uris.forEachIndexed { i, uri ->
                val file = try {
                    open(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    batch.skip(displayName(uri) ?: "ficheiro ${i + 1}")
                    return@forEachIndexed
                }
                val name = file.name ?: "ficheiro ${i + 1}"
                onProgress(ImportProgress(name, i + 1, uris.size))
                if (FileTypeDetector.detect(file.mime, file.name, file.bytes) == ReceiptFileType.ZIP) {
                    // Um zip estragado (ou grande demais) no meio dos outros ficheiros é só ignorado.
                    try {
                        batch.addZip(file.bytes) {}
                    } catch (e: ZipException) {
                        batch.skip(name)
                    } catch (e: ZipLimitException) {
                        batch.skip(name)
                    }
                } else {
                    batch.add(name, file.bytes, file.mime, file.uri)
                }
            }
            batch.result(zipName = null)
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

    /** Um ficheiro aberto: nome (se o Android o souber), tipo MIME e conteúdo. */
    private class OpenedFile(val name: String?, val mime: String?, val bytes: ByteArray, val uri: Uri)

    private fun open(uri: Uri): OpenedFile {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytesLimited() }
            ?: throw IllegalArgumentException("Não foi possível abrir o ficheiro.")
        return OpenedFile(displayName(uri), resolver.getType(uri), bytes, uri)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private suspend fun process(
        bytes: ByteArray,
        mime: String?,
        name: String?,
        imageUri: Uri? = null,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        val type = FileTypeDetector.detect(mime, name, bytes)
            ?: return ImportResult.Failure("Formato não suportado ($mime). Usa PDF, imagem, CSV, JSON, XML, HTML, TXT ou ZIP.")
        if (type == ReceiptFileType.ZIP) {
            val zipName = name ?: "recibos.zip"
            return Batch().apply { addZip(bytes, onProgress) }.result(zipName)
        }
        val result = readOne(type, bytes, imageUri)
        return if (result is ImportResult.Success && result.receipts.isEmpty() && result.recipes.isEmpty()) {
            // Mesmo sem produtos encontrados, deixamos o utilizador preenchê-los à mão.
            result.copy(receipts = listOf(ParsedReceipt("Loja desconhecida", null, emptyList())))
        } else result
    }

    /**
     * Um "lote": junta os recibos e as receitas de VÁRIOS ficheiros (escolhidos de uma vez
     * ou tirados de um .zip). O que não for recibo nem receita fica na lista de ignorados.
     */
    private inner class Batch {
        val receipts = mutableListOf<ParsedReceipt>()
        val recipes = mutableListOf<ParsedRecipe>()
        val ignored = mutableListOf<String>()
        val rawText = StringBuilder()
        var fileCount = 0
        var readCount = 0

        /** Um ficheiro que nem deu para abrir. */
        fun skip(name: String) {
            fileCount++
            ignored += name
        }

        /** Lê um ficheiro do lote. Um ficheiro estragado não estraga o lote todo. */
        suspend fun add(name: String, bytes: ByteArray, mime: String?, imageUri: Uri? = null) {
            fileCount++
            val type = FileTypeDetector.detect(mime, name, bytes)
            val result = if (type == null || type == ReceiptFileType.ZIP) null
            else try {
                readOne(type, bytes, imageUri)
            } catch (e: CancellationException) {
                throw e // o utilizador saiu: paramos tudo
            } catch (e: Exception) {
                null
            }
            val success = result as? ImportResult.Success
            // Só contam os recibos com produtos: um ficheiro sem nada que se aproveite é ignorado.
            val found = success?.receipts.orEmpty().filter { it.items.isNotEmpty() }
            val foundRecipes = success?.recipes.orEmpty()
            if (found.isEmpty() && foundRecipes.isEmpty()) {
                ignored += name
                return
            }
            readCount++
            receipts += found
            recipes += foundRecipes
            if (found.isNotEmpty()) rawText.appendLine("===== $name =====").appendLine(success!!.rawText)
        }

        /** Abre um .zip e lê cada ficheiro de dentro (um zip dentro de um zip é ignorado). */
        suspend fun addZip(bytes: ByteArray, onProgress: (ImportProgress) -> Unit) {
            val contents = ZipExtractor().extract(bytes)
            contents.skipped.forEach(::skip)
            contents.files.forEachIndexed { i, file ->
                onProgress(ImportProgress(file.name, i + 1, contents.files.size))
                add(file.name, file.bytes, null)
            }
        }

        fun result(zipName: String?): ImportResult {
            if (receipts.isEmpty() && recipes.isEmpty()) {
                return ImportResult.Failure(
                    when {
                        zipName != null && fileCount == 0 -> "O ficheiro $zipName está vazio."
                        zipName != null -> "Não encontrei recibos nem receitas dentro de $zipName. Usa PDF, imagem, CSV, JSON, XML, HTML ou TXT."
                        else -> "Não consegui ler nenhum dos $fileCount ficheiros. Usa PDF, imagem, CSV, JSON, XML, HTML, TXT ou ZIP."
                    },
                )
            }
            return ImportResult.Success(
                receipts = receipts,
                rawText = rawText.toString().trimEnd(),
                type = if (zipName != null) ReceiptFileType.ZIP else ReceiptFileType.VARIOS,
                summary = ImportSummary(zipName, fileCount, readCount, ignored),
                recipes = recipes,
            )
        }
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
            ReceiptFileType.JSON ->
                // O mesmo .json pode ser um recibo ou uma receita de cozinha: o conteúdo decide.
                if (RecipeJsonParser.isRecipeFile(text)) {
                    val recipes = RecipeJsonParser.parse(text)
                    if (recipes.isEmpty()) ImportResult.Failure("Este ficheiro de receitas não tem nenhuma receita com nome e ingredientes.")
                    else ImportResult.Success(emptyList(), text, type, recipes = recipes)
                } else {
                    ImportResult.Success(JsonReceiptParser.parse(text), text, type)
                }
            ReceiptFileType.XML -> ImportResult.Success(XmlReceiptParser.parse(text), text, type)
            ReceiptFileType.ZIP -> ImportResult.Failure("Não abro um zip dentro de outro zip.")
            ReceiptFileType.VARIOS -> ImportResult.Failure("Tipo de ficheiro inválido.")
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

    companion object {
        /** Ficheiros escolhidos de uma vez (o mesmo limite dos ficheiros dentro de um .zip). */
        const val MAX_FILES = 50
    }
}
