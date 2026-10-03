package com.eatbefore.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eatbefore.R
import com.eatbefore.core.designsystem.format.formatMoney
import com.eatbefore.domain.usecase.WeeklySummary
import com.eatbefore.navigation.LaunchTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Posts the weekly summary. Its own channel, separate from the expiry reminders: the two
 * are different kinds of message, and someone who wants the reminders loud may well want
 * the summary quiet — or off — which only a separate channel lets them choose in system
 * settings.
 */
class WeeklySummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expiryNotifier: ExpiryNotifier,
) {

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.weekly_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.weekly_channel_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // POST_NOTIFICATIONS is checked through ExpiryNotifier.hasPermission() just below.
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(summary: WeeklySummary) {
        if (!expiryNotifier.hasPermission()) return
        ensureChannel()

        val text = buildList {
            add(context.getString(R.string.weekly_line_eaten, summary.eaten))
            val wasted = summary.wastedMoney?.let { money ->
                context.getString(R.string.weekly_line_wasted_money, summary.wasted, formatMoney(money.amount, money.currency))
            } ?: context.getString(R.string.weekly_line_wasted, summary.wasted)
            add(wasted)
            if (summary.dueNextWeek > 0) add(context.getString(R.string.weekly_line_due, summary.dueNextWeek))
        }.joinToString(" · ")

        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            LaunchTarget.INVENTORY.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.weekly_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "weekly_summary"
        const val NOTIFICATION_ID = 2002
        const val REQUEST_OPEN = 21
    }
}
