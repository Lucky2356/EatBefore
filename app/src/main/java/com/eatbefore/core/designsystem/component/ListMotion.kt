package com.eatbefore.core.designsystem.component

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eatbefore.core.designsystem.theme.Motion

/**
 * Makes one row of a list appear, move and disappear rather than blink.
 *
 * Every list in the app is one an action changes under the user's hand: eating something
 * removes a row, buying it adds one back, undo puts it where it was. All of that used to
 * happen between two frames, which matters most in exactly the case where it is worst — a
 * mis-tap wrote a product off, the list closed the gap instantly, and there was nothing on
 * screen to say what had just gone or from where.
 *
 * Requires the surrounding `items(...)` to have a stable `key`; without one there is no
 * identity to follow and the modifier does nothing.
 */
@Composable
fun LazyItemScope.animatedItem(): Modifier = Modifier.animateItem(
    fadeInSpec = Motion.fadeIn(),
    placementSpec = Motion.listItem(),
    fadeOutSpec = Motion.fadeOut(),
)
