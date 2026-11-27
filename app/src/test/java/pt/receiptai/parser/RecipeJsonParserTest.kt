package pt.receiptai.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType

class RecipeJsonParserTest {

    @Test
    fun `le a receita de exemplo`() {
        val text = exampleFile("receita-exemplo.json")
        assertTrue(RecipeJsonParser.isRecipeFile(text))
        val r = RecipeJsonParser.parse(text).single()
        assertEquals("Bacalhau à Brás", r.name)
        assertEquals(MealType.JANTAR, r.mealType)
        assertEquals(30, r.minutes)
        assertEquals(setOf(DietTag.SEM_GLUTEN, DietTag.SEM_LACTOSE), r.tags)
        assertEquals(6, r.ingredients.size)
        assertEquals(ParsedIngredient("Bacalhau desfiado", "300 g"), r.ingredients.first())
        assertEquals(listOf("Azeitonas", "Salsa"), r.ingredients.filter { it.optional }.map { it.name })
        assertEquals(4, r.steps.size)
    }

    @Test
    fun `lista de receitas com acentos, passos num texto e ingredientes so com o nome`() {
        val (pato, torradas) = RecipeJsonParser.parse(exampleFile("receitas-exemplo.json"))
        assertEquals(MealType.ALMOCO, pato.mealType) // "almoço"
        assertEquals(setOf(DietTag.SEM_GLUTEN), pato.tags) // "Sem glúten"
        assertEquals(3, pato.steps.size) // texto com 3 linhas
        assertEquals(MealType.PEQUENO_ALMOCO, torradas.mealType) // "pequeno-almoço"
        assertEquals(listOf("Pão", "Abacate", "Limão"), torradas.ingredients.map { it.name })
        assertEquals(listOf(false, false, true), torradas.ingredients.map { it.optional })
    }

    @Test
    fun `chaves em ingles e receitas sem ingredientes sao ignoradas`() {
        val text = """
            [
              { "name": "Pancakes", "meal": "breakfast", "minutes": "15 min", "diets": ["vegetarian"],
                "ingredients": [ { "name": "Eggs", "quantity": "2" }, { "name": "Honey", "optional": "yes" } ],
                "steps": ["Mix.", "Fry."] },
              { "name": "Sem ingredientes", "ingredients": [] },
              { "ingredients": ["Sal"] }
            ]
        """.trimIndent()
        val r = RecipeJsonParser.parse(text).single()
        assertEquals("Pancakes", r.name)
        assertEquals(MealType.PEQUENO_ALMOCO, r.mealType)
        assertEquals(15, r.minutes)
        assertEquals(setOf(DietTag.VEGETARIANO), r.tags)
        assertEquals(listOf(false, true), r.ingredients.map { it.optional })
    }

    @Test
    fun `um recibo em JSON nao e uma receita`() {
        assertFalse(RecipeJsonParser.isRecipeFile(exampleFile("recibo-exemplo.json")))
        assertFalse(RecipeJsonParser.isRecipeFile("isto não é JSON"))
        // Sem "formato" mas com ingredientes: também é receita.
        assertTrue(RecipeJsonParser.isRecipeFile("""{ "nome": "Sopa", "ingredientes": ["Água"] }"""))
    }
}
