package com.eatbefore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.eatbefore.core.notifications.ExpiryNotifier
import com.eatbefore.ui.EatBeforeApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Set when launched from the notification's "open list" action. */
    private val openInventory = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openInventory.value = intent.readOpenInventory()
        // Theme (incl. the user-selected mode) is applied inside EatBeforeApp.
        setContent {
            EatBeforeApp(
                openInventory = openInventory.value,
                onOpenInventoryHandled = { openInventory.value = false },
            )
        }
    }

    /** The activity is singleTop-ish in practice: a second tap reuses this instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openInventory.value = intent.readOpenInventory()
    }

    private fun Intent.readOpenInventory(): Boolean =
        getBooleanExtra(ExpiryNotifier.EXTRA_OPEN_INVENTORY, false)
}
