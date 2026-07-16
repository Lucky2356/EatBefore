package com.eatbefore.domain.shelflife

/**
 * Typical "use within N days after opening" for common food groups.
 *
 * These are conservative rules of thumb for home storage, not a legal or medical
 * reference — the printed date always wins (see [com.eatbefore.domain.usecase
 * .CalculateExpirationAfterOpeningUseCase], which caps the result at it), and the user
 * can edit the value on any batch. Matching is keyword-based over the product name and
 * category, in Russian and English, because that is all the data the app has.
 *
 * Rules are checked **in order**, so narrower entries come before broader ones
 * ("сгущённое молоко" must not be matched by the plain "молоко" rule).
 */
object OpeningShelfLife {

    private data class Rule(val days: Int, val keywords: List<String>)

    private val RULES = listOf(
        // Narrow dairy first — these would otherwise be caught by "молоко"/"milk".
        Rule(14, listOf("сгущен", "сгущён", "condensed")),
        Rule(30, listOf("сухое молоко", "milk powder", "powdered milk")),
        // Dairy.
        Rule(3, listOf("молоко", "milk", "сливки", "cream")),
        Rule(7, listOf("сметана", "sour cream", "smetana")),
        Rule(5, listOf("кефир", "йогурт", "yogurt", "yoghurt", "kefir", "ряженка")),
        Rule(3, listOf("творог", "cottage cheese", "curd")),
        Rule(7, listOf("сыр", "cheese")),
        Rule(14, listOf("масло сливочное", "сливочное масло", "butter")),
        // Meat and fish are the riskiest once opened.
        Rule(3, listOf("колбас", "сосиск", "ветчина", "sausage", "ham", "бекон", "bacon")),
        Rule(2, listOf("рыба", "fish", "икра", "caviar", "морепродукт", "seafood")),
        Rule(2, listOf("паштет", "pate", "pâté")),
        // Drinks.
        Rule(3, listOf("сок", "juice", "нектар", "nectar", "морс")),
        Rule(3, listOf("чай", "tea", "холодный чай", "iced tea")),
        Rule(3, listOf("компот", "квас", "kvass")),
        // Long-lived pantry items.
        Rule(30, listOf("кетчуп", "ketchup", "майонез", "mayonnaise", "соус", "sauce", "горчица", "mustard")),
        Rule(30, listOf("джем", "варенье", "jam", "повидло", "мед", "мёд", "honey")),
        Rule(14, listOf("паста томатная", "tomato paste", "томатная паста")),
        Rule(2, listOf("консерв", "canned", "тушенка", "тушёнка")),
    )

    /**
     * Suggested shelf life in days after opening, or null when nothing matches — an
     * unknown product simply gets no suggestion rather than a made-up one.
     */
    fun suggestDays(name: String?, category: String? = null): Int? {
        val haystack = listOfNotNull(name, category).joinToString(" ").lowercase()
        if (haystack.isBlank()) return null
        return RULES.firstOrNull { rule -> rule.keywords.any { haystack.contains(it) } }?.days
    }
}
