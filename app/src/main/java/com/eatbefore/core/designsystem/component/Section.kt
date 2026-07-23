package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes

/**
 * The app's one way to group related content: a section label plus a tonal card.
 *
 * Every screen uses this rather than hand-rolling a Card, so elevation, corner radius and
 * inner padding cannot drift apart between screens.
 */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Dimens.spaceXs),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = Shapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = Dimens.spaceLg,
                    vertical = Dimens.spaceSm,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                content = content,
            )
        }
    }
}

/** A row inside a [SectionCard] with a title, supporting text and a switch. */
@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .padding(vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A tappable row that opens something else. The whole row is the touch target. */
@Composable
fun SettingActionRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.minTouchTarget)
            .padding(vertical = Dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        RowText(title, subtitle, Modifier.weight(1f))
    }
}

/** A tappable row showing the current value on the right. */
@Composable
fun SettingValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Dimens.minTouchTarget)
            .padding(vertical = Dimens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
