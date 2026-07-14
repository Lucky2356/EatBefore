package com.eatbefore.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TakeoutDining
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eatbefore.core.designsystem.component.StatusBadge
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.label
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.domain.model.StorageType

private fun StorageType.icon(): ImageVector = when (this) {
    StorageType.FRIDGE -> Icons.Outlined.Kitchen
    StorageType.FREEZER -> Icons.Outlined.AcUnit
    StorageType.CUPBOARD -> Icons.Outlined.TakeoutDining
    StorageType.PANTRY -> Icons.Outlined.Warehouse
    StorageType.OTHER -> Icons.Outlined.Place
}

/** A tappable card summarizing one inventory batch. */
@Composable
fun InventoryRowCard(
    row: InventoryRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = row.locationType.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val locationLabel = if (row.locationType == StorageType.OTHER) {
                    row.locationName
                } else {
                    row.locationType.label()
                }
                Text(
                    text = "${formatQuantity(row.quantity, row.unit)} · $locationLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                remainingText(row.remainingDays)?.let { remaining ->
                    Text(
                        text = remaining,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusBadge(status = row.expiryStatus)
        }
    }
}
