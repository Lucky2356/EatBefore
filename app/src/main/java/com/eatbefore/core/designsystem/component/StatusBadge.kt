package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eatbefore.R
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.domain.model.ExpiryStatus

/**
 * Visual descriptor for an [ExpiryStatus]: an icon, a text label, and a color. Meaning is
 * always carried by icon + text so the badge remains understandable without color
 * (accessibility / prompt requirement).
 *
 * [needsAttention] decides how loudly it is drawn, and it means exactly what the home
 * screen's summary counts: already off, or gone by tonight. Everything used to be a filled
 * pill, so "fresh" — the most common and least interesting state — shouted on every row
 * while the one item actually going off looked no more important than the four that were
 * fine.
 *
 * "Expiring soon" deliberately sits in between: it is a coloured label, not a pill. Three
 * days left is worth noticing on the way past, not worth the same alarm as milk that has
 * to be drunk tonight.
 */
data class StatusVisual(val icon: ImageVector, val labelResId: Int, val color: Color, val needsAttention: Boolean)

@Composable
fun ExpiryStatus.toVisual(): StatusVisual {
    val colors = LocalStatusColors.current
    return when (this) {
        ExpiryStatus.FRESH ->
            StatusVisual(Icons.Filled.CheckCircle, R.string.status_fresh, colors.fresh, needsAttention = false)
        ExpiryStatus.EXPIRING_SOON ->
            StatusVisual(Icons.Filled.Schedule, R.string.status_expiring_soon, colors.soon, needsAttention = false)
        ExpiryStatus.EXPIRES_TODAY ->
            StatusVisual(Icons.Filled.HourglassBottom, R.string.status_expires_today, colors.today, needsAttention = true)
        ExpiryStatus.EXPIRED ->
            StatusVisual(Icons.Filled.Warning, R.string.status_expired, colors.expired, needsAttention = true)
        ExpiryStatus.NO_DATE ->
            StatusVisual(
                Icons.AutoMirrored.Filled.HelpOutline,
                R.string.status_no_date,
                colors.opened,
                needsAttention = false,
            )
    }
}

/**
 * The expiry state of one batch.
 *
 * States that need doing something about are filled and carry their icon; the calm ones
 * are a plain coloured label. Colour alone never carries the meaning either way — the
 * text is always there.
 */
@Composable
fun StatusBadge(
    status: ExpiryStatus,
    modifier: Modifier = Modifier,
) {
    val visual = status.toVisual()
    val label = stringResource(visual.labelResId)

    if (!visual.needsAttention) {
        Text(
            text = label,
            color = visual.color,
            style = MaterialTheme.typography.labelLarge,
            modifier = modifier.semantics { contentDescription = label },
        )
        return
    }

    val onStatus = LocalStatusColors.current.onStatus
    Row(
        modifier = modifier
            .background(visual.color, Shapes.pill)
            .padding(horizontal = Dimens.spaceSm, vertical = Dimens.spaceXs)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = onStatus,
            modifier = Modifier.size(Dimens.iconSm),
        )
        Text(
            text = label,
            color = onStatus,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
