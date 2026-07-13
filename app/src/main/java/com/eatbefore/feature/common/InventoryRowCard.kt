package com.eatbefore.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eatbefore.core.designsystem.component.StatusBadge
import com.eatbefore.core.designsystem.format.formatQuantity
import com.eatbefore.core.designsystem.format.label
import com.eatbefore.core.designsystem.format.remainingText
import com.eatbefore.domain.model.StorageType

/** A tappable card summarizing one inventory batch. */
@Composable
fun InventoryRowCard(
    row: InventoryRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.productName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    append(formatQuantity(row.quantity, row.unit))
                    append(" · ")
                    val locationLabel = if (row.locationType == StorageType.OTHER) {
                        row.locationName
                    } else {
                        row.locationType.label()
                    }
                    append(locationLabel)
                }
                Text(
                    text = subtitle,
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
