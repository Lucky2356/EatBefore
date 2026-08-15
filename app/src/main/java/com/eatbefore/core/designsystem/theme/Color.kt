package com.eatbefore.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/*
 * Warm neutral surfaces with a green accent.
 *
 * The surfaces used to be tinted green themselves (#F6FBF4 background, #E5EAE3 cards),
 * which put a card barely two steps away from the page it sat on: a list read as a band of
 * grey slabs rather than as separate things. Dark was worse — #262B27 on #101511 is a
 * difference you have to look for. Now the page is a warm off-white, cards are white (dark:
 * one clear step up) and every card carries a hairline in `outlineVariant`; the accent is
 * the only place colour is spent.
 *
 * `primary` deliberately keeps its old value: it is also the launcher icon's background in
 * res/values/colors.xml, and moving one without the other makes the app disagree with its
 * own icon on the home screen.
 */

internal val md_light_primary = Color(0xFF2E7D5B)
internal val md_light_onPrimary = Color(0xFFFFFFFF)
internal val md_light_primaryContainer = Color(0xFFC9EBD8)
internal val md_light_onPrimaryContainer = Color(0xFF04281A)
internal val md_light_secondary = Color(0xFF4F6354)
internal val md_light_onSecondary = Color(0xFFFFFFFF)
internal val md_light_secondaryContainer = Color(0xFFDCE8E0)
internal val md_light_onSecondaryContainer = Color(0xFF0D1F16)
internal val md_light_tertiary = Color(0xFF3B6470)
internal val md_light_onTertiary = Color(0xFFFFFFFF)
internal val md_light_tertiaryContainer = Color(0xFFC9E7F0)
internal val md_light_onTertiaryContainer = Color(0xFF00212A)
internal val md_light_error = Color(0xFFB3261E)
internal val md_light_onError = Color(0xFFFFFFFF)
internal val md_light_errorContainer = Color(0xFFFBDDD9)
internal val md_light_onErrorContainer = Color(0xFF410E0B)
internal val md_light_background = Color(0xFFFCFBFA)
internal val md_light_onBackground = Color(0xFF1B1A17)
internal val md_light_surface = Color(0xFFFCFBFA)
internal val md_light_onSurface = Color(0xFF1B1A17)
internal val md_light_surfaceVariant = Color(0xFFE7E3DB)
internal val md_light_onSurfaceVariant = Color(0xFF57534B)
internal val md_light_outline = Color(0xFF847F76)

/** The hairline around cards. Doing the separating means the fills need not shout. */
internal val md_light_outlineVariant = Color(0xFFE0DCD4)

internal val md_light_surfaceDim = Color(0xFFDEDBD4)
internal val md_light_surfaceBright = Color(0xFFFCFBFA)
internal val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
internal val md_light_surfaceContainerLow = Color(0xFFF7F6F3)
internal val md_light_surfaceContainer = Color(0xFFF2F0EC)
internal val md_light_surfaceContainerHigh = Color(0xFFECEAE5)
internal val md_light_surfaceContainerHighest = Color(0xFFE5E2DC)
internal val md_light_inverseSurface = Color(0xFF31302C)
internal val md_light_inverseOnSurface = Color(0xFFF4F1EC)
internal val md_light_inversePrimary = Color(0xFF8ED2AF)
internal val md_light_scrim = Color(0xFF000000)

internal val md_dark_primary = Color(0xFF8ED2AF)
internal val md_dark_onPrimary = Color(0xFF00391F)
internal val md_dark_primaryContainer = Color(0xFF155238)
internal val md_dark_onPrimaryContainer = Color(0xFFAAEECA)
internal val md_dark_secondary = Color(0xFFB7CCBE)
internal val md_dark_onSecondary = Color(0xFF22352A)
internal val md_dark_secondaryContainer = Color(0xFF2E3F35)
internal val md_dark_onSecondaryContainer = Color(0xFFD3E8DA)
internal val md_dark_tertiary = Color(0xFFA3CDDB)
internal val md_dark_onTertiary = Color(0xFF03353F)
internal val md_dark_tertiaryContainer = Color(0xFF204A56)
internal val md_dark_onTertiaryContainer = Color(0xFFBFE9F5)
internal val md_dark_error = Color(0xFFF2B8B5)
internal val md_dark_onError = Color(0xFF601410)
internal val md_dark_errorContainer = Color(0xFF8C1D18)
internal val md_dark_onErrorContainer = Color(0xFFF9DEDC)
internal val md_dark_background = Color(0xFF121211)
internal val md_dark_onBackground = Color(0xFFE8E5DF)
internal val md_dark_surface = Color(0xFF121211)
internal val md_dark_onSurface = Color(0xFFE8E5DF)
internal val md_dark_surfaceVariant = Color(0xFF48453E)
internal val md_dark_onSurfaceVariant = Color(0xFFB0ABA1)
internal val md_dark_outline = Color(0xFF8A857B)
internal val md_dark_outlineVariant = Color(0xFF33332F)

