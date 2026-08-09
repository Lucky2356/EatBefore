package com.eatbefore.feature.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.ExpiryOnlyDialog
import java.time.LocalDate

/**
 * The screen-level half of quick actions, shared by the home and inventory screens.
 *
 * Two things every screen offering them must have. First, an undo snackbar: a quick action
 * changes stock from a list row, with no card to check first, so a mis-tap has to be
 * recoverable. Second, the one action that cannot be answered silently — buying another
 * package needs a date, and guessing one would put a wrong expiry into the inventory.
 *
 * It both emits that dialog and returns the callback to hand to [InventoryRowCard]; the
 * returned lambda keeps a stable identity so list rows are not recomposed on every frame.
 */
@Composable
fun rememberQuickActionHandler(
    signal: QuickActionSignal?,
    snackbarHostState: SnackbarHostState,
    today: LocalDate,
    onPerform: (QuickAction, Long, LocalDate?) -> Unit,
    onUndo: () -> Unit,
    onConsume: () -> Unit,
): (QuickAction, Long) -> Unit {
    var pendingRepeatBatchId by remember { mutableStateOf<Long?>(null) }

    pendingRepeatBatchId?.let { batchId ->
        ExpiryOnlyDialog(
            title = stringResource(R.string.product_repeat_title),
            today = today,
            onConfirm = { date ->
                pendingRepeatBatchId = null
                onPerform(QuickAction.REPEAT, batchId, date)
            },
            onDismiss = { pendingRepeatBatchId = null },
        )
    }

    val undoLabel = stringResource(R.string.action_undo)
    val message = signal?.messageRes?.let { stringResource(it) }
    LaunchedEffect(signal) {
        if (signal != null && message != null) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (signal.undoable) undoLabel else null,
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo() else onConsume()
        }
    }

    val perform by rememberUpdatedState(onPerform)
    return remember {
        { action: QuickAction, batchId: Long ->
            if (action == QuickAction.REPEAT) {
                pendingRepeatBatchId = batchId
            } else {
                perform(action, batchId, null)
            }
        }
    }
}
