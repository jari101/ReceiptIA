package pt.receiptai.domain

/** Tipo de refeição de uma receita. */
enum class MealType { PEQUENO_ALMOCO, LANCHE, ALMOCO, JANTAR }

/** Filtros de dieta (README: diabéticos, ginásio, intolerâncias). */
enum class DietTag { DIABETICOS, GINASIO, SEM_GLUTEN, SEM_LACTOSE, VEGETARIANO }

/** Categorias de produtos (usadas para estimar a validade). */
enum class Category { LATICINIOS, PADARIA, CARNE, PEIXE, FRUTA, LEGUMES, OVOS, MERCEARIA, CONGELADOS, BEBIDAS, NAO_ALIMENTAR }

/** De onde veio o recibo. */
enum class ReceiptSource { FICHEIRO, CAMARA, LINK, PARTILHA, ROBO, EXEMPLO, MANUAL }
