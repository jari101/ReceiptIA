package pt.receiptai.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptTextParserTest {

    @Test
    fun `le o recibo de exemplo completo`() {
        val r = ReceiptTextParser.parse(exampleFile("recibo-exemplo.txt"))

        assertEquals("Mercado do bairro", r.storeName)
        assertEquals(LocalDate.of(2026, 11, 18), r.date)
        assertEquals(1442L, r.totalCents)
        assertEquals(
            listOf("Leite meio gordo 1l", "Iogurte natural 4x125g", "Banana", "Pao de forma integral", "Ovos classe m 12un", "Atum em lata 3x80g", "Detergente loica"),
            r.items.map { it.name },
        )
        // A soma dos produtos bate certo com o TOTAL impresso (inclui o desconto de 0,30 nos ovos).
        assertEquals(r.totalCents, r.itemsTotalCents)
    }

    @Test
    fun `quantidade na linha seguinte`() {
        val r = ReceiptTextParser.parse(exampleFile("recibo-exemplo.txt"))
        val iogurte = r.items.first { it.name.startsWith("Iogurte") }
        assertEquals(2.0, iogurte.quantity, 0.0)
        assertEquals(149L, iogurte.unitPriceCents)
        assertEquals(298L, iogurte.totalCents)

        val banana = r.items.first { it.name == "Banana" }
        assertEquals(0.85, banana.quantity, 0.0001)
        assertEquals(101L, banana.totalCents)
    }

    @Test
    fun `desconto abate ao produto anterior`() {
        val r = ReceiptTextParser.parse(exampleFile("recibo-exemplo.txt"))
        assertEquals(229L, r.items.first { it.name.startsWith("Ovos") }.totalCents)
    }

    @Test
    fun `quantidade na mesma linha e precos com euro`() {
        val text = """
            PADARIA AURORA
            Data: 03/11/26
            PAPO SECO 6 X 0,15 € 0,90 €
            CROISSANT 0,95€
            TOTAL 1,85 €
        """.trimIndent()
        val r = ReceiptTextParser.parse(text)
        assertEquals("Padaria aurora", r.storeName)
        assertEquals(LocalDate.of(2026, 11, 3), r.date)
        assertEquals(2, r.items.size)
        assertEquals(6.0, r.items[0].quantity, 0.0)
        assertEquals(90L, r.items[0].totalCents)
        assertEquals(185L, r.totalCents)
    }

    @Test
    fun `texto sem produtos devolve lista vazia`() {
        val r = ReceiptTextParser.parse("Olá mundo\nisto não é um recibo")
        assertTrue(r.items.isEmpty())
    }

    @Test
    fun `recibo em html`() {
        val r = ReceiptTextParser.parse(HtmlText.toText(exampleFile("recibo-exemplo.html")))
        assertEquals("Mercado do Bairro", r.storeName)
        assertEquals(LocalDate.of(2026, 11, 12), r.date)
        assertEquals(listOf("Leite meio gordo 1L", "Café moído 250g"), r.items.map { it.name })
        assertEquals(418L, r.totalCents)
    }
}
