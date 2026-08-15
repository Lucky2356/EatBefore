package com.eatbefore.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.eatbefore.core.diagnostics.DiagnosticsLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Where the system reports what became of an install session.
 *
 * The interesting case is [PackageInstaller.STATUS_PENDING_USER_ACTION]: the system has
 * decided this update needs confirming, and hands back the dialog to show. That happens
 * whenever the app is not yet the installer of record for itself — the ordinary state
 * after an APK was put on by hand — so it is a normal path, not a failure.
 */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject lateinit var diagnostics: DiagnosticsLog

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmation?.let {
                    // Started from a receiver, so it needs its own task.
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(it) }
                        .onFailure { error ->
                            diagnostics.record("update", "cannot show install dialog", error)
                        }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> Unit // The app is about to be replaced.

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                diagnostics.record("update", "install failed: status=$status, $message")
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.eatbefore.action.INSTALL_STATUS"
    }
}
