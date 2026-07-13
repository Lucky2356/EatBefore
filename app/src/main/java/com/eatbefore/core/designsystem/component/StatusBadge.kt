package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eatbefore.R
import com.eatbefore.core.designsystem.theme.LocalStatusColors
import com.eatbefore.domain.model.ExpiryStatus

/**
 * Visual descriptor for an [ExpiryStatus]: an icon, a text label, and a color. Meaning is
 * always carried by icon + text so the badge remains understandable without color
 * (accessibility / prompt requirement).
 */
data class StatusVisual(
    val icon: ImageVector,
    val labelResId: Int,
    val color: Color,
)

@Composable
fun ExpiryStatus.toVisual(): StatusVisual {
    val colors = LocalStatusColors.current
    return when (this) {
        ExpiryStatus.FRESH -> StatusVisual(Icons.Filled.CheckCircle, R.string.status_fresh, colors.fresh)
        ExpiryStatus.EXPIRING_SOON -> StatusVisual(Icons.Filled.Schedule, R.string.status_expiring_soon, colors.soon)
        ExpiryStatus.EXPIRES_TODAY -> StatusVisual(Icons.Filled.HourglassBottom, R.string.status_expires_today, colors.today)
        ExpiryStatus.EXPIRED -> StatusVisual(Icons.Filled.Warning, R.string.status_expired, colors.expired)
        ExpiryStatus.NO_DATE -> StatusVisual(Icons.AutoMirrored.Filled.HelpOutline, R.string.status_no_date, colors.opened)
    }
}

@Composable
fun StatusBadge(
    status: ExpiryStatus,
    modifier: Modifier = Modifier,
) {
    val visual = status.toVisual()
    val label = stringResource(visual.labelResId)
    val onStatus = LocalStatusColors.current.onStatus
    Row(
        modifier = modifier
            .background(visual.color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = visual.icon,
            contentDescription = null,
            tint = onStatus,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = onStatus,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}
