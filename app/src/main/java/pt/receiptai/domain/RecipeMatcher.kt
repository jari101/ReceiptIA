package pt.receiptai.domain

/** Dados mínimos de uma receita para a comparação (sem depender da base de dados). */
data class RecipeInfo(
    val id: Long,
    val name: String,
    val mealType: MealType,
    val minutes: Int,
    val tags: Set<DietTag>,
    val ingredients: List<String>,
    /** Ingredientes sem os quais a receita também se faz. */
    val optional: Set<String> = emptySet(),
)

data class RecipeMatch(
    val recipe: RecipeInfo,
    val have: List<String>,
    val missing: List<String>,
) {
    /** Percentagem de ingredientes que já existem em casa (0..1). É a barra verde do cartão. */
    val coverage: Float get() = if (recipe.ingredients.isEmpty()) 0f else have.size.toFloat() / recipe.ingredients.size

    /** O que falta e é MESMO preciso. */
    val missingRequired: List<String> get() = missing.filterNot { it in recipe.optional }

    /** true = dá para fazer já (só faltam, no máximo, ingredientes opcionais). */
    val canCook: Boolean get() = missingRequired.isEmpty()

    /** Como [coverage], mas só com os ingredientes obrigatórios: um opcional em falta não conta. */
    val requiredCoverage: Float
        get() {
            val required = recipe.ingredients.filterNot { it in recipe.optional }
            if (required.isEmpty()) return 1f
            return (required.size - missingRequired.size).toFloat() / required.size
        }
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
            // Primeiro as que dá para fazer (os opcionais em falta não contam), depois as mais completas.
            .sortedWith(
                compareByDescending<RecipeMatch> { it.requiredCoverage }
                    .thenByDescending { it.coverage }
                    .thenBy { it.missingRequired.size }
                    .thenBy { it.recipe.name },
            )
}
