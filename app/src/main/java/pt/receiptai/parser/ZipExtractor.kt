package pt.receiptai.parser

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Um ficheiro tirado de dentro do zip. */
class FileInZip(val name: String, val bytes: ByteArray)

/**
 * O que estava dentro do zip.
 * @param files ficheiros que vale a pena tentar ler como recibo.
 * @param skipped nomes dos ficheiros postos de parte logo à partida (zip dentro de zip, ficheiro grande demais).
 */
class ZipContents(val files: List<FileInZip>, val skipped: List<String>)

/** O zip passa um limite de segurança: a importação inteira pára e o utilizador vê esta mensagem. */
class ZipLimitException(message: String) : IllegalArgumentException(message)

/**
 * Descompacta um ficheiro .zip EM MEMÓRIA (nada é escrito no disco), com a biblioteca
 * `java.util.zip`, que já vem com o Android.
 *
 * Limites de segurança: um zip pequeno pode esconder gigabytes ("bomba zip"). Por isso
 * contamos os bytes que saem de facto e não confiamos no tamanho que o zip diz ter.
 */
class ZipExtractor(
    private val maxFiles: Int = 50,
    private val maxFileBytes: Int = 15 * 1024 * 1024,
    private val maxTotalBytes: Long = 50L * 1024 * 1024,
) {

    /**
     * @throws ZipLimitException se o zip passar um dos limites.
     * @throws ZipException se o zip estiver danificado ou tiver palavra-passe.
     */
    fun extract(bytes: ByteArray): ZipContents =
        try {
            readWithNameFallback(bytes)
        } catch (e: EOFException) {
            throw ZipException("O zip acaba a meio (ficheiro cortado).")
        }

    private fun readWithNameFallback(bytes: ByteArray): ZipContents =
        try {
            read(bytes, Charsets.UTF_8)
        } catch (e: ZipLimitException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // Zips antigos do Windows guardam os nomes noutra codificação e o UTF-8 falha.
            // A ISO-8859-1 aceita qualquer byte: no pior caso um "ç" aparece trocado no nome.
            read(bytes, Charsets.ISO_8859_1)
        }

    private fun read(bytes: ByteArray, charset: Charset): ZipContents {
        val files = mutableListOf<FileInZip>()
        val skipped = mutableListOf<String>()
        var total = 0L
        var anyEntry = false

        ZipInputStream(ByteArrayInputStream(bytes), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                anyEntry = true
                val path = entry.name
                if (entry.isDirectory || isJunk(path)) continue

                val name = path.substringAfterLast('/')
                if (files.size + skipped.size >= maxFiles) {
                    throw ZipLimitException("O zip tem demasiados ficheiros (máx. $maxFiles).")
                }
                if (name.substringAfterLast('.', "").lowercase() == "zip") {
                    skipped += name // não abrimos zips dentro de zips
                    continue
                }

                val content = zip.readUpTo(maxFileBytes)
                if (content == null) {
                    skipped += name
                    continue
                }
                total += content.size
                if (total > maxTotalBytes) {
                    throw ZipLimitException("O zip descompactado é demasiado grande (máx. ${maxTotalBytes / (1024 * 1024)} MB).")
                }
                files += FileInZip(name, content)
            }
        }
        // Um zip vazio a sério começa pelos bytes 50 4B 05 06. Sem nenhum ficheiro e sem essa marca, está estragado.
        if (!anyEntry && !bytes.startsWithEmptyZipMarker()) throw ZipException("O zip está danificado.")
        return ZipContents(files, skipped)
    }

    private fun ByteArray.startsWithEmptyZipMarker(): Boolean =
        size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4B.toByte() && this[2] == 0x05.toByte() && this[3] == 0x06.toByte()

    /** Lê o ficheiro atual do zip; devolve null se passar de [max] bytes. */
    private fun ZipInputStream.readUpTo(max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) return null
            out.write(buffer, 0, n)
        }
    }

    /** Lixo que o macOS e o Windows metem nos zips: não interessa ao utilizador. */
    private fun isJunk(path: String): Boolean {
        val name = path.substringAfterLast('/')
        return path.startsWith("__MACOSX/") ||
            name.startsWith(".") ||
            name.equals("Thumbs.db", ignoreCase = true) ||
            name.equals("desktop.ini", ignoreCase = true)
    }
}
