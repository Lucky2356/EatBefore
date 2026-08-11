package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes

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
    val container = if (alarming) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = if (alarming) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
        shape = Shapes.card,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = onContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
        }
    }
}
