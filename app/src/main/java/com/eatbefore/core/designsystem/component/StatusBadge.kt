package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.eatbefore.R
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.core.designsystem.theme.Shapes
import com.eatbefore.core.designsystem.theme.StatusEmphasis
import com.eatbefore.core.designsystem.theme.StatusRole
import com.eatbefore.domain.model.ExpiryStatus

/**
 * Visual descriptor for an [ExpiryStatus]: an icon, a text label, its colours and how loudly
 * to draw them. Meaning is always carried by icon + text, so the badge stays understandable
 * with the colour removed.
 *
 * [emphasis] is the whole point of this type. Everything used to be a filled pill, so
 * "fresh" — the most common and least interesting state — shouted on every row while the
 * one item actually going off looked no more important than the four that were fine. Then
 * everything above "soon" was filled, which swung it the other way: a single carton of milk
 * due today painted a saturated orange slab across the row, and the home screen's banner
 * turned a quarter of the screen pure red. Four levels give the scale room to be honest.
 */
data class StatusVisual(val icon: ImageVector, val labelResId: Int, val role: StatusRole, val emphasis: StatusEmphasis)

@Composable
fun ExpiryStatus.toVisual(): StatusVisual {
    val colors = LocalStatusColors.current
    return when (this) {
        ExpiryStatus.FRESH -> StatusVisual(
            Icons.Outlined.CheckCircle,
            R.string.status_fresh,
            colors.fresh,
            StatusEmphasis.QUIET,
        )

        ExpiryStatus.EXPIRING_SOON -> StatusVisual(
            Icons.Outlined.Schedule,
            R.string.status_expiring_soon,
            colors.soon,
            StatusEmphasis.TONED,
        )

        ExpiryStatus.EXPIRES_TODAY -> StatusVisual(
            Icons.Outlined.HourglassBottom,
            R.string.status_expires_today,
            colors.today,
            StatusEmphasis.SOFT,
        )

        ExpiryStatus.EXPIRED -> StatusVisual(
            Icons.Outlined.Warning,
            R.string.status_expired,
            colors.expired,
            StatusEmphasis.LOUD,
        )

        ExpiryStatus.NO_DATE -> StatusVisual(
            Icons.AutoMirrored.Outlined.HelpOutline,
            R.string.status_no_date,
            colors.opened,
            StatusEmphasis.QUIET,
        )
    }
}

/** True when this state is drawn as a chip rather than as bare text. */
val StatusEmphasis.isChip: Boolean
    get() = this == StatusEmphasis.SOFT || this == StatusEmphasis.LOUD

/**
 * The expiry state of one batch.
 *
 * States that need doing something about become a chip and carry their icon; the calm ones
 * are a plain label. Colour alone never carries the meaning either way — the text is
 * always there.
 */
@Composable
fun StatusBadge(
    status: ExpiryStatus,
    modifier: Modifier = Modifier,
) {
    val visual = status.toVisual()
    val label = stringResource(visual.labelResId)

    if (!visual.emphasis.isChip) {
        Text(
            text = label,
            color = statusTextColor(visual),
            style = MaterialTheme.typography.labelLarge,
            modifier = modifier.semantics { contentDescription = label },
        )
        return
    }

    StatusChip(
        visual = visual,
        text = label,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/**
 * The chip form of a status: a rounded tint carrying the icon and a short line of text.
 *
 * Shared with [ExpiryLabel] so that "expires today" looks the same whether the text beside
 * it is the state's name or the number of days left.
 */
@Composable
fun StatusChip(
    visual: StatusVisual,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(visual.role.container, Shapes.pill)
            .padding(horizontal = Dimens.spaceSm, vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = visual.role.onContainer,
            modifier = Modifier.size(Dimens.iconSm),
        )
        Text(
            text = text,
            color = visual.role.onContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The colour of a status drawn as bare text: its own where that is the point, and the
 * page's muted text colour where it is not. A row whose product is fine should read as an
 * ordinary row.
 */
@Composable
fun statusTextColor(visual: StatusVisual) = when (visual.emphasis) {
    StatusEmphasis.TONED -> visual.role.content
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
