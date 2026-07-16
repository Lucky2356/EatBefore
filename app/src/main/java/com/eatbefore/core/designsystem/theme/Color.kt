package com.eatbefore.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Natural, calm palette: greens (fresh food), warm amber (warning), clay red (expired).
// Values are hand-tuned Material 3 tonal roles for light and dark schemes.

internal val md_light_primary = Color(0xFF2E7D5B)
internal val md_light_onPrimary = Color(0xFFFFFFFF)
internal val md_light_primaryContainer = Color(0xFFB4F1CE)
internal val md_light_onPrimaryContainer = Color(0xFF00210F)
internal val md_light_secondary = Color(0xFF4F6354)
internal val md_light_onSecondary = Color(0xFFFFFFFF)
internal val md_light_secondaryContainer = Color(0xFFD1E8D5)
internal val md_light_onSecondaryContainer = Color(0xFF0C1F14)
internal val md_light_tertiary = Color(0xFF3B6470)
internal val md_light_onTertiary = Color(0xFFFFFFFF)
internal val md_light_tertiaryContainer = Color(0xFFBFEAF7)
internal val md_light_onTertiaryContainer = Color(0xFF001F27)
internal val md_light_error = Color(0xFFBA1A1A)
internal val md_light_onError = Color(0xFFFFFFFF)
internal val md_light_errorContainer = Color(0xFFFFDAD6)
internal val md_light_onErrorContainer = Color(0xFF410002)
internal val md_light_background = Color(0xFFF6FBF4)
internal val md_light_onBackground = Color(0xFF181D19)
internal val md_light_surface = Color(0xFFF6FBF4)
internal val md_light_onSurface = Color(0xFF181D19)
internal val md_light_surfaceVariant = Color(0xFFDCE5DC)
internal val md_light_onSurfaceVariant = Color(0xFF404943)
internal val md_light_outline = Color(0xFF707973)
internal val md_light_outlineVariant = Color(0xFFC0C9C0)

// Tonal container roles (cards, sheets, bars). Without these, Material falls back to its
// baseline purple set, which clashes badly with the green surfaces above.
internal val md_light_surfaceDim = Color(0xFFD7DBD5)
internal val md_light_surfaceBright = Color(0xFFF6FBF4)
internal val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
internal val md_light_surfaceContainerLow = Color(0xFFF0F5EE)
internal val md_light_surfaceContainer = Color(0xFFEAF0E8)
internal val md_light_surfaceContainerHigh = Color(0xFFE5EAE3)
internal val md_light_surfaceContainerHighest = Color(0xFFDFE4DD)
internal val md_light_inverseSurface = Color(0xFF2D322D)
internal val md_light_inverseOnSurface = Color(0xFFEEF2EB)
internal val md_light_inversePrimary = Color(0xFF99D5B3)
internal val md_light_scrim = Color(0xFF000000)

internal val md_dark_primary = Color(0xFF99D5B3)
internal val md_dark_onPrimary = Color(0xFF00391D)
internal val md_dark_primaryContainer = Color(0xFF0E5230)
internal val md_dark_onPrimaryContainer = Color(0xFFB4F1CE)
internal val md_dark_secondary = Color(0xFFB6CCBA)
internal val md_dark_onSecondary = Color(0xFF223528)
internal val md_dark_secondaryContainer = Color(0xFF384B3D)
internal val md_dark_onSecondaryContainer = Color(0xFFD1E8D5)
internal val md_dark_tertiary = Color(0xFFA3CDDB)
internal val md_dark_onTertiary = Color(0xFF033541)
internal val md_dark_tertiaryContainer = Color(0xFF224C58)
internal val md_dark_onTertiaryContainer = Color(0xFFBFEAF7)
internal val md_dark_error = Color(0xFFFFB4AB)
internal val md_dark_onError = Color(0xFF690005)
internal val md_dark_errorContainer = Color(0xFF93000A)
internal val md_dark_onErrorContainer = Color(0xFFFFDAD6)
internal val md_dark_background = Color(0xFF101511)
internal val md_dark_onBackground = Color(0xFFDFE4DD)
internal val md_dark_surface = Color(0xFF101511)
internal val md_dark_onSurface = Color(0xFFDFE4DD)
internal val md_dark_surfaceVariant = Color(0xFF404943)
internal val md_dark_onSurfaceVariant = Color(0xFFC0C9C1)
internal val md_dark_outline = Color(0xFF8A938C)
internal val md_dark_outlineVariant = Color(0xFF404943)

internal val md_dark_surfaceDim = Color(0xFF101511)
internal val md_dark_surfaceBright = Color(0xFF353B36)
internal val md_dark_surfaceContainerLowest = Color(0xFF0B0F0C)
internal val md_dark_surfaceContainerLow = Color(0xFF181D19)
internal val md_dark_surfaceContainer = Color(0xFF1C211D)
internal val md_dark_surfaceContainerHigh = Color(0xFF262B27)
internal val md_dark_surfaceContainerHighest = Color(0xFF313632)
internal val md_dark_inverseSurface = Color(0xFFDFE4DD)
internal val md_dark_inverseOnSurface = Color(0xFF2D322D)
internal val md_dark_inversePrimary = Color(0xFF2E7D5B)
internal val md_dark_scrim = Color(0xFF000000)

// Semantic status colors for expiry states, used by StatusBadge. Each status is also
// paired with an icon + label so meaning is never conveyed by color alone.
data class StatusColors(
    val fresh: Color,
    val soon: Color,
    val today: Color,
    val expired: Color,
    val opened: Color,
    val lowQuantity: Color,
    val onStatus: Color,
)

val LightStatusColors = StatusColors(
    fresh = Color(0xFF2E7D5B),
    soon = Color(0xFF8A263B),
    today = Color(0xFFC9760A),
    expired = Color(0xFFBA1A1A),
    opened = Color(0xFF3B6470),
    lowQuantity = Color(0xFF7A5B00),
    onStatus = Color(0xFFFFFFFF),
)

val DarkStatusColors = StatusColors(
    fresh = Color(0xFF99D5B3),
    soon = Color(0xFFFFB2BC),
    today = Color(0xFFFFB77C),
    expired = Color(0xFFFFB4AB),
    opened = Color(0xFFA3CDDB),
    lowQuantity = Color(0xFFF2C14E),
    onStatus = Color(0xFF102015),
)
