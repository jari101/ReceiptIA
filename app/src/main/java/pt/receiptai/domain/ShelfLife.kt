package pt.receiptai.domain

import java.time.LocalDate

/**
 * Estima a categoria e a validade de um produto a partir do nome.
 * As regras são verificadas por ordem: a primeira que encaixar ganha.
 * O utilizador pode sempre corrigir a data na Despensa.
 */
object ShelfLife {
    private data class Rule(val keywords: List<String>, val category: Category, val days: Int)

    private val rules = listOf(
        // Não alimentar (não vai para a despensa)
        Rule(listOf("detergente", "lixivia", "papel higienico", "rolo cozinha", "guardanapo", "champo", "gel banho",
            "sabonete", "pasta dentes", "esponja", "saco lixo", "amaciador", "fralda", "pilha"), Category.NAO_ALIMENTAR, 0),
        // Conservas e secos primeiro (ex.: "atum em lata" não é peixe fresco)
        Rule(listOf("atum", "sardinha lata", "conserva", "lata", "enlatado", "salsicha"), Category.MERCEARIA, 365),
        Rule(listOf("congelado", "gelado", "ervilha", "douradinho"), Category.CONGELADOS, 90),
        Rule(listOf("bacalhau"), Category.PEIXE, 180),
        Rule(listOf("leite"), Category.LATICINIOS, 7),
        Rule(listOf("iogurte", "kefir"), Category.LATICINIOS, 14),
        Rule(listOf("queijo fresco", "requeijao", "natas"), Category.LATICINIOS, 7),
        Rule(listOf("queijo", "mozzarella", "fiambre"), Category.LATICINIOS, 14),
        Rule(listOf("manteiga", "margarina"), Category.LATICINIOS, 60),
        Rule(listOf("pao", "baguete", "broa", "papo seco", "croissant", "bolo"), Category.PADARIA, 3),
        Rule(listOf("tosta", "bolacha", "cereais", "aveia", "granola"), Category.MERCEARIA, 120),
        Rule(listOf("frango", "peru", "porco", "vaca", "novilho", "carne", "bife", "hamburguer", "picada", "lombo", "entrecosto"), Category.CARNE, 2),
        Rule(listOf("chourico", "presunto", "bacon"), Category.CARNE, 30),
        Rule(listOf("peixe", "salmao", "pescada", "dourada", "robalo", "camarao", "polvo", "sardinha"), Category.PEIXE, 2),
        Rule(listOf("ovo"), Category.OVOS, 21),
        Rule(listOf("morango", "framboesa", "mirtilo"), Category.FRUTA, 3),
        Rule(listOf("banana", "pera", "pessego", "kiwi", "uva", "fruta", "abacate", "manga", "ananas", "melao", "melancia"), Category.FRUTA, 6),
        Rule(listOf("maca", "laranja", "limao", "tangerina"), Category.FRUTA, 14),
        Rule(listOf("alface", "espinafre", "rucula", "cogumelo", "brocolo", "salada", "coentro", "salsa"), Category.LEGUMES, 4),
        Rule(listOf("tomate", "pepino", "courgete", "pimento", "beringela", "feijao verde", "couve"), Category.LEGUMES, 7),
        Rule(listOf("cenoura", "abobora", "nabo"), Category.LEGUMES, 21),
        Rule(listOf("batata", "cebola", "alho"), Category.LEGUMES, 30),
        Rule(listOf("arroz", "massa", "esparguete", "macarrao", "farinha", "acucar", "sal", "azeite", "oleo", "feijao", "grao",
            "lentilha", "cafe", "cha", "mel", "compota", "polpa", "molho", "tempero", "chocolate", "quinoa", "manteiga amendoim"),
            Category.MERCEARIA, 365),
        Rule(listOf("agua", "sumo", "refrigerante", "cerveja", "vinho", "bebida"), Category.BEBIDAS, 180),
    )

    private fun ruleFor(productName: String): Rule? {
        // Compara palavras inteiras (já no singular): " leite meio gordo " contém " leite ".
        val normalized = " " + Normalizer.keywords(productName).joinToString(" ") + " "
        return rules.firstOrNull { rule ->
            rule.keywords.any { kw -> normalized.contains(" " + Normalizer.keywords(kw).joinToString(" ") + " ") }
        }
    }

    fun category(productName: String): Category = ruleFor(productName)?.category ?: Category.MERCEARIA

    fun isFood(productName: String): Boolean = category(productName) != Category.NAO_ALIMENTAR

    /** Dias de validade estimados (30 se não conhecermos o produto). */
    fun days(productName: String): Int = ruleFor(productName)?.days ?: 30

    fun estimateExpiry(productName: String, purchasedOn: LocalDate): LocalDate =
        purchasedOn.plusDays(days(productName).toLong())
}
