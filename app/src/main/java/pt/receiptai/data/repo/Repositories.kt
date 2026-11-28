package pt.receiptai.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.receiptai.data.DemoData
import pt.receiptai.data.db.PantryItemEntity
import pt.receiptai.data.db.ReceiptAiDatabase
import pt.receiptai.data.db.ReceiptEntity
import pt.receiptai.data.db.ReceiptItemEntity
import pt.receiptai.data.db.RecipeEntity
import pt.receiptai.data.db.RecipeIngredientEntity
import pt.receiptai.data.db.RecipeWithIngredients
import pt.receiptai.data.db.ShoppingItemEntity
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.domain.Normalizer
import pt.receiptai.domain.PurchaseLine
import pt.receiptai.domain.ReceiptSource
import pt.receiptai.domain.ReceiptSummary
import pt.receiptai.domain.RecipeInfo
import pt.receiptai.domain.ShelfLife
import pt.receiptai.parser.ParsedReceipt
import pt.receiptai.parser.ParsedRecipe
import java.time.LocalDate

/**
 * Repositórios: a única "porta" entre os ecrãs (ViewModels) e a base de dados.
 * Os ecrãs nunca falam diretamente com o Room.
 */
class ReceiptRepository(private val db: ReceiptAiDatabase, private val today: () -> LocalDate) {
    private val dao = db.receiptDao()

    val receipts = dao.observeAll()
    val latest = dao.observeLatest()
    val count = dao.observeCount()
    val receiptTotals: Flow<List<Pair<LocalDate, Long>>> =
        dao.observeReceipts().map { list -> list.map { it.purchasedOn to it.totalCents } }
    val receiptSummaries: Flow<List<ReceiptSummary>> = dao.observeReceiptSummaries().map { rows ->
        rows.map { ReceiptSummary(it.id, it.purchasedOn, it.storeName, it.totalCents, it.itemCount) }
    }
    val purchaseLines: Flow<List<PurchaseLine>> = dao.observePurchaseRows().map { rows ->
        rows.map { PurchaseLine(it.receiptId, it.purchasedOn, it.name, it.normalizedName, it.quantity, it.totalCents) }
    }

    /**
     * Guarda recibos lidos (depois de o utilizador os rever) e põe os alimentos na Despensa
     * com a validade estimada. Devolve quantos recibos foram guardados.
     */
    suspend fun saveParsed(
        receipts: List<ParsedReceipt>,
        source: ReceiptSource,
        rawText: String = "",
        /** Só os recibos destes últimos dias enchem a despensa (um recibo antigo já foi comido!). */
        pantryWindowDays: Long = 30,
    ): Int = db.withTransaction {
        var saved = 0
        for (r in receipts) {
            if (r.items.isEmpty()) continue
            val date = r.date ?: today()
            val items = r.items.map {
                ReceiptItemEntity(
                    name = it.name.trim(),
                    normalizedName = Normalizer.normalize(it.name),
                    quantity = it.quantity,
                    unitPriceCents = it.unitPriceCents,
                    totalCents = it.totalCents,
                    category = ShelfLife.category(it.name).name,
                )
            }
            val receipt = ReceiptEntity(
                storeName = r.storeName.trim().ifBlank { "Loja desconhecida" },
                purchasedOn = date,
                totalCents = items.sumOf { it.totalCents },
                source = source,
                rawText = rawText,
            )
            val inserted = dao.insertWithItems(receipt, items)
            // Só os alimentos comprados recentemente entram na despensa.
            if (date >= today().minusDays(pantryWindowDays)) {
                db.pantryDao().insertAll(
                    inserted.filter { ShelfLife.isFood(it.name) }.map {
                        PantryItemEntity(
                            name = it.name,
                            normalizedName = it.normalizedName,
                            quantity = it.quantity,
                            addedOn = date,
                            expiresOn = ShelfLife.estimateExpiry(it.name, date),
                            receiptItemId = it.id,
                        )
                    },
                )
            }
            saved++
        }
        saved
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** "Experimentar com dados de exemplo". */
    suspend fun loadDemoData() {
        saveParsed(DemoData.receipts(today()), ReceiptSource.EXEMPLO, pantryWindowDays = 7)
    }
}

class PantryRepository(private val db: ReceiptAiDatabase, private val today: () -> LocalDate) {
    private val dao = db.pantryDao()
    val active = dao.observeActive()

