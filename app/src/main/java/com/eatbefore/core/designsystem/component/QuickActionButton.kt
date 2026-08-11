package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Shapes

/**
 * A large, thumb-friendly quick action: a tonal icon with a caption beneath. The whole
 * column (icon *and* caption) is one clickable target — a tap that lands on the label must
 * work too — and it exposes a single Button node carrying the label to accessibility
 * services.
 *
 * The width is left to the caller: the home screen spreads three of these evenly, and a
 * fixed width there only produced uneven gaps.
 */
@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .selectable(
                selected = false,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = Dimens.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.quickActionSize)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = Shapes.card,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(Dimens.iconLg),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            // One line: labels that wrapped made the row of three ragged and pushed the
            // list further down. The captions are short enough now to fit.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimens.spaceSm),
        )
    }
}
