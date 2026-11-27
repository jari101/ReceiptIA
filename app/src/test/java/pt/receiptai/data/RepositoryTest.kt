package pt.receiptai.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.receiptai.data.db.ReceiptAiDatabase
import pt.receiptai.data.repo.ReceiptRepository
import pt.receiptai.data.repo.RecipeRepository
import pt.receiptai.data.repo.ShoppingRepository
import pt.receiptai.domain.ReceiptSource
import pt.receiptai.parser.ReceiptTextParser
import pt.receiptai.parser.exampleFile
import java.time.LocalDate

/** Testa a base de dados verdadeira (Room) dentro do JVM, com o Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class RepositoryTest {
    private val today = LocalDate.of(2026, 11, 19)
    private lateinit var db: ReceiptAiDatabase
    private lateinit var receipts: ReceiptRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReceiptAiDatabase::class.java)
            .allowMainThreadQueries().build()
        receipts = ReceiptRepository(db) { today }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `guardar recibo cria produtos e despensa`() = runBlocking {
        val parsed = ReceiptTextParser.parse(exampleFile("recibo-exemplo.txt"))
        assertEquals(1, receipts.saveParsed(listOf(parsed), ReceiptSource.FICHEIRO))

        val saved = receipts.receipts.first().single()
        assertEquals(1442L, saved.receipt.totalCents)
        assertEquals(7, saved.items.size)

        val pantry = db.pantryDao().observeActive().first()
        // O detergente não é comida, por isso não vai para a despensa.
        assertEquals(6, pantry.size)
        assertTrue(pantry.none { it.name.startsWith("Detergente") })
        assertEquals(LocalDate.of(2026, 11, 25), pantry.first { it.name.startsWith("Leite") }.expiresOn)
    }

    @Test
    fun `apagar recibo apaga os produtos`() = runBlocking {
        receipts.saveParsed(listOf(ReceiptTextParser.parse(exampleFile("recibo-exemplo.txt"))), ReceiptSource.FICHEIRO)
        val id = receipts.receipts.first().single().receipt.id
        receipts.delete(id)
        assertTrue(receipts.receipts.first().isEmpty())
        assertTrue(receipts.purchaseLines.first().isEmpty())
    }

    @Test
    fun `dados de exemplo`() = runBlocking {
        receipts.loadDemoData()
        val all = receipts.receipts.first()
        assertTrue(all.size > 20)
        assertTrue(all.all { it.receipt.purchasedOn <= today })
        // Só as compras dos últimos 7 dias enchem a despensa.
        val pantry = db.pantryDao().observeActive().first()
        assertTrue(pantry.isNotEmpty())
        assertTrue(pantry.all { it.addedOn >= today.minusDays(7) })
    }

    @Test
    fun `receitas sao carregadas uma so vez`() = runBlocking {
        val recipes = RecipeRepository(db)
        recipes.seedIfEmpty()
        recipes.seedIfEmpty()
        assertEquals(DemoData.recipes.size, recipes.recipes.first().size)
    }

    @Test
    fun `lista de compras`() = runBlocking {
        val shopping = ShoppingRepository(db)
        shopping.add("Leite")
        shopping.add("Pão")
        shopping.toggle(shopping.items.first().first { it.name == "Leite" })
        shopping.clearChecked()
        assertEquals(listOf("Pão"), shopping.items.first().map { it.name })

        // "Anular": o id devolvido pelo add serve para tirar logo o produto da lista.
        val id = shopping.add("Bananas")!!
        assertEquals(listOf("Bananas", "Pão"), shopping.items.first().map { it.name })
        shopping.deleteById(id)
        assertEquals(listOf("Pão"), shopping.items.first().map { it.name })
        assertEquals(null, shopping.add("   "))
    }
}
