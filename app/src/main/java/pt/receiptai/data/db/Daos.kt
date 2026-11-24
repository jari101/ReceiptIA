package pt.receiptai.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ReceiptDao {
    @Transaction
    @Query("SELECT * FROM receipts ORDER BY purchasedOn DESC, id DESC")
    fun observeAll(): Flow<List<ReceiptWithItems>>

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY purchasedOn DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<ReceiptWithItems?>

    @Query("SELECT COUNT(*) FROM receipts")
    fun observeCount(): Flow<Int>

    /** (data, total) de todos os recibos — chega para os gráficos. */
    @Query("SELECT * FROM receipts")
    fun observeReceipts(): Flow<List<ReceiptEntity>>

    @Query(
        """SELECT i.receiptId AS receiptId, r.purchasedOn AS purchasedOn, i.name AS name,
                  i.normalizedName AS normalizedName, i.quantity AS quantity, i.totalCents AS totalCents
           FROM receipt_items i JOIN receipts r ON r.id = i.receiptId""",
    )
    fun observePurchaseRows(): Flow<List<PurchaseRow>>

    @Insert suspend fun insertReceipt(receipt: ReceiptEntity): Long
    @Insert suspend fun insertItems(items: List<ReceiptItemEntity>): List<Long>

    /** Guarda o recibo e os produtos de uma só vez (se algo falhar, nada fica guardado). */
    @Transaction
    suspend fun insertWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): List<ReceiptItemEntity> {
        val id = insertReceipt(receipt)
        val withId = items.map { it.copy(receiptId = id) }
        val ids = insertItems(withId)
        return withId.mapIndexed { i, item -> item.copy(id = ids[i]) }
    }

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items WHERE used = 0 ORDER BY expiresOn ASC, name ASC")
    fun observeActive(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE used = 0 AND expiresOn <= :limit ORDER BY expiresOn ASC")
    suspend fun expiringBy(limit: LocalDate): List<PantryItemEntity>

    @Insert suspend fun insertAll(items: List<PantryItemEntity>)
    @Insert suspend fun insert(item: PantryItemEntity): Long
    @Update suspend fun update(item: PantryItemEntity)

    @Query("UPDATE pantry_items SET used = 1 WHERE id = :id")
    suspend fun markUsed(id: Long)

    @Query("UPDATE pantry_items SET expiresOn = :date WHERE id = :id")
    suspend fun updateExpiry(id: Long, date: LocalDate)
}

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name")
    fun observeAll(): Flow<List<RecipeWithIngredients>>

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    @Insert suspend fun insertRecipe(recipe: RecipeEntity): Long
    @Insert suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Transaction
    suspend fun insertWithIngredients(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>): Long {
        val id = insertRecipe(recipe)
        insertIngredients(ingredients.map { it.copy(recipeId = id) })
        return id
    }

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, id DESC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Insert suspend fun insert(item: ShoppingItemEntity): Long
    @Insert suspend fun insertAll(items: List<ShoppingItemEntity>)
    @Update suspend fun update(item: ShoppingItemEntity)
    @Delete suspend fun delete(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun deleteChecked()
}
