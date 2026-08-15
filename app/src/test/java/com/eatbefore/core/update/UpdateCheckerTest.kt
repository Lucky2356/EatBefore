package com.eatbefore.core.update

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The update check against canned GitHub answers.
 *
 * An interceptor stands in for the network rather than a mock web server: no new
 * dependency, and the request still goes through the real client, the real headers and the
 * real parser — which is where the mistakes would be.
 */
class UpdateCheckerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun checker(body: String, code: Int = 200, fail: Boolean = false): UpdateChecker {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    if (fail) throw IOException("no network")
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message("stub")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()
        return UpdateChecker(client, json, UnconfinedTestDispatcher())
    }

    private fun release(
        tag: String,
        assets: String = """[{"name":"EatBefore-$tag.apk","browser_download_url":"https://x/a.apk","size":123}]""",
        extra: String = "",
    ) = """{"tag_name":"$tag","body":"notes"$extra,"assets":$assets}"""

    @Test
    fun `a newer release is offered with its apk`() = runTest {
        val result = checker(release("v2.0.0")).check(currentVersion = "1.9.0")

        val update = (result as UpdateCheckResult.Available).update
        assertEquals(AppVersion(2, 0, 0), update.version)
        assertEquals("https://x/a.apk", update.downloadUrl)
        assertEquals(123L, update.sizeBytes)
    }

    @Test
    fun `the same version is not an update`() = runTest {
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker(release("v1.9.0")).check(currentVersion = "1.9.0"),
        )
    }

    /** Installing an older build must not be talked into "updating" back down. */
    @Test
    fun `an older release is not offered`() = runTest {
        assertEquals(
            UpdateCheckResult.UpToDate,
            checker(release("v1.8.0")).check(currentVersion = "1.9.0"),
        )
    }

    /**
     * A release is published before its APK finishes uploading. Offering a download then
     * would end in a 404 the user can do nothing about, so it is its own answer.
     */
    @Test
    fun `a release without an apk is reported as such`() = runTest {
        val result = checker(release("v2.0.0", assets = "[]")).check(currentVersion = "1.9.0")

        assertEquals(UpdateCheckResult.NoApk, result)
    }

    @Test
    fun `drafts and prereleases are ignored`() = runTest {
        val draft = checker(release("v2.0.0", extra = ""","draft":true"""))
        val pre = checker(release("v2.0.0", extra = ""","prerelease":true"""))

        assertEquals(UpdateCheckResult.UpToDate, draft.check(currentVersion = "1.9.0"))
        assertEquals(UpdateCheckResult.UpToDate, pre.check(currentVersion = "1.9.0"))
    }

    /** Offline is the ordinary state of a phone; it must be a result, not a crash. */
    @Test
    fun `no network is a failure the caller can show`() = runTest {
        val result = checker("", fail = true).check(currentVersion = "1.9.0")

        assertTrue(result is UpdateCheckResult.Failed)
    }

    @Test
    fun `a rate-limited or broken response is a failure`() = runTest {
        assertTrue(
            checker("", code = 403).check(currentVersion = "1.9.0") is UpdateCheckResult.Failed,
        )
        assertTrue(
            checker("not json").check(currentVersion = "1.9.0") is UpdateCheckResult.Failed,
        )
    }

    /** Anything but a version number in the tag means we cannot compare — so we do not. */
    @Test
    fun `an unreadable tag is not announced as an update`() = runTest {
        val result = checker(release("latest")).check(currentVersion = "1.9.0")

        assertTrue(result is UpdateCheckResult.Failed)
    }
}
