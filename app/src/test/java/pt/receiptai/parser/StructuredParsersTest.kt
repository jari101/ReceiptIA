package pt.receiptai.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StructuredParsersTest {

    @Test
    fun `csv agrupa linhas por data e loja`() {
        val receipts = CsvReceiptParser.parse(exampleFile("recibos-exemplo.csv"))
        assertEquals(2, receipts.size)
        val mercado = receipts.first { it.storeName == "Mercado do Bairro" }
        assertEquals(LocalDate.of(2026, 11, 15), mercado.date)
        assertEquals(3, mercado.items.size)
        assertEquals("Queijo flamengo; fatias", mercado.items[2].name) // ";" dentro de aspas
        assertEquals(534L, mercado.items[0].totalCents) // 6 × 0,89
        assertEquals(155L, mercado.items[1].totalCents) // 1,2 × 1,29 = 1,548
    }

    @Test
    fun `csv com virgulas e coluna total`() {
        val csv = "date,store,name,qty,total\n2026-01-05,Loja X,Arroz,2,2.38\n"
        val r = CsvReceiptParser.parse(csv).single()
        assertEquals(238L, r.items.single().totalCents)
        assertEquals(119L, r.items.single().unitPriceCents)
    }

    @Test
    fun `json receiptai-v1`() {
        val r = JsonReceiptParser.parse(exampleFile("recibo-exemplo.json")).single()
        assertEquals("Mercado do Bairro", r.storeName)
        assertEquals(LocalDate.of(2026, 11, 18), r.date)
        assertEquals(625L, r.totalCents)
        assertEquals(625L, r.itemsTotalCents)
        assertEquals(337L, r.items[2].totalCents) // preço em texto "3,37"
    }

    @Test
    fun `json lista com chaves em ingles`() {
        val json = """[{"store":"Shop","date":"2026-02-01","items":[{"name":"Milk","quantity":1,"price":1.05}]}]"""
        val r = JsonReceiptParser.parse(json).single()
        assertEquals("Shop", r.storeName)
        assertEquals(105L, r.items.single().totalCents)
    }

    @Test
    fun `xml fatura ubl`() {
        val r = XmlReceiptParser.parse(exampleFile("fatura-ubl-exemplo.xml")).single()
        assertEquals("Frutaria Central", r.storeName)
        assertEquals(LocalDate.of(2026, 11, 16), r.date)
        assertEquals(listOf("Maçã gala", "Laranjas"), r.items.map { it.name })
        assertEquals(547L, r.totalCents)
        assertEquals(547L, r.itemsTotalCents)
    }

    @Test
    fun `xml saft soma o iva`() {
        val r = XmlReceiptParser.parse(exampleFile("saft-exemplo.xml")).single()
        assertEquals("Talho Silva", r.storeName)
        assertEquals(LocalDate.of(2026, 11, 14), r.date)
        assertEquals(890L, r.totalCents)
        assertEquals(890L, r.itemsTotalCents)
    }

    @Test(expected = Exception::class)
    fun `xml com doctype e recusado`() {
        XmlReceiptParser.parse("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><Invoice>&e;</Invoice>""")
    }
}
