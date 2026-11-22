package pt.receiptai

import android.content.Context
import pt.receiptai.data.db.ReceiptAiDatabase
import pt.receiptai.data.repo.PantryRepository
import pt.receiptai.data.repo.ReceiptRepository
import pt.receiptai.data.repo.RecipeRepository
import pt.receiptai.data.repo.SettingsRepository
import pt.receiptai.data.repo.ShoppingRepository
import pt.receiptai.importer.ReceiptImporter
import java.time.LocalDate

/**
 * "Injeção de dependências" manual: cria cada peça da app UMA vez e partilha-a.
 * (Em apps maiores usa-se o Hilt, mas a ideia é exatamente esta.)
 */
class AppContainer(context: Context, val today: () -> LocalDate = { LocalDate.now() }) {
    val database = ReceiptAiDatabase.create(context)
    val receipts = ReceiptRepository(database, today)
    val pantry = PantryRepository(database, today)
    val recipes = RecipeRepository(database)
    val shopping = ShoppingRepository(database)
    val settings = SettingsRepository(context)
    val importer = ReceiptImporter(context)
}
