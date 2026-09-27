package com.eatbefore.domain.shelflife

import com.eatbefore.domain.model.HomemadeKind

/**
 * Roughly how long home-made food keeps, counted from the day it was made.
 *
 * The third table next to [TypicalShelfLife] and [OpeningShelfLife], and separate for the
 * same reason they are separate from each other: "борщ" from a shop is a sealed pouch
 * good for months, "борщ" from the stove is three days in the fridge. Asking the shop
 * table about a pot of soup would offer the pouch's date.
 *
 * Unlike the shop table, this one has a default for each kind. For bought food an unknown
 * name gets no suggestion, because shop products range from two days to two years and any
 * average would be a confident lie. Home cooking does not range like that: cooked food in
 * the fridge is three days whatever it is called, and a sealed home jar is a year. Those
 * are the figures food-safety guidance itself falls back on, so offering them is honest.
 *
 * As everywhere, nothing is applied silently — the add screen offers the figure as a chip.
 * After a jar is opened, [OpeningShelfLife] takes over.
 */
object HomemadeShelfLife {

    private data class Rule(val days: Int, val keywords: List<String>)

    /** Checked in order: narrower first. */
    private val DISH_RULES = listOf(
        // Mayonnaise salads are the classic day-after risk.
        Rule(1, listOf("оливье", "майонез", "мимоза", "шуба", "селёдка под", "селедка под")),
        Rule(1, listOf("салат", "salad")),
        Rule(2, listOf("котлет", "тефтел", "фрикадел", "голубц", "фарширован", "cutlet", "meatball")),
        Rule(2, listOf("рыб", "fish")),
    )

    private val PRESERVE_RULES = listOf(
        // Frozen berries and vegetables are put up too, and keep less than a sealed jar.
        Rule(DAYS_FROZEN_PRESERVE, listOf("заморож", "морожен", "frozen")),
    )

    /** Cooked food in the fridge. */
    const val DAYS_DISH_DEFAULT = 3

    /** A dish put in the freezer on the day it was cooked. */
    const val DAYS_DISH_FROZEN = 90

    /** A sealed home jar: jam, pickles, compote, lecho. */
    const val DAYS_PRESERVE_DEFAULT = 365

    private const val DAYS_FROZEN_PRESERVE = 270

    /**
     * Typical days from the day it was made for [name] of the given [kind]. [frozen] is
     * true when it goes straight into the freezer, which changes a dish from days to months.
     */
    fun suggestDays(name: String?, kind: HomemadeKind, frozen: Boolean = false): Int {
        val haystack = name.orEmpty().lowercase()
        return when (kind) {
            HomemadeKind.DISH ->
                if (frozen) {
                    DAYS_DISH_FROZEN
                } else {
                    DISH_RULES.match(haystack) ?: DAYS_DISH_DEFAULT
                }
            HomemadeKind.PRESERVE -> PRESERVE_RULES.match(haystack) ?: DAYS_PRESERVE_DEFAULT
        }
    }

    private fun List<Rule>.match(haystack: String): Int? =
        if (haystack.isBlank()) null else firstOrNull { rule -> rule.keywords.any { haystack.contains(it) } }?.days
}
