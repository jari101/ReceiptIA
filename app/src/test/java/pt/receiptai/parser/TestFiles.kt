package pt.receiptai.parser

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Lê um ficheiro de docs/exemplos (configurado como recurso de teste no build.gradle.kts). */
fun exampleFile(name: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResource(name)) { "Falta o exemplo $name" }.readText()

/** O mesmo, mas em bytes (para ficheiros binários como o .zip de exemplo). */
fun exampleBytes(name: String): ByteArray =
    requireNotNull(object {}.javaClass.classLoader!!.getResource(name)) { "Falta o exemplo $name" }.readBytes()

/**
 * Cria um .zip em memória, por exemplo `zipOf("a.csv" to bytes, "pasta/" to null)`.
 * Um nome acabado em "/" com conteúdo null é uma pasta.
 */
fun zipOf(vararg files: Pair<String, ByteArray?>, charset: Charset = Charsets.UTF_8): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out, charset).use { zip ->
        for ((name, bytes) in files) {
            zip.putNextEntry(ZipEntry(name))
            if (bytes != null) zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
