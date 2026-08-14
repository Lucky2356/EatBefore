package com.eatbefore.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.domain.model.BatchStatus
import com.eatbefore.domain.usecase.ChangeQuantityUseCase
import com.eatbefore.domain.usecase.MarkBatchStatusUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Writes off the one product a reminder is about, straight from the notification shade.
 *
 * The reminder used to offer only "open the list" and "remind me tomorrow", so finishing
 * the milk it warned about meant unlocking the phone, finding the app, finding the row and
 * pressing three things — for an errand the user had already done in the kitchen. When the
 * reminder is about exactly one batch, the two answers it can have are buttons.
 *
 * The action goes through the same use cases as the app, so it lands in history and can be
 * undone there; nothing here is a shortcut around the audit trail.
 */
@AndroidEntryPoint
class ExpiryActionReceiver : BroadcastReceiver() {

    @Inject lateinit var changeQuantity: ChangeQuantityUseCase

    @Inject lateinit var markStatus: MarkBatchStatusUseCase

    @Inject lateinit var diagnostics: DiagnosticsLog

    @Inject @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val batchId = intent.getLongExtra(EXTRA_BATCH_ID, -1L)
        if (batchId <= 0L) return
        val action = intent.action ?: return
        if (action != ACTION_CONSUMED && action != ACTION_DISCARDED) return

        // The notification goes as soon as the button is pressed. Leaving it up while the
        // write happens invites a second press on a batch that is already gone.
        NotificationManagerCompat.from(context).cancel(ExpiryNotifier.NOTIFICATION_ID)

        // The process may be killed the moment onReceive returns; goAsync keeps it alive
        // for the database write, and pendingResult.finish() releases it either way.
        val pendingResult = goAsync()
        CoroutineScope(ioDispatcher).launch {
            runCatching {
                when (action) {
                    ACTION_CONSUMED -> changeQuantity(batchId, 0.0)
                    ACTION_DISCARDED -> markStatus(batchId, BatchStatus.DISCARDED)
                }
            }.onFailure { error ->
                // Nobody is looking at a screen; a silent failure here would look exactly
                // like a press that never registered.
                diagnostics.record("NOTIFICATION", "Could not apply $action to batch $batchId", error)
            }
            // Always: the process is being held open only for this, and not releasing it
            // is a leak the system eventually kills us for.
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_CONSUMED = "com.eatbefore.action.NOTIFICATION_CONSUMED"
        const val ACTION_DISCARDED = "com.eatbefore.action.NOTIFICATION_DISCARDED"
        const val EXTRA_BATCH_ID = "com.eatbefore.extra.BATCH_ID"
    }
}
