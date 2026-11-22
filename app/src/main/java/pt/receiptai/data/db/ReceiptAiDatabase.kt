package pt.receiptai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** A base de dados pessoal do cliente (fica guardada só no telemóvel). */
@Database(
    entities = [
        ReceiptEntity::class, ReceiptItemEntity::class, PantryItemEntity::class,
        RecipeEntity::class, RecipeIngredientEntity::class, ShoppingItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ReceiptAiDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun pantryDao(): PantryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        fun create(context: Context): ReceiptAiDatabase =
            Room.databaseBuilder(context, ReceiptAiDatabase::class.java, "receiptai.db").build()
    }
}
