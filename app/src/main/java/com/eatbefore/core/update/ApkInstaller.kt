package com.eatbefore.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.annotation.RequiresApi
import com.eatbefore.core.diagnostics.DiagnosticsLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a downloaded APK over the running app.
 *
 * Uses [PackageInstaller] sessions rather than an install intent: the APK is streamed
 * straight into the session, so no FileProvider and no shared file are involved.
 *
 * How quiet this is depends on the system, and the app cannot promise otherwise. Android 12
 * added [PackageInstaller.SessionParams.setRequireUserAction], which lets an update through
 * without a confirmation dialog — but only for the app that is recorded as the installer of
 * the package. While the APK was being put on by hand, that is the file manager, so the
 * first update through this path still shows the system dialog. Afterwards EatBefore is the
 * installer of record, and the next one can go through silently. Either way the system
 * verifies the signature: an APK signed with a different key cannot replace this one.
 */
@Singleton
class ApkInstaller @Inject constructor(@ApplicationContext private val context: Context, private val diagnostics: DiagnosticsLog) {

    /** Whether the user has allowed this app to install packages at all (Android 8+). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The system screen where that permission is granted. */
    fun unknownSourcesIntent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        android.net.Uri.parse("package:${context.packageName}"),
    )

    /**
     * Hands [apkPath] to the system. Returns false when the session could not even be
     * created; anything after that arrives at [InstallResultReceiver].
     */
    fun install(apkPath: String): Boolean {
        val apk = File(apkPath)
        if (!apk.exists()) return false

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestNoUserAction()
            }
        }

        return runCatching {
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusIntent(sessionId).intentSender)
            }
            true
        }.onFailure { error ->
            diagnostics.record("update", "install session failed", error)
        }.getOrDefault(false)
    }

    /**
     * Asks the system not to interrupt the user. It obliges only when this app is already
     * the installer of record, and quietly falls back to a dialog when it is not — which
     * is why nothing here depends on the request being honoured.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun PackageInstaller.SessionParams.requestNoUserAction() {
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }

    private fun statusIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION_INSTALL_STATUS)
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val APK_NAME = "eatbefore.apk"
    }
}
