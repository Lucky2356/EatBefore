package com.eatbefore.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Handles the notification's "remind me tomorrow" action: dismisses today's reminder and
 * lets the regular daily schedule post the next one. Nothing is rescheduled here — the
 * periodic worker already fires again tomorrow, so snoozing is simply "not today".
 */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return
        NotificationManagerCompat.from(context).cancel(ExpiryNotifier.NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_SNOOZE = "com.eatbefore.action.SNOOZE_EXPIRY_REMINDER"
    }
}
