package com.eatbefore.domain.usecase

import com.eatbefore.core.common.validation.InputValidator
import javax.inject.Inject

/**
 * Turns a list shared from a messenger — «молоко, хлеб, яйца x2» or one item per line with
 * bullets and checkboxes — into shopping-list entries.
 *
 * Shared text is untrusted: it is split, trimmed and length-limited, and nothing in it is
 * interpreted beyond a trailing amount. Each line goes through [AddToShoppingListUseCase],
 * so «молоко» already on the list is bumped rather than added twice.
 */
class ImportShoppingTextUseCase @Inject constructor(private val addToShoppingList: AddToShoppingListUseCase) {

    /** Returns how many lines were taken. */
    suspend operator fun invoke(text: String): Int {
        val items = parseShoppingText(text)
        items.forEach { (name, quantity) ->
            addToShoppingList(AddToShoppingListUseCase.Params(customName = name, quantity = quantity))
        }
        return items.size
    }
}

/** A line of a shared list: what to buy, and how many when the line said so. */
internal fun parseShoppingText(text: String): List<Pair<String, Double>> =
    text.take(MAX_TEXT_LENGTH)
        .split('\n', ';')
        // A comma separates items — but not inside a number, «молоко 3,2%» is one item.
        .flatMap { line -> line.split(ITEM_COMMA) }
        .mapNotNull(::parseLine)
        .distinctBy { it.first.lowercase() }
        .take(MAX_ITEMS)

private fun parseLine(raw: String): Pair<String, Double>? {
    val line = raw.trim().replace(BULLET, "").trim()
    // «Купить:» or «В магазине:» heads the list rather than being on it.
    if (line.isEmpty() || line.endsWith(':')) return null
    val amount = AMOUNT.find(line)
    val name = (if (amount != null) line.substring(0, amount.range.first) else line).trim().trimEnd('-', '—', ':').trim()
    val quantity = amount?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toDoubleOrNull()
    val clean = InputValidator.sanitizeText(name, InputValidator.MAX_NAME_LENGTH) ?: return null
    return clean to (quantity?.takeIf { it in 1.0..MAX_QUANTITY } ?: 1.0)
}

/** Bullets, numbering and checkboxes people put in front of list items. */
private val BULLET = Regex("""^(?:[-*•·–—+]|\d+[.)]|\[[ xX✓]?]|[☐☑✅✔□■▪◦])\s*""")

/**
 * «x2», «×2», «х2» (Cyrillic), «2 шт», «2шт.», «2 pcs» at the end of a line. The «x» form
 * needs a space before it, or «Twix2» would become two of «Twi».
 */
private val AMOUNT = Regex("""(?:\s+[xх×]\s*(\d{1,3})|\s*(\d{1,3})\s*(?:шт|pcs|pc)\.?)\s*$""", RegexOption.IGNORE_CASE)

private val ITEM_COMMA = Regex("""(?<!\d),|,(?!\d)""")

private const val MAX_TEXT_LENGTH = 10_000
private const val MAX_ITEMS = 50
private const val MAX_QUANTITY = 99.0
