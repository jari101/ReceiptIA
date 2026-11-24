package pt.receiptai.parser

/** Lê um ficheiro de docs/exemplos (configurado como recurso de teste no build.gradle.kts). */
fun exampleFile(name: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResource(name)) { "Falta o exemplo $name" }.readText()
