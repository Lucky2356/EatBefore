package com.eatbefore.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.core.designsystem.theme.Motion
import com.eatbefore.domain.model.ExpiryStatus

/**
 * How long one batch has left, drawn at a weight that matches how much it matters.
 *
 * One element, not two. A row used to carry both "169 days left" and a filled «Fresh»
 * pill, which say the same thing — and the pill said it louder, so four healthy products
 * shouted while the one going off did not stand out. Now only the weight changes: a chip
 * for what must be dealt with today, the status colour for what is running out, plain grey
 * for everything else. The text always carries the number, so it says strictly more than
 * the status word it replaced.
 *
 * Shared so that every place listing batches — the stock list, the batches of one product —
 * says the same thing the same way.
 */
@Composable
fun ExpiryLabel(
    status: ExpiryStatus,
    remainingDays: Long?,
    modifier: Modifier = Modifier,
    /**
     * Drops the state word from an expired batch, leaving the number: "2 days ago" rather
     * than "Expired 2 days ago". For rows sitting under a heading that already reads
     * "Expired" — the word was the widest part of the widest label in the app, and it was
     * pushing the product's own name out of its row.
     */
    concise: Boolean = false,
) {
    val visual = status.toVisual()
    val text = when {
        concise && remainingDays != null && remainingDays < 0 ->
            stringResource(R.string.expired_days_ago_short, (-remainingDays).toInt())

        else -> remainingText(remainingDays) ?: stringResource(R.string.status_no_date)
    }

    if (visual.emphasis.isChip) {
        StatusChip(visual = visual, text = text, modifier = modifier)
        return
    }

    // Crossing rather than cutting: a batch opened from the row's own menu moves from
    // "12 days" to "3 days" and to a different colour in the same breath, and a hard swap
    // between two greys is easy to miss entirely.
    val color by animateColorAsState(
        targetValue = statusTextColor(visual),
        animationSpec = Motion.quick(),
        label = "expiryColor",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}
