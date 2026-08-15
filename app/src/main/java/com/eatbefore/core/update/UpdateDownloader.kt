package com.eatbefore.core.update

import android.content.Context
import com.eatbefore.core.common.dispatcher.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the release APK into the app's own cache, reporting how far it has got.
 *
 * The shared http client is built for catalogue lookups — five second timeouts, so a slow
 * network never holds up someone adding milk. A 66 MB download needs the opposite, so this
 * borrows the client's connection pool and relaxes the limits for its own calls only.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val downloadClient = client.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Where downloaded installers live. Kept in the cache: the system may reclaim it. */
    private val directory: File get() = File(context.cacheDir, DIRECTORY_NAME)

    fun download(update: AvailableUpdate): Flow<DownloadState> = flow {
        emit(DownloadState.Running(0))

        // One file per version, and the folder is cleared first: a half-written APK from a
        // cancelled attempt must never be handed to the installer.
        directory.deleteRecursively()
        directory.mkdirs()
        val target = File(directory, "EatBefore-${update.version}.apk")

        val request = Request.Builder().url(update.downloadUrl).build()
        val result = runCatching {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty response")
                val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var copied = 0L
                        var lastPercent = 0
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            val percent = if (total > 0) {
                                (copied * PERCENT / total).toInt().coerceIn(0, PERCENT.toInt())
                            } else {
                                0
                            }
                            // Only on change: the UI has no use for a hundred identical
                            // emissions per percent.
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(DownloadState.Running(percent))
                            }
                        }
                    }
                }
                // A truncated file installs as "package appears corrupt", which tells the
                // user nothing. Checking the size says what actually happened.
                if (update.sizeBytes > 0 && target.length() != update.sizeBytes) {
                    error("size mismatch: got ${target.length()} of ${update.sizeBytes}")
                }
                target
            }
        }

        result.fold(
            onSuccess = { emit(DownloadState.Ready(it.absolutePath)) },
            onFailure = { error ->
                target.delete()
                emit(DownloadState.Failed(error.message ?: "download failed"))
            },
        )
    }.flowOn(ioDispatcher)

    /** Removes downloaded installers — called once one has been handed to the installer. */
    fun clear() {
        directory.deleteRecursively()
    }

    private companion object {
        const val DIRECTORY_NAME = "updates"
        const val BUFFER_SIZE = 64 * 1024
        const val PERCENT = 100L
        const val READ_TIMEOUT_SECONDS = 60L
    }
}
