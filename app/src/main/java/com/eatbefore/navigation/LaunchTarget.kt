package com.eatbefore.navigation

import android.content.Context
import android.content.Intent
import com.eatbefore.MainActivity

/**
 * Where the app should open when something outside it asks: the reminder's «open the
 * list», a launcher shortcut, the quick-settings tile, a list shared from a messenger.
 *
 * One enum and one extra instead of a flag per caller. The reminder used to carry a
 * boolean of its own, and every new way in would have added another.
 */
enum class LaunchTarget {
    INVENTORY,
    SCANNER,
    ADD,
    HOMEMADE,
    SHOPPING,
    ;

    /** An intent that opens the app on this screen, for shortcuts, the tile and the reminder. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(EXTRA, name)
        // Reuse the running app instead of stacking a second copy on top of it.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    companion object {
        const val EXTRA = "com.eatbefore.extra.LAUNCH_TARGET"

        fun from(intent: Intent?): LaunchTarget? =
            intent?.getStringExtra(EXTRA)?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
