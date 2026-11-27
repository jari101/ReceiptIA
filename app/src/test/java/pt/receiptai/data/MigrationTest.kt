package pt.receiptai.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.receiptai.data.db.ReceiptAiDatabase
import java.io.File

/**
 * Simula um telemóvel que já tinha a app com a base de dados na VERSÃO 1
 * e verifica que, ao abrir a versão 2, nada se perde e os opcionais ficam marcados.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val name = "migracao_teste.db"

    /** Cria as tabelas exatamente como eram na versão 1 (lidas de app/schemas/.../1.json). */
    private fun createVersion1() {
        context.deleteDatabase(name)
        val schema = Json.parseToJsonElement(File("schemas/pt.receiptai.data.db.ReceiptAiDatabase/1.json").readText())
        val entities = schema.jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in entities) {
                val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
                db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            // Uma receita que veio com a app e uma criada pelo utilizador, ambas com queijo.
            db.execSQL("INSERT INTO recipes VALUES (1, 'Omelete de espinafres', 'PEQUENO_ALMOCO', 15, 'Bate os ovos.', 'VEGETARIANO', 0)")
            db.execSQL("INSERT INTO recipes VALUES (2, 'A minha omelete', 'JANTAR', 10, 'Cozinha.', '', 1)")
            db.execSQL("INSERT INTO recipe_ingredients VALUES (1, 1, 'Ovos', '3'), (2, 1, 'Queijo', '2 fatias'), (3, 2, 'Queijo', '1 fatia')")
            db.version = 1
        }
    }

    @Test
    fun `versao 1 para 2 guarda as receitas e marca os opcionais da app`() = runBlocking {
        createVersion1()
        val room = Room.databaseBuilder(context, ReceiptAiDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val recipes = room.recipeDao().observeAll().first().associateBy { it.recipe.name }
            assertEquals(2, recipes.size)

            val seed = recipes.getValue("Omelete de espinafres").ingredients.associate { it.name to it.optional }
            assertEquals(mapOf("Ovos" to false, "Queijo" to true), seed)

            // A receita do utilizador não muda (não sabemos o que ele considera opcional).
            val mine = recipes.getValue("A minha omelete").ingredients.single()
            assertEquals(false, mine.optional)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
