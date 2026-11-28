package pt.receiptai.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import pt.receiptai.data.DemoData

/** A base de dados pessoal do cliente (fica guardada só no telemóvel). */
@Database(
    entities = [
        ReceiptEntity::class, ReceiptItemEntity::class, PantryItemEntity::class,
        RecipeEntity::class, RecipeIngredientEntity::class, ShoppingItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v1 -> v2: os ingredientes ganham a coluna "optional". O Room escreve o ALTER TABLE sozinho
    // (compara os esquemas guardados em app/schemas); nós só marcamos os opcionais das receitas da app.
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = ReceiptAiDatabase.MarkSeedOptionals::class)],
)
@TypeConverters(Converters::class)
abstract class ReceiptAiDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun pantryDao(): PantryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingDao(): ShoppingDao

    /**
     * Corre logo a seguir à migração 1 -> 2: nas receitas que vieram com a app (isCustom = 0),
     * marca como opcionais os mesmos ingredientes que o DemoData marca. As receitas criadas
     * pelo utilizador ficam como estavam (nenhum ingrediente opcional).
     */
    class MarkSeedOptionals : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            DemoData.recipes.forEach { recipe ->
                recipe.optional.forEach { ingredient ->
                    db.execSQL(
                        """UPDATE recipe_ingredients SET optional = 1
                           WHERE name = ? AND recipeId IN (SELECT id FROM recipes WHERE name = ? AND isCustom = 0)""",
                        arrayOf(ingredient, recipe.name),
                    )
                }
            }
        }
    }

    companion object {
        fun create(context: Context): ReceiptAiDatabase =
            Room.databaseBuilder(context, ReceiptAiDatabase::class.java, "receiptai.db").build()
    }
}
