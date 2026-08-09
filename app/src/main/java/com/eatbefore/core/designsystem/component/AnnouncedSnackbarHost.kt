package com.eatbefore.core.designsystem.component

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * A snackbar host that screen readers actually read out.
 *
 * Material3 (checked against 1.4.0) sets no live region on `SnackbarHost` or `Snackbar`,
 * so a snackbar appears and disappears without TalkBack ever mentioning it. That matters
 * here more than usual: the snackbar is where "undo" lives, and an undo nobody hears about
 * is not an undo.
 *
 * Polite rather than Assertive — it should be read after whatever the user is doing, not
 * cut across it.
 */
@Composable
fun AnnouncedSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
