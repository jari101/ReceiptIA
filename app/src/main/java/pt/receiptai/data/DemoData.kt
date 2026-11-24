package pt.receiptai.data

import pt.receiptai.domain.DietTag
import pt.receiptai.domain.DietTag.DIABETICOS
import pt.receiptai.domain.DietTag.GINASIO
import pt.receiptai.domain.DietTag.SEM_GLUTEN
import pt.receiptai.domain.DietTag.SEM_LACTOSE
import pt.receiptai.domain.DietTag.VEGETARIANO
import pt.receiptai.domain.MealType
import pt.receiptai.parser.ParsedItem
import pt.receiptai.parser.ParsedReceipt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Dados de exemplo (fictícios) para experimentar a app sem recibos reais.
 * Usa sempre a mesma "semente" aleatória, por isso gera sempre os mesmos dados.
 */
object DemoData {

    private data class Product(val name: String, val priceCents: Long, val chance: Double, val qty: IntRange = 1..1, val perKg: Boolean = false)

    private val supermarket = listOf(
        Product("Leite meio gordo 1L", 89, 1.0, 4..6),
        Product("Pão de forma integral", 149, 0.8),
        Product("Ovos classe M 12un", 259, 0.55),
        Product("Iogurte natural 4x125g", 149, 0.7, 1..2),
        Product("Bananas", 129, 0.9, perKg = true),
        Product("Maçã gala", 179, 0.6, perKg = true),
        Product("Tomate", 199, 0.6, perKg = true),
        Product("Alface", 89, 0.5),
        Product("Cenoura 1kg", 99, 0.4),
        Product("Batata 3kg", 349, 0.3),
        Product("Cebola 1kg", 129, 0.4),
        Product("Alho", 59, 0.25),
        Product("Arroz agulha 1kg", 119, 0.35),
        Product("Esparguete 500g", 79, 0.35, 1..2),
        Product("Peito de frango", 549, 0.6, perKg = true),
        Product("Atum em lata", 129, 0.4, 2..3),
        Product("Queijo flamengo fatias", 229, 0.5),
        Product("Fiambre de peru", 199, 0.4),
        Product("Azeite virgem extra 750ml", 649, 0.15),
        Product("Café moído 250g", 329, 0.3),
        Product("Água 6x1,5L", 199, 0.5),
        Product("Salmão fresco", 1299, 0.25, perKg = true),
        Product("Brócolos", 179, 0.35),
        Product("Feijão vermelho lata", 89, 0.25, 1..2),
        Product("Aveia em flocos", 169, 0.2),
        Product("Espinafres 300g", 149, 0.25),
        Product("Cogumelos 250g", 159, 0.25),
        Product("Courgete", 149, 0.2, perKg = true),
        Product("Lentilhas 500g", 139, 0.15),
        Product("Detergente da loiça", 189, 0.15),
        Product("Papel higiénico 12 rolos", 449, 0.15),
    )

    private val bakery = listOf(
        Product("Pão de mistura", 120, 0.9),
        Product("Papo-seco", 15, 0.8, 4..8),
        Product("Croissant", 95, 0.4, 1..3),
        Product("Bananas", 139, 0.5, perKg = true),
        Product("Laranjas", 149, 0.4, perKg = true),
    )

    /** ~3 meses de compras até [today]: compra grande ao sábado + padaria/frutaria a meio da semana. */
    fun receipts(today: LocalDate, weeks: Int = 13): List<ParsedReceipt> {
        val rnd = Random(42)
        val out = mutableListOf<ParsedReceipt>()
        val lastSaturday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        for (w in weeks - 1 downTo 0) {
            val saturday = lastSaturday.minusWeeks(w.toLong())
            out += shop("Mercado do Bairro", saturday, supermarket, rnd)
            val wednesday = saturday.minusDays(3)
            if (wednesday <= today) out += shop("Padaria Aurora", wednesday, bakery, rnd)
        }
        // Compra de ontem, para haver algo recente na despensa.
        out += shop("Mercado do Bairro", today.minusDays(1), supermarket.filter { it.chance >= 0.5 }, rnd)
        return out.sortedBy { it.date }
    }

