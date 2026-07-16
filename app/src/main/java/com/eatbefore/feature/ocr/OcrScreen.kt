package com.eatbefore.feature.ocr

import android.Manifest
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !cameraPermission.status.isGranted -> PermissionPrompt(
                    onGrant = { cameraPermission.launchPermissionRequest() },
                    onManual = onBack,
                )

                state.isRecognizing -> Centered {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ocr_recognizing), modifier = Modifier.padding(top = 12.dp))
                }

                state.hasResult -> OcrResultContent(
                    state = state,
                    onPick = onDatePicked,
                    onRetry = viewModel::retry,
                    onManual = onBack,
                )

                else -> CaptureContent(
                    onCaptureReady = { imageCapture = it },
                    onShutter = {
                        val capture = imageCapture ?: return@CaptureContent
                        takePhoto(
                            context = context,
                            imageCapture = capture,
                            onSaved = { uri -> viewModel.recognize(uri.toString()) },
                            onError = { /* keep preview; user can retry */ },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CaptureContent(onCaptureReady: (ImageCapture) -> Unit, onShutter: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        OcrCameraView(onCaptureReady = onCaptureReady, modifier = Modifier.fillMaxSize())
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.ocr_hint),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        FloatingActionButton(
            onClick = onShutter,
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).size(72.dp),
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.candidates.isEmpty()) {
            Text(stringResource(R.string.ocr_none_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.ocr_none_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.ocr_pick_date), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.padding(12.dp),
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
private fun PermissionPrompt(onGrant: () -> Unit, onManual: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.scanner_permission_rationale),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.scanner_permission_grant))
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.padding(top = 8.dp)) {
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
