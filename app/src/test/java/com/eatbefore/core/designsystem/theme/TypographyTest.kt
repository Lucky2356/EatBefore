package com.eatbefore.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isUnspecified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app must draw in one typeface.
 *
 * [Typography] only replaces the styles it is given and silently keeps Material's defaults —
 * on the platform font — for the rest. The scale used to define six of fifteen styles while
 * five of the missing ones were in daily use, so a third of the app's text was set in a
 * different face from the rest and nothing said so. Bundling a font of our own turns that
 * from a small inconsistency into two visibly different alphabets in the same list row.
 *
 * Reflection rather than a written-out list of fifteen names: a list would pass unchanged on
 * the day Material adds a sixteenth style, which is precisely the day this test is for.
 */
class TypographyTest {

    private fun allStyles(): List<Pair<String, TextStyle>> =
        Typography::class.java.declaredMethods
            .filter { it.returnType == TextStyle::class.java && it.parameterCount == 0 }
            .map {
                it.name.removePrefix("get").replaceFirstChar { c -> c.lowercase() } to
                    it.invoke(EatBeforeTypography) as TextStyle
            }
            .sortedBy { it.first }

    @Test
    fun everyStyleIsDefined() {
        val styles = allStyles()

        assertTrue("Material 3 has 15 text styles, found ${styles.size}", styles.size >= EXPECTED_STYLES)
    }

    @Test
    fun everyStyleUsesTheAppFontFamily() {
        val strays = allStyles().filter { (_, style) -> style.fontFamily != AppFontFamily }

        assertEquals(
            "These styles fall back to the platform font: ${strays.map { it.first }}",
            emptyList<String>(),
            strays.map { it.first },
        )
    }

    @Test
    fun everyStyleHasASizeAndALineHeight() {
        val incomplete = allStyles()
            .filter { (_, style) -> style.fontSize.isUnspecified || style.lineHeight.isUnspecified }
            .map { it.first }

        assertEquals(emptyList<String>(), incomplete)
    }

    private companion object {
        const val EXPECTED_STYLES = 15
    }
}