internal val md_dark_surfaceDim = Color(0xFF121211)
internal val md_dark_surfaceBright = Color(0xFF383733)
internal val md_dark_surfaceContainerLowest = Color(0xFF0C0C0B)
internal val md_dark_surfaceContainerLow = Color(0xFF1A1A18)
internal val md_dark_surfaceContainer = Color(0xFF1F1F1D)
internal val md_dark_surfaceContainerHigh = Color(0xFF292926)
internal val md_dark_surfaceContainerHighest = Color(0xFF343430)
internal val md_dark_inverseSurface = Color(0xFFE8E5DF)
internal val md_dark_inverseOnSurface = Color(0xFF31302C)
internal val md_dark_inversePrimary = Color(0xFF2E7D5B)
internal val md_dark_scrim = Color(0xFF000000)

/**
 * One expiry state, drawn three ways.
 *
 * A state needs all three because the same fact appears at different volumes: as plain text
 * in a list row, as a soft chip when it wants noticing, as a solid chip when it is already
 * too late. Before this, every state had exactly one colour and the choice was between
 * "plain text" and "filled with that colour" — which is why "expires today" arrived as a
 * saturated orange slab and the attention banner as a block of pure red.
 */
data class StatusRole(
    /** Text or icon drawn straight onto the page. */
    val content: Color,
    /** Fill of a soft chip. For [StatusEmphasis.LOUD] states this is the strong colour. */
    val container: Color,
    /** Text on top of [container]. */
    val onContainer: Color,
)

/** How loudly a state is drawn. Only one state in the app is allowed to be [LOUD]. */
enum class StatusEmphasis {
    /** Muted text, the same weight as the rest of the row. Most products are here. */
    QUIET,

    /** Coloured text, no fill. Worth catching on the way past, not worth stopping for. */
    TONED,

    /** Soft tinted chip. Something to deal with today. */
    SOFT,

    /** Solid chip. Already gone off — the one thing in the app that raises its voice. */
    LOUD,
}

data class StatusColors(
    val fresh: StatusRole,
    val soon: StatusRole,
    val today: StatusRole,
    val expired: StatusRole,
    val opened: StatusRole,
)

val LightStatusColors = StatusColors(
    fresh = StatusRole(
        content = Color(0xFF2E7D5B),
        container = Color(0xFFDCF0E5),
        onContainer = Color(0xFF0C3D28),
    ),
    soon = StatusRole(
        content = Color(0xFF8A5A00),
        container = Color(0xFFFBEFD6),
        onContainer = Color(0xFF4A2F00),
    ),
    today = StatusRole(
        content = Color(0xFFA8480F),
        container = Color(0xFFFCE4D6),
        onContainer = Color(0xFF4F1C00),
    ),
    expired = StatusRole(
        content = Color(0xFFB3261E),
        // The only saturated fill in the app.
        container = Color(0xFFB3261E),
        onContainer = Color(0xFFFFFFFF),
    ),
    opened = StatusRole(
        content = Color(0xFF3B6470),
        container = Color(0xFFDBEDF3),
        onContainer = Color(0xFF0B2B33),
    ),
)

val DarkStatusColors = StatusColors(
    fresh = StatusRole(
        content = Color(0xFF8ED2AF),
        container = Color(0xFF183F2E),
        onContainer = Color(0xFFB4EFCF),
    ),
    soon = StatusRole(
        content = Color(0xFFE8B571),
        container = Color(0xFF40300F),
        onContainer = Color(0xFFF7D9A8),
    ),
    today = StatusRole(
        content = Color(0xFFF0A47A),
        container = Color(0xFF4A2412),
        onContainer = Color(0xFFFBD3BC),
    ),
    expired = StatusRole(
        content = Color(0xFFF2B8B5),
        container = Color(0xFF8C1D18),
        onContainer = Color(0xFFFFDAD6),
    ),
    opened = StatusRole(
        content = Color(0xFFA3CDDB),
        container = Color(0xFF1D3A44),
        onContainer = Color(0xFFC7E7F2),
    ),
)
