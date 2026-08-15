package com.eatbefore.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison is the whole update check in miniature: get it wrong and the app
 * either nags about a release that is already installed or goes quiet about a real one.
 */
class AppVersionTest {

    /**
     * The case this class exists for. As text "1.10.0" sorts *before* "1.9.0", so a string
     * comparison would stop reporting updates the moment the numbers grew a second digit —
     * quietly, and only from the tenth release onwards.
     */
    @Test
    fun `the tenth release is newer than the ninth`() {
        val ninth = AppVersion.parse("1.9.0")!!
        val tenth = AppVersion.parse("1.10.0")!!

        assertTrue(tenth > ninth)
    }

    @Test
    fun `versions compare part by part`() {
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertTrue(AppVersion.parse("1.9.1")!! > AppVersion.parse("1.9.0")!!)
        assertEquals(AppVersion.parse("1.9.0"), AppVersion.parse("1.9.0"))
    }

    /** Releases are tagged `v1.9.0`, so the tag goes in as it comes from GitHub. */
    @Test
    fun `a leading v is not part of the number`() {
        assertEquals(AppVersion.parse("1.9.0"), AppVersion.parse("v1.9.0"))
    }

    @Test
    fun `missing parts count as zero`() {
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2"))
        assertEquals(AppVersion(2, 1, 0), AppVersion.parse("2.1"))
    }

    /** Better no answer than a guess: an unreadable tag must not be announced as an update. */
    @Test
    fun `nonsense is null rather than a guess`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("v"))
    }

    @Test
    fun `a suffix on the patch number is ignored`() {
        assertEquals(AppVersion(1, 9, 0), AppVersion.parse("1.9.0-rc1"))
    }
}
