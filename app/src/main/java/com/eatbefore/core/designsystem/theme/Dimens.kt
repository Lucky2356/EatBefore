package com.eatbefore.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The app's spacing and shape scale. Screens must use these instead of raw dp literals so
 * spacing stays consistent when a screen is edited in isolation — before this existed the
 * corner radius alone varied between 12, 14, 16 and 20 dp across screens.
 *
 * The scale is a 4 dp grid; only the steps actually needed are named.
 */
object Dimens {
    /** Between an icon and its label, or inside a chip. */
    val spaceXs = 4.dp

    /** Between tightly related lines of text. */
    val spaceSm = 8.dp

    /** Between elements inside a card. */
    val spaceMd = 12.dp

    /** Screen edge padding and the gap between cards. The app's default. */
    val spaceLg = 16.dp

    /** Between sections that should read as separate blocks. */
    val spaceXl = 24.dp

    /** Around empty states and other centered, full-screen content. */
    val spaceXxl = 32.dp

    /** Minimum touch target; below this a control is hard to hit reliably. */
    val minTouchTarget = 48.dp

    /**
     * The line around a card. It, and not a difference of fills, is what separates a card
     * from the page — which is the only thing that works in the dark theme, where every
     * surface is within a few percent of every other.
     */
    val hairline = 1.dp

    /** Primary actions that must be comfortable to hit without looking. */
    val primaryActionHeight = 72.dp

    /** Product thumbnail in a list row. */
    val thumbnailSize = 46.dp

    /** Icon inside a badge or inline with body text. */
    val iconSm = 16.dp

    /** Icon inside a list row's leading square, and in menus. */
    val iconMd = 24.dp

    /** The tonal square of a quick action on the home screen. */
    val quickActionSize = 60.dp

    /** Icon inside that square. */
    val iconLg = 28.dp

    /** Illustration in an empty or error state. */
    val iconXl = 56.dp

    /** The tinted disc that illustration sits in. */
    val emptyStateDisc = 104.dp

    /** Scrollable preview of the diagnostics log — enough to judge it, not enough to bury the buttons. */
    val diagnosticsPreviewHeight = 240.dp
}

/**
 * Corner radii, kept separate so a shape is never re-derived from a raw number.
 *
 * The radius follows the size of the thing: everything used to be [card]'s 20 dp, from a
 * full-width panel down to a thumbnail, and a single radius across three orders of size
 * reads as one flat layer instead of a hierarchy.
 */
object Shapes {
    /** Cards, sheets and other containers. */
    val card = RoundedCornerShape(20.dp)

    /** One row of a list. Tighter than [card] so a list of them reads as lighter. */
    val row = RoundedCornerShape(16.dp)

    /** Controls sitting inside a card: fields, tonal buttons, thumbnails. */
    val control = RoundedCornerShape(12.dp)

    /** Small inline elements — badges, chips. */
    val badge = RoundedCornerShape(8.dp)

    /** Fully rounded, for pills and circular icon backgrounds. */
    val pill = RoundedCornerShape(percent = 50)
}
