package com.eatbefore.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eatbefore.R

/**
 * The one typeface the app draws with: Onest, under the SIL Open Font License (the licence
 * ships in the APK at assets/licenses/onest-ofl.txt, as redistribution requires).
 *
 * Chosen for its Cyrillic, which was drawn as part of the design rather than bolted on
 * afterwards — this app is read in Russian, and a face whose Latin is the real one and whose
 * Cyrillic is an afterthought shows it at every «д» and «ж». Checked before committing: all
 * 64 letters of А–я plus Ё, ₽ and №.
 *
 * One variable file for every weight, 189 KB. Four static cuts would have cost three times
 * that and still only offered four weights.
 *
 * Kept as a single symbol because [EatBeforeTypography] has to name it fifteen times: a
 * scale that sets only the styles someone happened to need leaves the rest on the platform
 * default, and the two typefaces then sit side by side in the same row with nothing to
 * announce it. That is exactly what used to happen here — six styles were defined and five
 * more were in daily use.
 */
internal val AppFontFamily = FontFamily(
    onest(FontWeight.Normal),
    onest(FontWeight.Medium),
    onest(FontWeight.SemiBold),
    onest(FontWeight.Bold),
)

/**
 * One instance of the variable file, pinned to [weight] on its `wght` axis.
 *
 * The axis has to be set explicitly. Without it every entry is the same 400-weight default
 * and the family silently collapses: nothing fails, headings simply stop being heavier than
 * body text, which is the sort of thing that survives review because it looks deliberate.
 */
@OptIn(ExperimentalTextApi::class)
private fun onest(weight: FontWeight) = Font(
    resId = R.font.onest_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * Every Material style, defined once.
 *
 * Sizes run a little above stock Material 3: the app is read at arm's length with a fridge
 * door open, and the brief asked for as little small text as possible. Large sizes get
 * negative tracking, which is what keeps a heading from looking like it has been stretched.
 *
 * The hierarchy is deliberate. A product's name (`titleMedium`) must be heavier than the
 * heading of the section it sits under (`labelLarge`, drawn muted at the call site) —
 * before, both were `titleMedium` and the eye had nothing to lead it down the screen.
 */
val EatBeforeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.7).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)
