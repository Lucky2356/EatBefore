package com.eatbefore.core.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eatbefore.testutil.FakeAppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DiagnosticsLogTest {

    private lateinit var context: Context
    private lateinit var clock: FakeAppClock
    private lateinit var log: DiagnosticsLog

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clock = FakeAppClock(Instant.parse("2026-08-01T09:30:00Z"))
        log = DiagnosticsLog(context, clock)
        File(context.filesDir, "diagnostics.txt").delete()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "diagnostics.txt").delete()
    }

    @Test
    fun `nothing recorded means nothing to report`() {
        assertNull(log.report())
        assertEquals(0, log.entryCount())
    }

    @Test
    fun `an entry carries the time, the tag and the message`() {
        log.record("SYNC", "Exchange did not finish")

        val report = log.report()!!
        assertTrue(report, report.contains("2026-08-01 09:30:00"))
        assertTrue(report, report.contains("SYNC"))
        assertTrue(report, report.contains("Exchange did not finish"))
    }

    @Test
    fun `the stack trace is kept, since it is the only thing worth reading later`() {
        log.record("CRASH", "Died", IllegalStateException("no folder"))

        val report = log.report()!!
        assertTrue(report, report.contains("IllegalStateException"))
        assertTrue(report, report.contains("no folder"))
    }

    @Test
    fun `entries accumulate rather than replace one another`() {
        log.record("A", "first")
        log.record("B", "second")

        val report = log.report()!!
        assertTrue(report, report.contains("first"))
        assertTrue(report, report.contains("second"))
        assertEquals(2, log.entryCount())
    }

    /** A failure that repeats every 15 minutes must not grow the file without bound. */
    @Test
    fun `only the newest entries are kept`() {
        repeat(30) { log.record("LOOP", "attempt $it") }

        val report = log.report()!!
        assertEquals(20, log.entryCount())
        assertTrue("the oldest must be gone", !report.contains("попытка 0\n"))
        assertTrue("the newest must be there", report.contains("attempt 29"))
    }

    /** Trimming works on whole entries: half a stack trace is worse than none. */
    @Test
    fun `a huge trace does not push the file past its size limit`() {
        val deep = RuntimeException("x".repeat(50_000))
        repeat(20) { log.record("SIZE", "entry $it", deep) }

        assertTrue("log must stay bounded", log.report()!!.length <= 128 * 1024)
    }

    @Test
    fun `clearing leaves nothing behind`() {
        log.record("A", "first")
        log.clear()

        assertNull(log.report())
        assertEquals(0, log.entryCount())
    }

    /** The crash handler runs on a dying thread; a failure to log must not add a crash. */
    @Test
    fun `recording survives an unwritable directory`() {
        val blocked = DiagnosticsLog(context, clock)
        File(context.filesDir, "diagnostics.txt").mkdirs() // a directory where a file is expected

        blocked.record("СБОЙ", "не должно бросить", RuntimeException("boom"))

        assertNull(blocked.report())
        File(context.filesDir, "diagnostics.txt").delete()
    }
}
