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
class ExpiryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

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

    fun notify(plan: ExpiryNotificationPlan) {
        if (!plan.hasContent || !hasPermission()) return
        ensureChannel()

        val summary = when (plan.total % 10) {
            1 -> if (plan.total % 100 == 11) {
                context.getString(R.string.notif_summary_many, plan.total)
            } else {
                context.getString(R.string.notif_summary_one)
            }
            in 2..4 -> if (plan.total % 100 in 12..14) {
                context.getString(R.string.notif_summary_many, plan.total)
            } else {
                context.getString(R.string.notif_summary_few, plan.total)
            }
            else -> context.getString(R.string.notif_summary_many, plan.total)
        }

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(summary)
            .setContentText(details.replace("\n", " · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // NotificationManagerCompat.notify is safe here — permission was checked above.
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "expiry_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