    private fun shop(store: String, date: LocalDate, catalog: List<Product>, rnd: Random): ParsedReceipt {
        val items = catalog.filter { rnd.nextDouble() < it.chance }.map { p ->
            val qty = if (p.perKg) (0.5 + rnd.nextDouble()).let { Math.round(it * 1000) / 1000.0 }
            else (p.qty.first + rnd.nextInt(p.qty.last - p.qty.first + 1)).toDouble()
            ParsedItem(p.name, qty, p.priceCents, (p.priceCents * qty).roundToLong())
        }
        return ParsedReceipt(store, date, items, items.sumOf { it.totalCents })
    }

    // ---------------- Receitas pré-carregadas ----------------

    data class SeedRecipe(
        val name: String,
        val mealType: MealType,
        val minutes: Int,
        val tags: Set<DietTag>,
        val ingredients: List<Pair<String, String>>, // nome -> quantidade
        val steps: List<String>,
    )

    val recipes = listOf(
        SeedRecipe("Omelete de espinafres", MealType.PEQUENO_ALMOCO, 15, setOf(DIABETICOS, GINASIO, SEM_GLUTEN, VEGETARIANO),
            listOf("Ovos" to "3", "Espinafres" to "1 mão-cheia", "Queijo" to "2 fatias", "Sal" to "q.b."),
            listOf("Bate os ovos com uma pitada de sal.", "Salteia os espinafres numa frigideira 2 minutos.", "Junta os ovos e o queijo e cozinha em lume brando até firmar.")),
        SeedRecipe("Papas de aveia com banana", MealType.PEQUENO_ALMOCO, 10, setOf(VEGETARIANO),
            listOf("Aveia" to "50 g", "Leite" to "250 ml", "Banana" to "1"),
            listOf("Aquece o leite com a aveia em lume médio, a mexer, 5 minutos.", "Serve com a banana às rodelas por cima.")),
        SeedRecipe("Panquecas de banana e aveia", MealType.PEQUENO_ALMOCO, 15, setOf(SEM_LACTOSE, GINASIO, VEGETARIANO),
            listOf("Banana" to "1", "Ovos" to "2", "Aveia" to "40 g"),
            listOf("Esmaga a banana e mistura com os ovos e a aveia.", "Cozinha pequenas porções numa frigideira antiaderente, 2 min de cada lado.")),
        SeedRecipe("Tosta mista", MealType.LANCHE, 10, emptySet(),
            listOf("Pão de forma" to "2 fatias", "Queijo" to "2 fatias", "Fiambre" to "2 fatias"),
            listOf("Monta a tosta com o queijo e o fiambre entre as fatias de pão.", "Leva à tostadeira ou frigideira até dourar.")),
        SeedRecipe("Iogurte com fruta e aveia", MealType.LANCHE, 5, setOf(VEGETARIANO, GINASIO),
            listOf("Iogurte" to "1", "Banana" to "1/2", "Maçã" to "1/2", "Aveia" to "2 colheres"),
            listOf("Corta a fruta aos cubos.", "Junta ao iogurte e polvilha com a aveia.")),
        SeedRecipe("Batido de banana proteico", MealType.LANCHE, 5, setOf(GINASIO, VEGETARIANO),
            listOf("Leite" to "300 ml", "Banana" to "1", "Aveia" to "30 g", "Manteiga de amendoim" to "1 colher"),
            listOf("Coloca tudo na liquidificadora.", "Tritura 1 minuto e serve fresco.")),
        SeedRecipe("Salada de atum", MealType.ALMOCO, 15, setOf(DIABETICOS, GINASIO, SEM_GLUTEN, SEM_LACTOSE),
            listOf("Atum" to "1 lata", "Alface" to "1/2", "Tomate" to "1", "Ovos" to "2", "Azeite" to "1 fio"),
            listOf("Coze os ovos 10 minutos.", "Lava e corta a alface e o tomate.", "Junta o atum e os ovos às rodelas e tempera com azeite.")),
        SeedRecipe("Frango grelhado com arroz e brócolos", MealType.ALMOCO, 30, setOf(GINASIO, SEM_GLUTEN, SEM_LACTOSE),
            listOf("Peito de frango" to "300 g", "Arroz" to "150 g", "Brócolos" to "1 molho", "Alho" to "2 dentes", "Azeite" to "1 colher"),
            listOf("Tempera o frango com alho, sal e azeite.", "Coze o arroz e os brócolos.", "Grelha o frango 6 minutos de cada lado.")),
        SeedRecipe("Arroz de atum", MealType.ALMOCO, 30, setOf(SEM_GLUTEN, SEM_LACTOSE),
            listOf("Arroz" to "200 g", "Atum" to "2 latas", "Cebola" to "1", "Tomate" to "1"),
            listOf("Refoga a cebola e o tomate em azeite.", "Junta o arroz e o dobro de água; coze 15 minutos.", "Envolve o atum no fim.")),
        SeedRecipe("Sopa de lentilhas", MealType.ALMOCO, 35, setOf(VEGETARIANO, SEM_GLUTEN, SEM_LACTOSE, DIABETICOS, GINASIO),
            listOf("Lentilhas" to "150 g", "Cenoura" to "2", "Cebola" to "1", "Tomate" to "1", "Alho" to "1 dente"),
            listOf("Coze tudo em 1,2 L de água durante 25 minutos.", "Tritura metade e volta a juntar.")),
        SeedRecipe("Wrap de frango", MealType.LANCHE, 20, setOf(GINASIO),
            listOf("Tortilhas" to "2", "Peito de frango" to "150 g", "Alface" to "folhas", "Tomate" to "1", "Iogurte" to "2 colheres"),
            listOf("Grelha o frango às tiras.", "Recheia as tortilhas com frango, alface, tomate e iogurte.")),
        SeedRecipe("Sopa de legumes", MealType.JANTAR, 40, setOf(DIABETICOS, VEGETARIANO, SEM_GLUTEN, SEM_LACTOSE),
            listOf("Batata" to "2", "Cenoura" to "2", "Cebola" to "1", "Courgete" to "1", "Azeite" to "1 fio", "Sal" to "q.b."),
            listOf("Descasca e corta os legumes.", "Coze em 1,5 L de água 25 minutos.", "Tritura e junta o azeite no fim.")),
        SeedRecipe("Esparguete à bolonhesa", MealType.JANTAR, 35, setOf(SEM_LACTOSE),
            listOf("Esparguete" to "250 g", "Carne picada" to "300 g", "Tomate" to "3", "Cebola" to "1", "Alho" to "2 dentes"),
            listOf("Refoga a cebola e o alho.", "Junta a carne e deixa alourar; acrescenta o tomate e coze 15 minutos.", "Coze o esparguete e serve com o molho.")),
        SeedRecipe("Salmão no forno com batata", MealType.JANTAR, 35, setOf(GINASIO, SEM_GLUTEN, SEM_LACTOSE, DIABETICOS),
            listOf("Salmão" to "2 postas", "Batata" to "4", "Limão" to "1", "Alho" to "2 dentes", "Azeite" to "2 colheres"),
            listOf("Corta as batatas às rodelas e coloca num tabuleiro.", "Põe o salmão por cima com alho, limão e azeite.", "Forno a 200 °C durante 25 minutos.")),
        SeedRecipe("Chili de feijão vermelho", MealType.JANTAR, 35, setOf(VEGETARIANO, SEM_GLUTEN, SEM_LACTOSE, DIABETICOS),
            listOf("Feijão vermelho" to "1 lata", "Tomate" to "2", "Cebola" to "1", "Pimento" to "1", "Arroz" to "150 g"),
            listOf("Refoga a cebola e o pimento.", "Junta o tomate e o feijão e deixa apurar 15 minutos.", "Serve com arroz branco.")),
        SeedRecipe("Cogumelos salteados com ovo", MealType.JANTAR, 15, setOf(DIABETICOS, SEM_GLUTEN, VEGETARIANO, GINASIO),
            listOf("Cogumelos" to "250 g", "Ovos" to "2", "Alho" to "1 dente", "Salsa" to "q.b."),
            listOf("Salteia os cogumelos com alho.", "Abre os ovos por cima e tapa até cozinharem.", "Termina com salsa picada.")),
    )
}
