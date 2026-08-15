package com.eatbefore.core.update

import com.eatbefore.BuildConfig
import com.eatbefore.core.common.dispatcher.IoDispatcher
import com.eatbefore.data.catalog.openfoodfacts.OpenFoodFactsCatalogProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks GitHub what the newest published release is.
 *
 * The app is installed by hand from GitHub Releases rather than from a store, so there is
 * nothing to tell it that a new version exists — until now that job was done by the owner
 * remembering to look.
 *
 * The repository is public, so the request carries no credentials: nothing to leak, and
 * nothing that could expire. Unauthenticated GitHub allows 60 requests an hour per address,
 * which one check a day does not come close to.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateCheckResult =
        withContext(ioDispatcher) {
            val current = AppVersion.parse(currentVersion)
                ?: return@withContext UpdateCheckResult.Failed("unreadable current version")

            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("User-Agent", OpenFoodFactsCatalogProvider.USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use UpdateCheckResult.Failed("HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: return@use UpdateCheckResult.Failed("empty response")
                    parse(body, current)
                }
            }.getOrElse { error ->
                // No network is the ordinary case here, not an incident: the check runs on
                // a schedule, and a phone is offline often enough.
                UpdateCheckResult.Failed(error.message ?: "network error")
            }
        }

    private fun parse(body: String, current: AppVersion): UpdateCheckResult {
        val release = runCatching { json.decodeFromString<GithubRelease>(body) }
            .getOrElse { return UpdateCheckResult.Failed("bad response") }

        // Drafts and pre-releases are not for the household: they exist while a release is
        // being prepared, and the phone in the kitchen should never be the one testing it.
        if (release.draft || release.prerelease) return UpdateCheckResult.UpToDate

        val version = AppVersion.parse(release.tagName)
            ?: return UpdateCheckResult.Failed("unreadable tag ${release.tagName}")

        return offerIfNewer(release, version, current)
    }

    private fun offerIfNewer(
        release: GithubRelease,
        version: AppVersion,
        current: AppVersion,
    ): UpdateCheckResult {
        if (version <= current) return UpdateCheckResult.UpToDate

        // A release can exist before its APK is uploaded; offering a download then would
        // end in a 404 the user could do nothing about.
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return UpdateCheckResult.NoApk

        return UpdateCheckResult.Available(
            AvailableUpdate(
                version = version,
                notes = release.body.orEmpty(),
                downloadUrl = apk.downloadUrl,
                sizeBytes = apk.size,
            ),
        )
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Lucky2356/EatBefore/releases/latest"
    }
}
