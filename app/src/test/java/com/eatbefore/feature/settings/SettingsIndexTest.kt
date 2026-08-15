package com.eatbefore.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search index is kept by hand, so the thing that can go wrong is forgetting to add a
 * setting to it — and a setting the search cannot find is exactly the complaint the search
 * was built to answer. These are the cheap checks that catch it.
 */
class SettingsIndexTest {

    @Test
    fun `every section can be reached from the search index`() {
        val covered = SETTINGS_INDEX.map { it.section }.toSet()

        assertEquals(
            "a section with no entries is invisible to search",
            SettingsSection.entries.toSet(),
            covered,
        )
    }

    @Test
    fun `no setting is listed twice`() {
        val duplicates = SETTINGS_INDEX.groupBy { it.titleRes }.filterValues { it.size > 1 }

        assertTrue("the same setting must not appear twice: $duplicates", duplicates.isEmpty())
    }

    /** Two sections sharing a route would send half the search results to the wrong screen. */
    @Test
    fun `each section has its own route`() {
        val routes = SettingsSection.entries.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
    }
}
