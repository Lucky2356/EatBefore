package com.eatbefore

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.core.notifications.ExpiryNotifier
import com.eatbefore.domain.usecase.ImportShoppingTextUseCase
import com.eatbefore.navigation.LaunchTarget
import com.eatbefore.ui.EatBeforeApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var importShoppingText: ImportShoppingTextUseCase

    @Inject lateinit var diagnostics: DiagnosticsLog

    /** Where the intent that started or resumed us asked to go; consumed once handled. */
    private val launchTarget = mutableStateOf<LaunchTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, and before enableEdgeToEdge: this is what swaps the launch
        // window's theme for the app's. Skip it and the activity keeps wearing the splash
        // theme for its whole life.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the intent is the same one, already handled.
        if (savedInstanceState == null) handle(intent)
        // Theme (incl. the user-selected mode) is applied inside EatBeforeApp.
        setContent {
            EatBeforeApp(
                launchTarget = launchTarget.value,
                onLaunchTargetHandled = { launchTarget.value = null },
            )
        }
    }

    /** The activity is singleTop-ish in practice: a second tap reuses this instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            importSharedList(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            return
        }
        launchTarget.value = LaunchTarget.from(intent)
            // The reminder's «open the list» predates LaunchTarget; old pending intents
            // still in the shade carry the old flag.
            ?: LaunchTarget.INVENTORY.takeIf { intent.getBooleanExtra(ExpiryNotifier.EXTRA_OPEN_INVENTORY, false) }
    }

    /**
     * A list shared from a messenger lands on the shopping list, one entry per line or
     * comma, and the app opens there so the result is visible at once.
     */
    private fun importSharedList(text: String) {
        lifecycleScope.launch {
            val added = runCatching { importShoppingText(text) }
                .onFailure { diagnostics.record("SHARE", "Could not import a shared list", it) }
                .getOrDefault(0)
            val message = if (added > 0) {
                resources.getQuantityString(R.plurals.share_import_done, added, added)
            } else {
                getString(R.string.share_import_empty)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            launchTarget.value = LaunchTarget.SHOPPING
        }
    }
}
