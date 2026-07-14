package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A large, thumb-friendly quick action: a tonal icon button with a caption beneath. Touch
 * target is 60dp to stay comfortably above the accessibility minimum; the column is wide
 * enough that Russian labels like «Сканировать» never break mid-word.
 */
@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
