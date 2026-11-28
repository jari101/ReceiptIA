package pt.receiptai.ui.records

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.receiptai.data.db.ReceiptEntity
import pt.receiptai.data.db.ReceiptWithItems
import pt.receiptai.domain.ReceiptSource
import java.time.LocalDate

class RecordsFocusTest {
    private fun receipt(id: Long, date: LocalDate) =
        ReceiptWithItems(ReceiptEntity(id, "Loja $id", date, 100, ReceiptSource.MANUAL), emptyList())

    @Test
    fun `posicao do recibo na lista dos registos`() {
        val groups = RecordsViewModel.group(
            listOf(
                receipt(10, LocalDate.of(2026, 11, 18)),
                receipt(11, LocalDate.of(2026, 11, 3)),
                receipt(12, LocalDate.of(2026, 10, 20)),
            ),
        )
        // 0 título · 1 pesquisa · 2 "Novembro" · 3 recibo 10 · 4 recibo 11 · 5 "Outubro" · 6 recibo 12
        assertEquals(3, RecordsViewModel.indexOf(groups, 10))
        assertEquals(4, RecordsViewModel.indexOf(groups, 11))
        assertEquals(6, RecordsViewModel.indexOf(groups, 12))
        assertNull(RecordsViewModel.indexOf(groups, 99))
    }
}
