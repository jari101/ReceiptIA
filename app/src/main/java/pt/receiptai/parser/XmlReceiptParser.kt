package pt.receiptai.parser

import org.w3c.dom.Element
import org.w3c.dom.Node
import pt.receiptai.domain.Money
import pt.receiptai.domain.parseQuantity
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Lê faturas eletrónicas em XML:
 *  - UBL 2.1 / CIUS-PT (fatura eletrónica europeia): <Invoice> com <cac:InvoiceLine>;
 *  - SAF-T PT (ficheiro das Finanças): <AuditFile> com <Invoice> e <Line>.
 */
object XmlReceiptParser {

    fun parse(text: String): List<ParsedReceipt> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Segurança: não carregar entidades externas (ataques XXE).
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(text.trim().toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement
        return when (root.localName ?: root.tagName) {
            "Invoice", "CreditNote" -> listOfNotNull(parseUbl(root))
            "AuditFile" -> parseSafT(root)
            else -> throw IllegalArgumentException("XML não reconhecido: ${root.tagName}")
        }
    }

    // ---------- UBL ----------
    private fun parseUbl(inv: Element): ParsedReceipt? {
        val store = inv.child("AccountingSupplierParty")?.let { sp ->
            sp.descendant("PartyName")?.childText("Name") ?: sp.descendant("PartyLegalEntity")?.childText("RegistrationName")
        } ?: "Loja desconhecida"
        val lines = inv.children("InvoiceLine") + inv.children("CreditNoteLine")
        val items = lines.mapNotNull { line ->
            val name = line.child("Item")?.childText("Name") ?: line.child("Item")?.childText("Description")
                ?: return@mapNotNull null
            val qty = (line.childText("InvoicedQuantity") ?: line.childText("CreditedQuantity"))?.let(::parseQuantity) ?: 1.0
            val unit = line.child("Price")?.childText("PriceAmount")?.let(Money::parseToCents)
            val total = line.childText("LineExtensionAmount")?.let(Money::parseToCents)
                ?: unit?.let { (it * qty).roundToLong() } ?: return@mapNotNull null
            ParsedItem(name, qty, unit ?: (total / qty).roundToLong(), total)
        }
        if (items.isEmpty()) return null
        val total = inv.child("LegalMonetaryTotal")?.let { it.childText("PayableAmount") ?: it.childText("TaxInclusiveAmount") }
        return ParsedReceipt(store, inv.childText("IssueDate")?.let(DateParsing::find), items, total?.let(Money::parseToCents))
    }

    // ---------- SAF-T PT ----------
    private fun parseSafT(root: Element): List<ParsedReceipt> {
        val company = root.child("Header")?.let { it.childText("CompanyName") ?: it.childText("BusinessName") }
            ?: "Loja desconhecida"
        val invoices = root.descendants("Invoice")
        return invoices.mapNotNull { inv ->
            val items = inv.children("Line").mapNotNull { line ->
                val name = line.childText("ProductDescription") ?: line.childText("Description") ?: return@mapNotNull null
                val qty = line.childText("Quantity")?.let(::parseQuantity) ?: 1.0
                val unit = line.childText("UnitPrice")?.let(Money::parseToCents)
                // No SAF-T os valores das linhas são SEM IVA; somamos o IVA da linha se existir.
                val net = (line.childText("CreditAmount") ?: line.childText("DebitAmount"))?.let(Money::parseToCents)
                    ?: unit?.let { (it * qty).roundToLong() } ?: return@mapNotNull null
                val taxPercent = line.child("Tax")?.childText("TaxPercentage")?.let(::parseQuantity) ?: 0.0
                val gross = (net * (1 + taxPercent / 100)).roundToLong()
                ParsedItem(name, qty, (gross / qty).roundToLong(), gross)
            }
            if (items.isEmpty()) return@mapNotNull null
            ParsedReceipt(
                storeName = company,
                date = inv.childText("InvoiceDate")?.let(DateParsing::find),
                items = items,
                totalCents = inv.child("DocumentTotals")?.childText("GrossTotal")?.let(Money::parseToCents),
            )
        }
    }

    // ---------- ajudas para navegar no XML (ignorando prefixos cbc:/cac:) ----------
    private val Node.name: String get() = localName ?: nodeName.substringAfter(':')
    private fun Element.children(name: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.name == name }
    private fun Element.child(name: String): Element? = children(name).firstOrNull()
    private fun Element.childText(name: String): String? = child(name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    private fun Element.descendants(name: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun Element.descendant(name: String): Element? = descendants(name).firstOrNull()
}
