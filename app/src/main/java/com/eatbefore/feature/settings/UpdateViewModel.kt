package com.eatbefore.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eatbefore.BuildConfig
import com.eatbefore.core.update.ApkInstaller
import com.eatbefore.core.update.AvailableUpdate
import com.eatbefore.core.update.DownloadState
import com.eatbefore.core.update.UpdateCheckResult
import com.eatbefore.core.update.UpdateChecker
import com.eatbefore.core.update.UpdateDownloader
import com.eatbefore.core.update.UpdatePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val isChecking: Boolean = false,
    val available: AvailableUpdate? = null,
    val download: DownloadState = DownloadState.Idle,
    /** Set after a check that found nothing or failed — the answer, in the user's words. */
    val messageRes: Int? = null,
    /** True when the system has not been told this app may install packages. */
    val needsInstallPermission: Boolean = false,
)

/**
 * The update row in "About": ask GitHub, fetch the APK, hand it to the system.
 *
 * Kept apart from [SettingsViewModel] because it owns work that outlives a tap — a running
 * download has to survive scrolling away and back — and because everything here is about
 * one thing, unlike the settings view model that serves every section.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val downloader: UpdateDownloader,
    private val installer: ApkInstaller,
    private val updatePreferences: UpdatePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    fun check() {
        if (_state.value.isChecking) return
        _state.update { it.copy(isChecking = true, messageRes = null) }
        viewModelScope.launch {
            when (val result = updateChecker.check()) {
                is UpdateCheckResult.Available -> {
                    updatePreferences.record(result.update.version.toString())
                    _state.update { it.copy(isChecking = false, available = result.update) }
                }

                UpdateCheckResult.UpToDate -> {
                    updatePreferences.record(null)
                    _state.update {
                        it.copy(
                            isChecking = false,
                            available = null,
                            messageRes = com.eatbefore.R.string.update_up_to_date,
                        )
                    }
                }

                // A published release whose APK has not finished uploading. Saying "no
                // update" would be a lie, and saying "error" would send the user looking
                // for a problem on their side.
                UpdateCheckResult.NoApk -> _state.update {
                    it.copy(
                        isChecking = false,
                        available = null,
                        messageRes = com.eatbefore.R.string.update_no_apk,
                    )
                }

                is UpdateCheckResult.Failed -> _state.update {
                    it.copy(
                        isChecking = false,
                        messageRes = com.eatbefore.R.string.update_check_failed,
                    )
                }
            }
        }
    }

    fun download() {
        val update = _state.value.available ?: return
        if (!installer.canInstall()) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        // Clearing it here rather than on returning from the system screen: coming back is
        // not the same as having granted it, and this is the moment we actually know.
        _state.update { it.copy(needsInstallPermission = false) }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloader.download(update).collect { progress ->
                _state.update { it.copy(download = progress) }
                if (progress is DownloadState.Ready) install(progress.path)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloader.clear()
        _state.update { it.copy(download = DownloadState.Idle) }
    }

    private fun install(path: String) {
        val started = installer.install(path)
        if (!started) {
            _state.update {
                it.copy(
                    download = DownloadState.Failed("install"),
                    messageRes = com.eatbefore.R.string.update_install_failed,
                )
            }
        }
    }

    /** The system screen where installing from this source is allowed. */
    fun unknownSourcesIntent(): Intent = installer.unknownSourcesIntent()

    fun installPermissionHandled() {
        _state.update { it.copy(needsInstallPermission = false) }
    }

    fun consumeMessage() {
        _state.update { it.copy(messageRes = null) }
    }

    private fun MutableStateFlow<UpdateUiState>.update(block: (UpdateUiState) -> UpdateUiState) {
        value = block(value)
    }
}
