package pt.receiptai.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.domain.ReceiptSource
import java.time.LocalDate

/** Uma compra (um recibo). */
@Entity(tableName = "receipts", indices = [Index("purchasedOn")])
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeName: String,
    val purchasedOn: LocalDate,
    val totalCents: Long,
    val source: ReceiptSource,
    val rawText: String = "",
)

/** Um produto dentro de uma compra. Se a compra for apagada, os produtos também são (CASCADE). */
@Entity(
    tableName = "receipt_items",
    foreignKeys = [ForeignKey(ReceiptEntity::class, ["id"], ["receiptId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("receiptId"), Index("normalizedName")],
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long = 0,
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val unitPriceCents: Long,
    val totalCents: Long,
    val category: String,
)

/** Recibo + os seus produtos (o Room junta-os automaticamente). */
data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(parentColumn = "id", entityColumn = "receiptId") val items: List<ReceiptItemEntity>,
)

/** Linha usada nas estatísticas e na lista de compras automática. */
data class PurchaseRow(
    val receiptId: Long,
    val purchasedOn: LocalDate,
    val name: String,
    val normalizedName: String,
    val quantity: Double,
    val totalCents: Long,
)

/** Um ingrediente/produto que está em casa, com a data de validade. */
@Entity(tableName = "pantry_items", indices = [Index("expiresOn")])
data class PantryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val quantity: Double = 1.0,
    val addedOn: LocalDate,
    val expiresOn: LocalDate,
    val receiptItemId: Long? = null,
    val used: Boolean = false,
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: MealType,
    val minutes: Int,
    /** Passos separados por mudança de linha. */
    val steps: String,
    val tags: Set<DietTag>,
    val isCustom: Boolean = false,
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipeId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val name: String,
    val amount: String,
)

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId") val ingredients: List<RecipeIngredientEntity>,
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val checked: Boolean = false,
    /** true se foi sugerido automaticamente pelo histórico de compras. */
    val isAuto: Boolean = false,
)
