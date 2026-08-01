package com.eatbefore.core.diagnostics

import android.os.Build
import com.eatbefore.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes an entry to [DiagnosticsLog] when a thread dies of an uncaught exception, then
 * lets Android proceed exactly as before.
 *
 * Chaining to the previous handler is not optional: it is what actually terminates the
 * process. Replacing it outright would leave a broken app alive and frozen instead of
 * closing it, which is worse than the crash.
 */
@Singleton
class CrashReporter @Inject constructor(private val diagnostics: DiagnosticsLog) {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                diagnostics.record(
                    tag = TAG,
                    message = "app ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}, " +
                        "${Build.MANUFACTURER} ${Build.MODEL}, thread ${thread.name}",
                    error = error,
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                // No platform handler to hand off to — end the process ourselves rather
                // than leave it in an unknown state.
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    private companion object {
        const val TAG = "CRASH"
    }
}
