package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.domain.model.ExpiryStatus

/**
 * How long one batch has left, drawn at a weight that matches how much it matters.
 *
 * One element, not two. A row used to carry both "169 days left" and a filled «Fresh»
 * pill, which say the same thing — and the pill said it louder, so four healthy products
 * shouted while the one going off did not stand out. Now only the weight changes: a pill
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
) {
    val visual = status.toVisual()
    val text = remainingText(remainingDays) ?: stringResource(R.string.status_no_date)

    if (!visual.needsAttention) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (status == ExpiryStatus.EXPIRING_SOON) {
                visual.color
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = modifier,
        )
        return
    }

    val onStatus = LocalStatusColors.current.onStatus
    Row(
        modifier = modifier
            .background(visual.color, Shapes.pill)
            .padding(horizontal = Dimens.spaceSm, vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = onStatus,
            modifier = Modifier.size(Dimens.iconSm),
        )
        Text(text = text, color = onStatus, style = MaterialTheme.typography.labelLarge)
    }
}
