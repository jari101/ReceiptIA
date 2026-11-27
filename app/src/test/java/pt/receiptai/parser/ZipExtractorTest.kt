package pt.receiptai.parser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipException
import kotlin.random.Random

class ZipExtractorTest {

    @Test
    fun `descompacta os ficheiros e ignora pastas e o lixo do macOS e do Windows`() {
        val csv = "data;loja;produto;quantidade;preco".toByteArray()
        val zip = zipOf(
            "recibos/" to null,
            "recibos/compras.csv" to csv,
            "__MACOSX/recibos/._compras.csv" to byteArrayOf(1, 2, 3),
            ".DS_Store" to byteArrayOf(0),
            "Thumbs.db" to byteArrayOf(0),
        )

        val contents = ZipExtractor().extract(zip)

        assertEquals(listOf("compras.csv"), contents.files.map { it.name })
        assertArrayEquals(csv, contents.files.single().bytes)
        assertTrue(contents.skipped.isEmpty())
    }

    @Test
    fun `poe de parte zips dentro de zips e ficheiros grandes demais`() {
        val zip = zipOf(
            "outro.zip" to zipOf("x.txt" to "x".toByteArray()),
            "grande.txt" to ByteArray(20) { 'a'.code.toByte() },
            "pequeno.txt" to "LEITE 0,89".toByteArray(),
        )

        val contents = ZipExtractor(maxFileBytes = 10).extract(zip)

        assertEquals(listOf("pequeno.txt"), contents.files.map { it.name })
        assertEquals(listOf("outro.zip", "grande.txt"), contents.skipped)
    }

    @Test
    fun `para quando o zip tem ficheiros a mais`() {
        val zip = zipOf(*Array(4) { "recibo$it.txt" to "LEITE 0,89".toByteArray() })
        val error = assertThrows(ZipLimitException::class.java) { ZipExtractor(maxFiles = 3).extract(zip) }
        assertEquals("O zip tem demasiados ficheiros (máx. 3).", error.message)
    }

    @Test
    fun `para uma bomba zip (pouco comprimido, muito descompactado)`() {
        // 2 x 1 MB de zeros ficam com poucos KB dentro do zip.
        val zip = zipOf("a.txt" to ByteArray(1024 * 1024), "b.txt" to ByteArray(1024 * 1024))
        assertTrue(zip.size < 20_000)
        assertThrows(ZipLimitException::class.java) { ZipExtractor(maxTotalBytes = 1_500_000).extract(zip) }
    }

    @Test
    fun `le nomes com acentos de zips antigos do Windows`() {
        val zip = zipOf("março.csv" to "a;b".toByteArray(), charset = Charsets.ISO_8859_1)
        assertEquals("março.csv", ZipExtractor().extract(zip).files.single().name)
    }

    @Test
    fun `um zip vazio nao tem ficheiros`() {
        assertTrue(ZipExtractor().extract(zipOf()).files.isEmpty())
    }

    @Test
    fun `zip estragado, cortado ou com palavra-passe da ZipException`() {
        // Bytes aleatórios quase não comprimem: assim, cortar o zip a meio corta mesmo os dados.
        val good = zipOf("a.bin" to Random(42).nextBytes(4000))

        assertThrows(ZipException::class.java) { ZipExtractor().extract("PK\u0003\u0004 isto não é um zip".toByteArray()) }
        assertThrows(ZipException::class.java) { ZipExtractor().extract(good.copyOf(good.size / 2)) }

        // O bit 0 da "flag" no cabeçalho diz que o ficheiro está cifrado (tem palavra-passe).
        val encrypted = good.copyOf().also { it[6] = (it[6].toInt() or 1).toByte() }
        val error = assertThrows(ZipException::class.java) { ZipExtractor().extract(encrypted) }
        assertTrue(error.message!!.contains("encrypt", ignoreCase = true))
    }
}
