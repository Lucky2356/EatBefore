package com.eatbefore.data.catalog

import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsContributor.Companion.isAuthFailure
import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsContributor.Companion.isSaved
import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsContributor.Companion.verboseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the catalog's answer is read.
 *
 * The strings here are what Open Food Facts actually returns, not what it might. The
 * previous code looked for the phrase "user name or password", which this endpoint never
 * says — so a rejected *login* was reported to the user as a rejected *product*, and the
 * one message that would have explained "I entered my password and nothing works" was
 * never shown.
 */
class OpenFoodFactsResponseTest {

    /** Verbatim from `POST /cgi/product_jqm2.pl` with no credentials. */
    private val noCredentials = """{"status_verbose":"no user credentials","status":0}"""
    private val saved = """{"status_verbose":"fields saved","status":1}"""

    @Test
    fun `a saved edit is recognised`() {
        assertTrue(saved.isSaved())
    }

    @Test
    fun `a refusal is not mistaken for a save`() {
        assertFalse(noCredentials.isSaved())
    }

    /** The whole point: this exact body must read as an authentication problem. */
    @Test
    fun `missing credentials read as an authentication failure`() {
        assertTrue(noCredentials.isAuthFailure())
    }

    @Test
    fun `the older wording is still recognised`() {
        assertTrue("""{"status_verbose":"incorrect user name or password","status":0}""".isAuthFailure())
    }

    /** A product the catalog dislikes must not be blamed on the account. */
    @Test
    fun `an unrelated refusal is not an authentication failure`() {
        val body = """{"status_verbose":"no code or invalid code","status":0}"""

        assertFalse(body.isAuthFailure())
        assertEquals("no code or invalid code", body.verboseStatus())
    }

    /** Whitespace in JSON is free-form; the reader must not depend on its absence. */
    @Test
    fun `spacing in the response does not change the reading`() {
        assertTrue("""{ "status" : 1 }""".isSaved())
        assertTrue("""{ "status_verbose" : "no user credentials" }""".isAuthFailure())
    }

    /** Nothing recognisable must not be reported as success. */
    @Test
    fun `an unreadable body is not a save`() {
        assertFalse("<html>Service unavailable</html>".isSaved())
        assertEquals(null, "<html>Service unavailable</html>".verboseStatus())
    }
}
