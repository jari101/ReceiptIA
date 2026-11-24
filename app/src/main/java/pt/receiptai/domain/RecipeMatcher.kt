package pt.receiptai.domain

/** Dados mínimos de uma receita para a comparação (sem depender da base de dados). */
data class RecipeInfo(
    val id: Long,
    val name: String,
    val mealType: MealType,
    val minutes: Int,
    val tags: Set<DietTag>,
    val ingredients: List<String>,
)

data class RecipeMatch(
    val recipe: RecipeInfo,
    val have: List<String>,
    val missing: List<String>,
) {
    /** Percentagem de ingredientes que já existem em casa (0..1). */
    val coverage: Float get() = if (recipe.ingredients.isEmpty()) 0f else have.size.toFloat() / recipe.ingredients.size
}

/**
 * "O que posso cozinhar?": compara os ingredientes da despensa com cada receita.
 */
object RecipeMatcher {
    /** Coisas que quase toda a gente tem em casa — não contam como "em falta". */
    private val staples = setOf("sal", "agua", "pimenta")

    fun isStaple(ingredient: String) = Normalizer.keywords(ingredient).joinToString(" ") in staples

    fun match(
        recipes: List<RecipeInfo>,
        pantryNames: List<String>,
        mealType: MealType? = null,
        diets: Set<DietTag> = emptySet(),
    ): List<RecipeMatch> =
        recipes
            .filter { mealType == null || it.mealType == mealType }
            .filter { it.tags.containsAll(diets) }
            .map { recipe ->
                val (have, missing) = recipe.ingredients.partition { ing ->
                    isStaple(ing) || pantryNames.any { Normalizer.ingredientMatches(ing, it) }
                }
                RecipeMatch(recipe, have, missing)
            }
            .sortedWith(
                compareByDescending<RecipeMatch> { it.coverage }
                    .thenBy { it.missing.size }
                    .thenBy { it.recipe.name },
            )
}
