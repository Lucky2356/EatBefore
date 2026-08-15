package com.eatbefore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.core.designsystem.theme.Motion
import com.eatbefore.core.designsystem.theme.Shapes

/**
 * The app's one container: a light fill, a hairline, and a small shrink under the finger.
 *
 * Cards used to be `surfaceContainerHigh` on a tinted page, which put them two barely
 * distinguishable steps apart — a list read as a band of grey slabs, and in the dark theme
 * the boundary all but disappeared. Separation now comes from the line rather than from the
 * fill, which works at both ends of the brightness range, and the fill is free to stay
 * quiet.
 *
 * The press response lives here rather than at forty call sites. It is the only feedback a
 * tap on a card gives besides the ripple, and on a large card a ripple alone is easy to miss.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = Shapes.card,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: Color? = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) Motion.PRESSED_SCALE else 1f,
        animationSpec = Motion.quick(),
        label = "cardPress",
    )

    val clickable = if (onClick == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(),
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(clickable),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border?.let { BorderStroke(Dimens.hairline, it) },
        content = { Column(content = content) },
    )
}
