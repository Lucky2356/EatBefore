package com.eatbefore.feature.ocr

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.theme.Dimens
import com.eatbefore.feature.ocr.camera.OcrCameraView
import com.eatbefore.feature.ocr.camera.takePhoto
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    onDatePicked: (LocalDate) -> Unit,
    onBack: () -> Unit,
    viewModel: OcrViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraUnavailable by remember { mutableStateOf(false) }

    // The photo picker needs no permission at all, which is the point: it is the one way
    // into this screen that works when the camera is refused or busy. A date photographed
    // in the shop and read at home is the other half of it.
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.recognize(it.toString()) } }
    val onPickFromGallery = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    ScreenScaffold(
        title = stringResource(R.string.ocr_title),
        onBack = onBack,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !cameraPermission.status.isGranted -> PermissionPrompt(
                    onGrant = { cameraPermission.launchPermissionRequest() },
                    onPickFromGallery = onPickFromGallery,
                    onManual = onBack,
                )

                state.isRecognizing -> Centered {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ocr_recognizing), modifier = Modifier.padding(top = Dimens.spaceMd))
                }

                state.hasResult -> OcrResultContent(
                    state = state,
                    onPick = onDatePicked,
                    onRetry = viewModel::retry,
                    onManual = onBack,
                )

                else -> CaptureContent(
                    cameraUnavailable = cameraUnavailable,
                    onCaptureReady = { imageCapture = it },
                    onCameraUnavailable = { cameraUnavailable = true },
                    onPickFromGallery = onPickFromGallery,
                    onShutter = {
                        // A shutter that silently does nothing reads as a broken app. Say
                        // the camera is unavailable instead; the date can still be typed.
                        val capture = imageCapture
                        if (capture == null) {
                            cameraUnavailable = true
                            return@CaptureContent
                        }
                        takePhoto(
                            context = context,
                            imageCapture = capture,
                            onSaved = { uri -> viewModel.recognize(uri.toString()) },
                            onError = { cameraUnavailable = true },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CaptureContent(
    cameraUnavailable: Boolean,
    onCaptureReady: (ImageCapture) -> Unit,
    onCameraUnavailable: () -> Unit,
    onPickFromGallery: () -> Unit,
    onShutter: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OcrCameraView(
            onCaptureReady = onCaptureReady,
            modifier = Modifier.fillMaxSize(),
            onUnavailable = onCameraUnavailable,
        )
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Text(
                text = if (cameraUnavailable) {
                    stringResource(R.string.ocr_camera_unavailable)
                } else {
                    stringResource(R.string.ocr_hint)
                },
                color = MaterialTheme.colorScheme.inverseOnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            )
        }
        // Left of the shutter, not beside it: the big round button in the middle is where
        // a hand reaches without looking, and moving it to make room would be a worse
        // trade than the gallery is worth.
        FloatingActionButton(
            onClick = onPickFromGallery,
            modifier = Modifier.align(Alignment.BottomStart).padding(Dimens.spaceXl),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = stringResource(R.string.ocr_pick_from_gallery),
            )
        }
        FloatingActionButton(
            onClick = onShutter,
            modifier = Modifier.align(Alignment.BottomCenter).padding(Dimens.spaceXl).size(72.dp),
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = stringResource(R.string.ocr_capture))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrResultContent(
    state: OcrUiState,
    onPick: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
    ) {
        if (state.candidates.isEmpty()) {
            Text(stringResource(R.string.ocr_none_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.ocr_none_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.ocr_pick_date), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                state.candidates.forEach { candidate ->
                    AssistChip(
                        onClick = { onPick(candidate.date) },
                        label = {
                            Text(
                                candidate.date.toString() +
                                    " · " + stringResource(
                                        R.string.ocr_confidence,
                                        (candidate.confidence * 100).roundToInt(),
                                    ),
                            )
                        },
                    )
                }
            }
        }

        if (state.rawText.isNotBlank()) {
            Text(
                stringResource(R.string.ocr_recognized_text),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    state.rawText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Dimens.spaceMd),
                )
            }
        }

        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ocr_retry))
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ocr_enter_manually))
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, onPickFromGallery: () -> Unit, onManual: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.scanner_permission_rationale),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = Dimens.spaceLg)) {
            Text(stringResource(R.string.scanner_permission_grant))
        }
        // Refusing the camera used to leave typing as the only way out. A photo already in
        // the gallery needs no permission and reads just as well.
        OutlinedButton(onClick = onPickFromGallery, modifier = Modifier.padding(top = Dimens.spaceSm)) {
            Text(stringResource(R.string.ocr_pick_from_gallery))
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.padding(top = Dimens.spaceSm)) {
            Text(stringResource(R.string.ocr_enter_manually))
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
