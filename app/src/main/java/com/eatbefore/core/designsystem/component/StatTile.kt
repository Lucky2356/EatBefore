package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.LocalStatusColors

/** How much a number wants to be noticed. */
enum class StatTone {
    /** An ordinary count. Most numbers are this. */
    NEUTRAL,

    /**
     * A number that is bad news when it is not zero — spoiled food, thrown away.
     * At zero it renders as [NEUTRAL]: zero wasted is a good result, and painting it
     * red says the opposite of what happened.
     */
    ATTENTION,
}

/**
 * One counted number with its label.
 *
 * The app used to have three of these — one on the home screen, one in analytics, and a
 * hand-rolled pair beside it — which is how "expired" ended up mauve and "discarded" pink
 * regardless of their values: the colour came from whichever Material container role was
 * still free, not from what the number meant.
 *
 * An alarming number is now a tint plus a coloured figure, not a block of solid red. Three
 * of these sit side by side; filling one of them completely made the tile shout louder than
 * the products it was counting.
 */
@Composable
fun StatTile(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.NEUTRAL,
    onClick: (() -> Unit)? = null,
) {
    val alarming = tone == StatTone.ATTENTION && value > 0
    val expired = LocalStatusColors.current.expired

    AppCard(
        modifier = modifier,
        onClick = onClick,
        // Derived from the status colour rather than from a second pair of literals: it is
        // a mid-tone in both themes, so one alpha behaves in both. `expired.container` is
        // the solid red and would defeat the point of a tint.
        containerColor = if (alarming) {
            expired.content.copy(alpha = ALARM_TINT)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = if (alarming) expired.content.copy(alpha = ALARM_BORDER) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = if (alarming) expired.content else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Enough tint to read as "not like the others", not enough to read as an alarm. */
private const val ALARM_TINT = 0.12f
private const val ALARM_BORDER = 0.30f
