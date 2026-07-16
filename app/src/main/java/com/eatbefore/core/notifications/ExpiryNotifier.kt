package com.eatbefore.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eatbefore.MainActivity
import com.eatbefore.R
import com.eatbefore.domain.notification.ExpiryNotificationPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds and posts the single, batched expiry notification. Tapping it opens the app. One
 * fixed id is reused so repeated checks replace (not stack) the notification — no spam.
 */
class ExpiryNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /** Returns false if the POST_NOTIFICATIONS permission is missing (Android 13+). */
    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // POST_NOTIFICATIONS is verified by hasPermission() right below; lint can't see
    // through the indirection.
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(plan: ExpiryNotificationPlan) {
        if (!plan.hasContent || !hasPermission()) return
        ensureChannel()

        // Plural rules differ per language; let the resource framework pick the form.
        val summary = context.resources.getQuantityString(
            R.plurals.notif_summary,
            plan.total,
            plan.total,
        )

        val details = buildString {
            if (plan.expiredCount > 0) appendLine(context.getString(R.string.notif_line_expired, plan.expiredCount))
            if (plan.todayCount > 0) appendLine(context.getString(R.string.notif_line_today, plan.todayCount))
            if (plan.soonCount > 0) appendLine(context.getString(R.string.notif_line_soon, plan.soonCount))
        }.trim()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Acting straight from the shade: open the list, or skip today's reminder.
        val inventoryPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_INVENTORY,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_INVENTORY, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_SNOOZE,
            Intent(context, SnoozeReceiver::class.java).apply {
                action = SnoozeReceiver.ACTION_SNOOZE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(summary)
            .setContentText(details.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                context.getString(R.string.notif_action_open_list),
                inventoryPendingIntent,
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_snooze),
                snoozePendingIntent,
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // NotificationManagerCompat.notify is safe here — permission was checked above.
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        /** Set when the user taps "open list", so the app lands on Inventory. */
        const val EXTRA_OPEN_INVENTORY = "com.eatbefore.extra.OPEN_INVENTORY"

        private const val CHANNEL_ID = "expiry_reminders"
        private const val REQUEST_OPEN_INVENTORY = 1
        private const val REQUEST_SNOOZE = 2
    }
}