    suspend fun markUsed(id: Long) = dao.markUsed(id)
    suspend fun updateExpiry(id: Long, date: LocalDate) = dao.updateExpiry(id, date)
    suspend fun expiringWithin(days: Long) = dao.expiringBy(today().plusDays(days))

    suspend fun addManual(name: String, expiresOn: LocalDate? = null) {
        dao.insert(
            PantryItemEntity(
                name = name.trim(),
                normalizedName = Normalizer.normalize(name),
                addedOn = today(),
                expiresOn = expiresOn ?: ShelfLife.estimateExpiry(name, today()),
            ),
        )
    }
}

/** Um ingrediente escrito no formulário "Nova receita" (ou lido de um ficheiro de receita). */
data class IngredientInput(val name: String, val amount: String = "", val optional: Boolean = false)

class RecipeRepository(private val db: ReceiptAiDatabase) {
    private val dao = db.recipeDao()
    val recipes: Flow<List<RecipeWithIngredients>> = dao.observeAll()

    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        DemoData.recipes.forEach { r ->
            dao.insertWithIngredients(
                RecipeEntity(name = r.name, mealType = r.mealType, minutes = r.minutes, steps = r.steps.joinToString("\n"), tags = r.tags),
                r.ingredients.map { (n, a) -> RecipeIngredientEntity(name = n, amount = a, optional = n in r.optional) },
            )
        }
    }

    suspend fun add(
        name: String,
        mealType: MealType,
        minutes: Int,
        tags: Set<DietTag>,
        ingredients: List<IngredientInput>,
        steps: List<String>,
    ): Long = dao.insertWithIngredients(
        RecipeEntity(name = name.trim(), mealType = mealType, minutes = minutes, steps = steps.joinToString("\n"), tags = tags, isCustom = true),
        ingredients.map { RecipeIngredientEntity(name = it.name.trim(), amount = it.amount.trim(), optional = it.optional) },
    )

    /**
     * Guarda receitas lidas de ficheiros (ex.: dentro de um .zip). Uma receita com o mesmo nome
     * de outra que já existe é saltada, para importar o mesmo ficheiro 2 vezes não duplicar nada.
     */
    suspend fun addImported(recipes: List<ParsedRecipe>): RecipeImportCount = db.withTransaction {
        val existing = dao.names().map(Normalizer::normalize).toMutableSet()
        var added = 0
        for (r in recipes) {
            if (!existing.add(Normalizer.normalize(r.name))) continue
            dao.insertWithIngredients(
                RecipeEntity(
                    name = r.name.trim(), mealType = r.mealType, minutes = r.minutes,
                    steps = r.steps.joinToString("\n"), tags = r.tags, isCustom = true,
                ),
                r.ingredients.map { RecipeIngredientEntity(name = it.name.trim(), amount = it.amount.trim(), optional = it.optional) },
            )
            added++
        }
        RecipeImportCount(added = added, alreadyExisted = recipes.size - added)
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Quantas receitas importadas entraram e quantas já existiam (e foram saltadas). */
data class RecipeImportCount(val added: Int, val alreadyExisted: Int)

fun RecipeWithIngredients.toInfo() = RecipeInfo(
    id = recipe.id,
    name = recipe.name,
    mealType = recipe.mealType,
    minutes = recipe.minutes,
    tags = recipe.tags,
    ingredients = ingredients.map { it.name },
    optional = ingredients.filter { it.optional }.map { it.name }.toSet(),
)

class ShoppingRepository(private val db: ReceiptAiDatabase) {
    private val dao = db.shoppingDao()
    val items = dao.observeAll()

    /** Junta um produto à lista. Devolve o id novo (serve para o "Anular"), ou null se o nome estiver vazio. */
    suspend fun add(name: String, isAuto: Boolean = false): Long? {
        if (name.isBlank()) return null
        return dao.insert(ShoppingItemEntity(name = name.trim(), normalizedName = Normalizer.normalize(name), isAuto = isAuto))
    }
    suspend fun addAll(names: List<String>, isAuto: Boolean) =
        dao.insertAll(names.filter { it.isNotBlank() }.map { ShoppingItemEntity(name = it.trim(), normalizedName = Normalizer.normalize(it), isAuto = isAuto) })
    suspend fun toggle(item: ShoppingItemEntity) = dao.update(item.copy(checked = !item.checked))
    suspend fun delete(item: ShoppingItemEntity) = dao.delete(item)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun clearChecked() = dao.deleteChecked()
}
