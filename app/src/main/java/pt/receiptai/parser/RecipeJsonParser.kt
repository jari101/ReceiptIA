package pt.receiptai.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import pt.receiptai.domain.DietTag
import pt.receiptai.domain.MealType
import pt.receiptai.domain.Normalizer

/** Um ingrediente lido de um ficheiro de receita. */
data class ParsedIngredient(val name: String, val amount: String = "", val optional: Boolean = false)

/** Uma receita lida de um ficheiro (ainda não está na base de dados). */
data class ParsedRecipe(
    val name: String,
    val mealType: MealType,
    val minutes: Int,
    val tags: Set<DietTag>,
    val ingredients: List<ParsedIngredient>,
    val steps: List<String>,
)

/**
 * Lê receitas em JSON, no formato "receiptai-receita-v1" (ver docs/formato-receita.md):
 * ```json
 * {
 *   "formato": "receiptai-receita-v1",
 *   "nome": "Omelete de queijo",
 *   "refeicao": "pequeno-almoco",
 *   "minutos": 10,
 *   "dietas": ["vegetariano", "sem-gluten"],
 *   "ingredientes": [
 *     { "nome": "Ovos", "quantidade": "3" },
 *     { "nome": "Queijo", "quantidade": "2 fatias", "opcional": true }
 *   ],
 *   "passos": ["Bate os ovos.", "Cozinha em lume brando."]
 * }
 * ```
 * Também aceita uma lista de receitas, {"receitas": [...]}, ingredientes escritos só como texto
 * ("Ovos") e chaves em inglês (name, ingredients, steps...).
 */
object RecipeJsonParser {
    const val FORMAT = "receiptai-receita-v1"

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * Este texto é um ficheiro de RECEITAS (e não de recibos)?
     * Sim se disser "formato": "receiptai-receita-v1", se tiver uma lista "receitas",
     * ou se as entradas tiverem "ingredientes".
     */
    fun isRecipeFile(text: String): Boolean {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return false
        return entries(root).any { it.text("formato", "format") == FORMAT || it.field("ingredientes", "ingredients") != null }
    }

    /** As receitas válidas do ficheiro (com nome e pelo menos um ingrediente). */
    fun parse(text: String): List<ParsedRecipe> = entries(json.parseToJsonElement(text)).mapNotNull(::parseRecipe)

    private fun entries(root: JsonElement): List<JsonObject> = when (root) {
        is JsonArray -> root.filterIsInstance<JsonObject>()
        is JsonObject -> (root.field("receitas", "recipes") as? JsonArray)?.filterIsInstance<JsonObject>() ?: listOf(root)
        else -> emptyList()
    }

    private fun parseRecipe(o: JsonObject): ParsedRecipe? {
        val name = o.text("nome", "name", "titulo", "title") ?: return null
        val ingredients = (o.field("ingredientes", "ingredients") as? JsonArray).orEmpty().mapNotNull { element ->
            when (element) {
                // "Ovos" (só o nome)
                is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { ParsedIngredient(it) }
                // { "nome": "Queijo", "quantidade": "2 fatias", "opcional": true }
                is JsonObject -> {
                    val ingName = element.text("nome", "name") ?: return@mapNotNull null
                    ParsedIngredient(
                        name = ingName,
                        amount = element.text("quantidade", "quantity", "amount").orEmpty(),
                        optional = element.flag("opcional", "optional"),
                    )
                }
                else -> null
            }
        }
        if (ingredients.isEmpty()) return null
        return ParsedRecipe(
            name = name,
            mealType = mealType(o.text("refeicao", "tipo", "meal", "meal_type")),
            minutes = (o.field("minutos", "tempo", "minutes", "time") as? JsonPrimitive)
                ?.let { it.intOrNull ?: it.contentOrNull?.filter(Char::isDigit)?.toIntOrNull() } ?: 0,
            tags = (o.field("dietas", "dieta", "diets", "tags") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::dietTag) }.toSet(),
            ingredients = ingredients,
            steps = steps(o.field("passos", "steps", "preparacao", "instructions")),
        )
    }

    /** Os passos podem vir como lista ou como um texto só, um passo por linha. */
    private fun steps(element: JsonElement?): List<String> = when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        is JsonPrimitive -> element.contentOrNull.orEmpty().lines().map { it.trim() }
        else -> emptyList()
    }.filter { it.isNotEmpty() }

    /** "Pequeno-almoço", "pequeno_almoco", "breakfast"… -> MealType. Sem tipo (ou desconhecido): jantar. */
    private fun mealType(text: String?): MealType = when (key(text)) {
        "pequenoalmoco", "breakfast" -> MealType.PEQUENO_ALMOCO
        "lanche", "snack" -> MealType.LANCHE
        "almoco", "lunch" -> MealType.ALMOCO
        else -> MealType.JANTAR
    }

    private fun dietTag(text: String): DietTag? = when (key(text)) {
        "diabeticos", "diabetico", "diabetic" -> DietTag.DIABETICOS
        "ginasio", "gym", "fitness" -> DietTag.GINASIO
        "semgluten", "glutenfree" -> DietTag.SEM_GLUTEN
        "semlactose", "lactosefree" -> DietTag.SEM_LACTOSE
        "vegetariano", "vegetarian" -> DietTag.VEGETARIANO
        else -> null
    }

    /** Tira acentos, maiúsculas, espaços e hífenes: "Sem glúten" e "sem-gluten" ficam "semgluten". */
    private fun key(text: String?): String = Normalizer.normalize(text.orEmpty()).filter(Char::isLetterOrDigit)

    private fun JsonObject.field(vararg names: String): JsonElement? = names.firstNotNullOfOrNull { this[it] }
    private fun JsonObject.text(vararg names: String): String? =
        (field(*names) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.flag(vararg names: String): Boolean {
        val p = field(*names) as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: (key(p.contentOrNull) in setOf("sim", "yes", "true", "1"))
    }
}
