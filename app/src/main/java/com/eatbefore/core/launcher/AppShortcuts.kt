package com.eatbefore.core.launcher

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.eatbefore.R
import com.eatbefore.navigation.LaunchTarget

/**
 * The menu that appears on a long press of the launcher icon: scan, add, home cooking,
 * shopping — each straight to its screen, without opening the home screen first.
 *
 * Published from code rather than declared in XML: a static shortcut has to name the
 * package, and the debug build's package differs, so the XML would only work in one of
 * them. Code also picks up the current language each time the app starts.
 */
object AppShortcuts {

    private data class Entry(val target: LaunchTarget, @StringRes val label: Int, @DrawableRes val icon: Int)

    private val entries = listOf(
        Entry(LaunchTarget.SCANNER, R.string.shortcut_scan, R.drawable.ic_shortcut_scan),
        Entry(LaunchTarget.ADD, R.string.shortcut_add, R.drawable.ic_shortcut_add),
        Entry(LaunchTarget.HOMEMADE, R.string.shortcut_homemade, R.drawable.ic_shortcut_homemade),
        Entry(LaunchTarget.SHOPPING, R.string.shortcut_shopping, R.drawable.ic_shortcut_shopping),
    )

    fun publish(context: Context) {
        val shortcuts = entries.map { entry ->
            ShortcutInfoCompat.Builder(context, entry.target.name)
                .setShortLabel(context.getString(entry.label))
                .setIcon(IconCompat.createWithResource(context, entry.icon))
                .setIntent(entry.target.intent(context))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
