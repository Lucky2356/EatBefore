package com.eatbefore.domain.shelflife

/**
 * Roughly how long an unopened product keeps once it is home, by food group.
 *
 * The sibling of [OpeningShelfLife], and deliberately a separate table: that one answers
 * "how long after opening", this one "how long from the shop". Mixing them would suggest
 * three days for a sealed carton of milk.
 *
 * These are conservative rules of thumb for home storage, not a legal or medical
 * reference. Nothing is ever applied silently: the add screen offers the match as one more
 * chip next to «Сегодня» and «Через неделю», labelled as a typical figure, and the user
 * taps it or ignores it. That is why an unknown product gets no suggestion at all rather
 * than an average of everything — a wrong date entered on the user's behalf is worse than
 * no date, because the app would then warn about the wrong day with total confidence.
 *
 * Rules are checked **in order**, so narrower entries come before broader ones.
 */
object TypicalShelfLife {

    private data class Rule(val days: Int, val keywords: List<String>)

    private val RULES = listOf(
        // Narrow first: these would otherwise be caught by the broader rules below.
        Rule(365, listOf("сгущен", "сгущён", "condensed")),
        Rule(180, listOf("сухое молоко", "milk powder", "powdered milk")),
        Rule(365, listOf("масло растительное", "растительное масло", "подсолнечное", "оливков", "olive oil")),
        Rule(30, listOf("масло сливочное", "сливочное масло", "butter")),
        // Fridge staples.
        Rule(7, listOf("молоко", "milk", "сливки", "cream")),
        Rule(14, listOf("сметана", "sour cream", "smetana")),
        Rule(10, listOf("кефир", "йогурт", "yogurt", "yoghurt", "kefir", "ряженка", "простокваша")),
        Rule(5, listOf("творог", "cottage cheese", "curd")),
        Rule(21, listOf("сыр", "cheese")),
        Rule(30, listOf("яйц", "egg")),
        // Meat and fish keep the shortest.
        Rule(2, listOf("фарш", "mince", "курица", "chicken", "мясо", "meat", "свинин", "говядин", "индейк")),
        Rule(2, listOf("рыба", "fish", "морепродукт", "seafood", "креветк", "shrimp")),
        Rule(7, listOf("колбас", "сосиск", "ветчина", "sausage", "ham", "бекон", "bacon")),
        // Bread and greens go off in days; roots and cabbage last, but not by this table.
        Rule(4, listOf("хлеб", "батон", "булк", "bread", "лаваш")),
        Rule(5, listOf("зелень", "укроп", "петрушка", "салат", "herbs", "lettuce")),
        Rule(7, listOf("овощ", "фрукт", "ягод", "vegetable", "fruit", "berry", "berries")),
        // Frozen.
        Rule(180, listOf("заморож", "морожен", "frozen", "пельмен", "вареник")),
        // Pantry.
        Rule(730, listOf("консерв", "canned", "тушенка", "тушёнка", "сахар", "sugar", "соль", "salt")),
        Rule(365, listOf("крупа", "рис", "гречк", "макарон", "паста", "rice", "pasta", "buckwheat", "овсян")),
        Rule(180, listOf("мука", "flour")),
        Rule(365, listOf("чай", "кофе", "tea", "coffee", "вода", "water")),
        Rule(180, listOf("сок", "juice", "нектар", "nectar")),
        Rule(180, listOf("кетчуп", "ketchup", "майонез", "mayonnaise", "соус", "sauce", "горчица", "mustard")),
        Rule(365, listOf("джем", "варенье", "jam", "повидло", "мед", "мёд", "honey")),
    )

    /**
     * Typical days from purchase for [name] (and [category] when known), or null when
     * nothing matches — an unknown product simply gets no suggestion.
     */
    fun suggestDays(name: String?, category: String? = null): Int? {
        val haystack = listOfNotNull(name, category).joinToString(" ").lowercase()
        if (haystack.isBlank()) return null
        return RULES.firstOrNull { rule -> rule.keywords.any { haystack.contains(it) } }?.days
    }
}
