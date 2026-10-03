package com.eatbefore.domain.shelflife

/**
 * How long food keeps once it goes into the freezer, and how long once it comes out.
 *
 * The fourth table beside [TypicalShelfLife], [OpeningShelfLife] and [HomemadeShelfLife].
 * Moving a pack into the freezer used to keep its fridge date, so chicken frozen on the
 * last day was reported as expired the next morning — and the date had to be fixed by hand
 * each time. Thawing is the reverse: the months the freezer granted are gone the moment it
 * is out, and a thawed fillet is a day or two, not «до марта».
 *
 * Like every table here it only suggests: the move dialog offers the date, and «оставить
 * как есть» is always one tap away. The figures are the usual household guidance (USDA
 * and Роспотребнадзор agree on the order of magnitude), rounded down.
 */
object FreezerShelfLife {

    private data class Rule(val days: Int, val keywords: List<String>)

    /** Checked in order: narrower first. */
    private val FROZEN_RULES = listOf(
        Rule(90, listOf("фарш", "колбас", "сосиск", "ветчин", "бекон", "minced", "sausage", "bacon")),
        Rule(90, listOf("рыб", "лосос", "форел", "сельд", "скумбр", "кревет", "fish", "salmon", "shrimp")),
        Rule(270, listOf("куриц", "курин", "цыпл", "индейк", "утк", "chicken", "turkey")),
        Rule(180, listOf("говяд", "свинин", "баран", "мяс", "стейк", "beef", "pork", "meat")),
        Rule(90, listOf("хлеб", "батон", "булк", "лаваш", "выпечк", "пирог", "bread", "bun")),
        Rule(240, listOf("ягод", "клубник", "малин", "черник", "смородин", "berr")),
        Rule(240, listOf("овощ", "горош", "брокколи", "фасол", "кукуруз", "шпинат", "vegetable")),
        Rule(120, listOf("масло", "сыр", "butter", "cheese")),
    )

    /** Anything else frozen: three months is safe for almost everything. */
    const val DAYS_FROZEN_DEFAULT = 90

    private val THAWED_RULES = listOf(
        Rule(2, listOf("хлеб", "батон", "булк", "лаваш", "выпечк", "пирог", "bread", "bun")),
        Rule(3, listOf("ягод", "овощ", "berr", "vegetable", "масло", "butter", "сыр", "cheese")),
    )

    /** Thawed meat, fish or a cooked dish: eat it within a day. */
    const val DAYS_THAWED_DEFAULT = 1

    /** Days the food keeps once frozen, from the day it goes in. */
    fun frozenDays(name: String?, category: String? = null): Int =
        FROZEN_RULES.match(name, category) ?: DAYS_FROZEN_DEFAULT

    /** Days the food keeps once out of the freezer, from the day it comes out. */
    fun thawedDays(name: String?, category: String? = null): Int =
        THAWED_RULES.match(name, category) ?: DAYS_THAWED_DEFAULT

    private fun List<Rule>.match(name: String?, category: String?): Int? {
        val haystack = "${name.orEmpty()} ${category.orEmpty()}".lowercase()
        if (haystack.isBlank()) return null
        return firstOrNull { rule -> rule.keywords.any { haystack.contains(it) } }?.days
    }
}
