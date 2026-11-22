package pt.receiptai.domain

import java.text.Normalizer as JavaNormalizer

/**
 * Transforma nomes de produtos numa "chave" simples para os podermos comparar.
 * Ex.: "LEITE M.GORDO MIMOSA 1L" -> "leite m gordo mimosa"
 */
object Normalizer {
    private val accents = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    // Tokens de medida/quantidade: 1l, 500g, 1,5kg, 6x, 4x125g, 12un, 33cl...
    private val unitToken = "^\\d+([.,]\\d+)?(x\\d+)?(kg|g|gr|l|lt|ml|cl|un|uni|x)?$".toRegex()
    private val nonAlnum = "[^a-z0-9 ]".toRegex()
    private val spaces = "\\s+".toRegex()

    fun normalize(name: String): String {
        val noAccents = JavaNormalizer.normalize(name.lowercase(Money.PT), JavaNormalizer.Form.NFD)
            .replace(accents, "")
        return noAccents.replace(nonAlnum, " ")
            .split(spaces)
            .filter { it.isNotBlank() && !unitToken.matches(it) }
            .joinToString(" ")
    }

    /** Singular muito simples para comparar: "ovos" -> "ovo", "limoes" -> "limao", "cereais" -> "cereal". */
    fun stem(word: String): String = when {
        word.length > 4 && (word.endsWith("oes") || word.endsWith("aes")) -> word.dropLast(3) + "ao"
        word.length > 4 && word.endsWith("ais") -> word.dropLast(3) + "al"
        word.length > 3 && word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    private val stopWords = setOf("de", "da", "do", "das", "dos", "e", "com", "a", "o", "em")

    fun keywords(name: String): List<String> =
        normalize(name).split(' ').filter { it.isNotBlank() && it !in stopWords }.map(::stem)

    /**
     * Um ingrediente ("ovos") existe num produto ("OVOS CLASSE M 12UN") se TODAS as palavras
     * do ingrediente aparecem no nome do produto.
     */
    fun ingredientMatches(ingredient: String, productName: String): Boolean {
        val need = keywords(ingredient)
        if (need.isEmpty()) return false
        val have = keywords(productName).toSet()
        return need.all { it in have }
    }
}
