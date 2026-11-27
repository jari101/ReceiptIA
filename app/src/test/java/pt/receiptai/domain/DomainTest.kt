package pt.receiptai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DomainTest {

    @Test
    fun `dinheiro em centimos`() {
        assertEquals(1234L, Money.parseToCents("12,34"))
        assertEquals(1234L, Money.parseToCents("12.34 €"))
        assertEquals(123456L, Money.parseToCents("1.234,56"))
        assertEquals(-50L, Money.parseToCents("-0,50"))
        assertNull(Money.parseToCents("abc"))
        assertEquals("12,34", Money.toPlain(1234))
        assertTrue(Money.format(1234).contains("12,34"))
    }

    @Test
    fun `normaliza nomes de produtos`() {
        assertEquals("leite m gordo mimosa", Normalizer.normalize("LEITE M.GORDO MIMOSA 1L"))
        assertEquals("iogurte natural", Normalizer.normalize("Iogurte Natural 4x125g"))
        assertTrue(Normalizer.ingredientMatches("Ovos", "OVOS CLASSE M 12UN"))
        assertTrue(Normalizer.ingredientMatches("Pão de forma", "Pão de forma integral"))
        assertTrue(Normalizer.ingredientMatches("Maçã", "Maçã gala"))
        assertTrue(Normalizer.ingredientMatches("Lentilhas", "Lentilhas 500g"))
        assertFalse(Normalizer.ingredientMatches("Carne picada", "Peito de frango"))
    }

    @Test
    fun `validade estimada pela categoria`() {
        val d = LocalDate.of(2026, 11, 1)
        assertEquals(d.plusDays(7), ShelfLife.estimateExpiry("Leite meio gordo 1L", d))
        assertEquals(Category.PEIXE, ShelfLife.category("Salmão fresco"))
        assertEquals(Category.MERCEARIA, ShelfLife.category("Atum em lata"))
        assertEquals(Category.LATICINIOS, ShelfLife.category("Iogurtes gregos"))
        assertEquals(Category.FRUTA, ShelfLife.category("Bananas"))
        assertFalse(ShelfLife.isFood("Detergente da loiça"))
        assertEquals(30, ShelfLife.days("Produto misterioso"))
    }

    private fun line(id: Long, date: LocalDate, name: String, cents: Long) =
        PurchaseLine(id, date, name, Normalizer.normalize(name), 1.0, cents)

    private fun receipt(id: Long, date: LocalDate, cents: Long, store: String = "Loja") =
        ReceiptSummary(id, date, store, cents, itemCount = 1)

    @Test
    fun `estatisticas por semana mes e ano`() {
        val today = LocalDate.of(2026, 11, 19) // quinta-feira
        val receipts = listOf(
            receipt(1, LocalDate.of(2026, 11, 16), 1000L), // segunda desta semana
            receipt(2, LocalDate.of(2026, 11, 19), 500L),
            receipt(3, LocalDate.of(2026, 11, 2), 2000L),
            receipt(4, LocalDate.of(2026, 10, 10), 3000L),
            receipt(5, LocalDate.of(2025, 12, 1), 9999L),
        )
        val week = StatsCalculator.summarize(StatsPeriod.SEMANA, today, receipts, emptyList())
        assertEquals(LocalDate.of(2026, 11, 16), week.from)
        assertEquals(1500L, week.totalCents)
        assertEquals(listOf(1L, 2L), week.points.map { it.id })
        assertEquals(7, week.buckets.size)
        assertEquals("Seg", week.buckets.first().label)
        assertEquals("Sáb", week.buckets[5].label)
        assertEquals(1000L, week.buckets[0].cents) // segunda
        assertEquals(500L, week.buckets[3].cents) // quinta

        val month = StatsCalculator.summarize(StatsPeriod.MES, today, receipts, emptyList())
        assertEquals(3500L, month.totalCents)
        assertEquals(3000L, month.previousTotalCents)
        assertEquals(17, month.changePercent)
        // Um ponto por recibo, do mais antigo para o mais recente.
        assertEquals(listOf(3L, 1L, 2L), month.points.map { it.id })
        assertEquals(5, month.buckets.size) // 1–7, 8–14, 15–21, 22–28, 29–30
        assertEquals("15–21", month.buckets[2].label)
        // Cada barra sabe que recibos tem (é o que o popup mostra).
        assertEquals(listOf(1L, 2L), month.buckets[2].receipts.map { it.id })
        assertEquals(LocalDate.of(2026, 11, 15), month.buckets[2].from)
        assertEquals(LocalDate.of(2026, 11, 30), month.buckets[4].to)

        val year = StatsCalculator.summarize(StatsPeriod.ANO, today, receipts, emptyList())
        assertEquals(6500L, year.totalCents)
        assertEquals(4, year.points.size)
        assertEquals(12, year.buckets.size)
        assertEquals(listOf(3L, 1L, 2L), year.buckets[10].receipts.map { it.id }) // novembro
        assertEquals(LocalDate.of(2026, 2, 28), year.buckets[1].to)
        // O ano ainda não acabou: compara com 1 jan – 19 nov de 2025 (a compra de dezembro não conta).
        assertEquals(0L, year.previousTotalCents)
        assertNull(year.changePercent)

        // Um ano que já acabou compara com o ano anterior inteiro.
        val lastYear = StatsCalculator.summarize(StatsPeriod.ANO, LocalDate.of(2025, 6, 1), receipts, emptyList(), today)
        assertEquals(9999L, lastYear.totalCents)
    }

    @Test
    fun `pontos do grafico do mesmo dia ficam lado a lado e o popup mostra-os todos`() {
        val nov18 = LocalDate.of(2026, 11, 18)
        val receipts = listOf(
            receipt(7, nov18, 1442, "Mercado do bairro"),
            receipt(2, LocalDate.of(2026, 11, 3), 890),
            receipt(5, nov18, 810, "Pingo Doce"),
            receipt(9, LocalDate.of(2026, 11, 21), 300),
            receipt(1, LocalDate.of(2026, 10, 31), 999), // outro mês: fica de fora
        )
        val range = StatsCalculator.range(StatsPeriod.MES, nov18)
        val points = StatsCalculator.chartPoints(range, receipts)
        // Por data e, no mesmo dia, pelo id (o recibo guardado primeiro vem primeiro).
        assertEquals(listOf(2L, 5L, 7L, 9L), points.map { it.id })

        // Tocar no 2.º ou no 3.º ponto mostra os DOIS recibos de 18 nov.
        assertEquals(listOf(5L, 7L), StatsCalculator.sameDay(points, 1).map { it.id })
        assertEquals(listOf(5L, 7L), StatsCalculator.sameDay(points, 2).map { it.id })
        assertEquals(listOf(9L), StatsCalculator.sameDay(points, 3).map { it.id })
        assertEquals(emptyList<ReceiptSummary>(), StatsCalculator.sameDay(points, 99))
    }

    @Test
    fun `ingrediente opcional em falta nao impede a receita`() {
        val recipes = listOf(
            RecipeInfo(1, "Omelete", MealType.JANTAR, 10, emptySet(), listOf("Ovos", "Queijo", "Espinafres"), optional = setOf("Queijo")),
            RecipeInfo(2, "Tosta", MealType.LANCHE, 5, emptySet(), listOf("Pão", "Queijo", "Fiambre")),
        )
        val pantry = listOf("Ovos", "Espinafres", "Pão", "Fiambre")
        val (omelete, tosta) = RecipeMatcher.match(recipes, pantry)

        // Na omelete só falta o queijo, que é opcional: dá para fazer.
        assertEquals("Omelete", omelete.recipe.name)
        assertEquals(listOf("Queijo"), omelete.missing)
        assertEquals(emptyList<String>(), omelete.missingRequired)
        assertTrue(omelete.canCook)
        assertEquals(1f, omelete.requiredCoverage)
        // A barra continua a mostrar 2 de 3 (é a verdade: o queijo não está em casa).
        assertEquals(2f / 3, omelete.coverage)

        // Na tosta o queijo é obrigatório: não dá.
        assertFalse(tosta.canCook)
        assertEquals(listOf("Queijo"), tosta.missingRequired)
    }

    @Test
    fun `top produtos conta compras diferentes`() {
        val d = LocalDate.of(2026, 11, 1)
        val lines = listOf(
            line(1, d, "Leite 1L", 89), line(1, d, "LEITE 1L", 89), // mesmo recibo conta 1 vez
            line(2, d, "Leite 1L", 89), line(3, d, "Leite 1L", 89),
            line(2, d, "Pão", 120), line(3, d, "Pão", 120),
            line(3, d, "Café", 329),
        )
        val top = StatsCalculator.topItems(lines)
        assertEquals(listOf("Leite 1L", "Pão", "Café"), top.map { it.name })
        assertEquals(3, top[0].timesBought)
        assertEquals(356L, top[0].totalCents)
    }

    @Test
    fun `lista de compras automatica`() {
        val today = LocalDate.of(2026, 11, 19)
        val history = listOf(
            // Leite de 7 em 7 dias; última compra há 8 dias -> sugerir
            line(1, today.minusDays(22), "Leite", 89), line(2, today.minusDays(15), "Leite", 89), line(3, today.minusDays(8), "Leite", 89),
            // Café de 30 em 30 dias; última há 5 dias -> não sugerir
            line(4, today.minusDays(65), "Café", 329), line(5, today.minusDays(35), "Café", 329), line(6, today.minusDays(5), "Café", 329),
            // Só 2 compras -> não chega
            line(7, today.minusDays(40), "Arroz", 119), line(8, today.minusDays(20), "Arroz", 119),
            // Pão de 7 em 7 dias, mas a última foi há 60 dias -> deixou de comprar, não sugerir
            line(9, today.minusDays(74), "Pão", 120), line(10, today.minusDays(67), "Pão", 120), line(11, today.minusDays(60), "Pão", 120),
        )
        val s = ShoppingListPlanner.suggest(history, today)
        assertEquals(listOf("Leite"), s.map { it.name })
        assertEquals(7.0, s[0].averageIntervalDays, 0.0)
        assertEquals(8L, s[0].daysSinceLast)
    }

    @Test
    fun `receitas com o que ha em casa e filtros de dieta`() {
        val recipes = listOf(
            RecipeInfo(1, "Omelete", MealType.JANTAR, 10, setOf(DietTag.SEM_GLUTEN, DietTag.VEGETARIANO), listOf("Ovos", "Queijo", "Sal")),
            RecipeInfo(2, "Bolonhesa", MealType.JANTAR, 30, setOf(DietTag.SEM_LACTOSE), listOf("Esparguete", "Carne picada", "Tomate")),
            RecipeInfo(3, "Papas", MealType.PEQUENO_ALMOCO, 5, setOf(DietTag.VEGETARIANO), listOf("Aveia", "Leite")),
        )
        val pantry = listOf("OVOS CLASSE M 12UN", "Queijo flamengo", "Tomate", "Esparguete 500g")

        val all = RecipeMatcher.match(recipes, pantry)
        assertEquals(listOf("Omelete", "Bolonhesa", "Papas"), all.map { it.recipe.name })
        assertEquals(1f, all[0].coverage) // o sal conta como básico
        assertEquals(listOf("Carne picada"), all[1].missing)

        assertEquals(listOf("Omelete", "Papas"), RecipeMatcher.match(recipes, pantry, diets = setOf(DietTag.VEGETARIANO)).map { it.recipe.name })
        assertEquals(listOf("Papas"), RecipeMatcher.match(recipes, pantry, mealType = MealType.PEQUENO_ALMOCO).map { it.recipe.name })
    }
}
