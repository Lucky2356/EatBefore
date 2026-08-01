package com.eatbefore.core.diagnostics

import android.content.Context
import com.eatbefore.core.common.time.AppClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small on-device record of what went wrong. The app deliberately ships without a crash
 * reporting SDK — it promises no trackers and no network beyond the product catalog — but
 * that left it completely blind: exceptions were swallowed on purpose in about ten places
 * so that a half-written cloud file could not take the app down, and no trace remained.
 * If sharing quietly stopped working on the other person's phone, there was nothing to look at.
 *
 * So: plain text, in the app's private storage, never sent anywhere. The user decides
 * whether to share it, through the ordinary system share sheet.
 *
 * Entries must stay free of inventory contents. What is useful here is the failure —
 * "could not read a peer journal", not what was in it.
 */
@Singleton
class DiagnosticsLog @Inject constructor(@ApplicationContext private val context: Context, private val clock: AppClock) {

    private val lock = Any()

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    /**
     * Appends one entry. Callable from a dying thread during a crash, so it never throws:
     * losing the note is bad, but crashing inside the crash handler is worse.
     */
    fun record(tag: String, message: String, error: Throwable? = null) {
        val entry = buildString {
            append(SEPARATOR)
            append(timestamp())
            append(' ')
            append(tag)
            append('\n')
            append(message)
            if (error != null) {
                append('\n')
                append(error.stackTraceToStringSafe())
            }
            append('\n')
        }
        synchronized(lock) {
            runCatching {
                val existing = if (file.exists()) file.readText() else ""
                file.writeText(trimToLimit(existing + entry))
            }
        }
    }

    /** The whole log as shareable text, or null when nothing has been recorded. */
    fun report(): String? = synchronized(lock) {
        runCatching { file.takeIf { it.exists() }?.readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** Number of recorded entries — drives the "nothing to report" state in settings. */
    fun entryCount(): Int = report()?.let { text ->
        text.split(SEPARATOR).count { it.isNotBlank() }
    } ?: 0

    fun clear() {
        synchronized(lock) { runCatching { file.delete() } }
    }

    /**
     * Keeps the newest entries within [MAX_ENTRIES] and [MAX_BYTES]. Trimming happens on
     * whole entries: half a stack trace is worse than no stack trace.
     */
    private fun trimToLimit(text: String): String {
        var entries = text.split(SEPARATOR).filter { it.isNotBlank() }
        if (entries.size > MAX_ENTRIES) {
            entries = entries.takeLast(MAX_ENTRIES)
        }
        while (entries.size > 1 && entries.sumOf { it.length + SEPARATOR.length } > MAX_BYTES) {
            entries = entries.drop(1)
        }
        return entries.joinToString(separator = "") { SEPARATOR + it }
    }

    private fun timestamp(): String = runCatching {
        TIMESTAMP_FORMAT.format(clock.now().atZone(clock.zone()))
    }.getOrElse { "—" }

    /**
     * A stack trace of a deeply nested cause can be enormous; the share sheet passes text
     * through a binder transaction with a hard size limit, so one crash must not be able
     * to make the whole report unsendable.
     */
    private fun Throwable.stackTraceToStringSafe(): String = runCatching {
        val writer = StringWriter()
        PrintWriter(writer).use { printStackTrace(it) }
        writer.toString().take(MAX_TRACE_CHARS)
    }.getOrElse { this::class.java.name }

    private companion object {
        const val FILE_NAME = "diagnostics.txt"
        const val SEPARATOR = "\n----- "
        const val MAX_ENTRIES = 20
        const val MAX_BYTES = 128 * 1024
        const val MAX_TRACE_CHARS = 8_000

        // No withZone(): the value is already in the clock's zone, and overriding it here
        // would silently ignore the injected clock in tests.
        val TIMESTAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
