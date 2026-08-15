package com.eatbefore.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A release newer than the one running, with the APK to fetch. */
data class AvailableUpdate(val version: AppVersion, val notes: String, val downloadUrl: String, val sizeBytes: Long)

/** What a check ended with. Failure is a state, not an exception: the UI has to say why. */
sealed interface UpdateCheckResult {
    data class Available(val update: AvailableUpdate) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    /** Reached the release, but it carries no APK — nothing to offer, and not an error. */
    data object NoApk : UpdateCheckResult

    data class Failed(val reason: String) : UpdateCheckResult
}

/** Where the download has got to, for the button that started it. */
sealed interface DownloadState {
    data object Idle : DownloadState

    data class Running(val percent: Int) : DownloadState

    data class Ready(val path: String) : DownloadState

    data class Failed(val reason: String) : DownloadState
}

// The subset of the GitHub release JSON this app reads. Everything else is ignored by the
// shared Json instance, so a change on their side cannot break the parse.

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(val name: String, @SerialName("browser_download_url") val downloadUrl: String, val size: Long = 0)
