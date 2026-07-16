package com.eatbefore.feature.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.feature.scanner.camera.CameraPreview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onOpenBatch: (Long) -> Unit,
    onAddManual: (barcode: String?, expiryEpochDay: Long?) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val snackbarHost = remember { SnackbarHostState() }
    val addedMessage = stringResource(R.string.scanner_added)
    var showManualDialog by remember { mutableStateOf(false) }

    LaunchedEffectAdded(
        addedBatchId = state.addedBatchId,
        snackbarHost = snackbarHost,
        message = addedMessage,
        actionLabel = stringResource(R.string.scanner_open_added),
        onOpenBatch = onOpenBatch,
        onShown = { viewModel.resume() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scanner_title)) },
                actions = {
                    if (cameraPermission.status.isGranted) {
                        IconButton(onClick = viewModel::toggleTorch) {
                            Icon(
                                if (state.torchEnabled) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                                contentDescription = stringResource(R.string.scanner_torch),
                            )
                        }
                        IconButton(onClick = { showManualDialog = true }) {
                            Icon(Icons.Outlined.Keyboard, contentDescription = stringResource(R.string.scanner_manual))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (cameraPermission.status.isGranted) {
                if (state.isScanning) {
                    CameraPreview(
                        torchEnabled = state.torchEnabled,
                        onCode = viewModel::onCodeDetected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ScanOverlay(hint = stringResource(R.string.scanner_hint))
            } else {
                CameraPermissionRequest(
                    onGrant = { cameraPermission.launchPermissionRequest() },
                    onAddManual = { onAddManual(null, null) },
                )
            }

            if (state.isResolving) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(R.string.scanner_looking),
                            color = Color.White,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            state.resolution?.let { resolution ->
                ScanResultDialog(
                    resolution = resolution,
                    onAddOnePackage = { product ->
                        viewModel.addOnePackage(product, resolution.expiryFromCode)
                    },
                    onGoManual = { barcode ->
                        // Reset before navigating so the dialog is gone when the user returns.
                        viewModel.resume()
                        onAddManual(barcode, resolution.expiryFromCode?.toEpochDay())
                    },
                    onDismiss = viewModel::resume,
                )
            }

            if (showManualDialog) {
                ManualCodeDialog(
                    onSubmit = { code ->
                        showManualDialog = false
                        viewModel.onManualCode(code)
                    },
                    onDismiss = { showManualDialog = false },
                )
            }
        }
    }
}

@Composable
private fun ScanOverlay(hint: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp)),
        )
        Text(
            text = hint,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraPermissionRequest(
    onGrant: () -> Unit,
    onAddManual: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.scanner_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(R.string.scanner_permission_grant))
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.scanner_open_settings))
        }
        TextButton(onClick = onAddManual, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.scanner_add_manual))
        }
    }
}

@Composable
private fun ManualCodeDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scanner_manual)) },
        text = {
            OutlinedTextField(
                value = text,
                // GS1 serials (Честный знак) may contain symbols; only whitespace is dropped.
                onValueChange = { text = it.take(128).filterNot { c -> c.isWhitespace() } },
                singleLine = true,
                label = { Text(stringResource(R.string.scanner_manual_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSubmit(text) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ScanResultDialog(
    resolution: ScanResolution,
    onAddOnePackage: (com.eatbefore.domain.model.Product) -> Unit,
    onGoManual: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    when (resolution) {
        is ScanResolution.Found -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.scanner_found_title)) },
            text = {
                Column {
                    Text(resolution.product.name, style = MaterialTheme.typography.titleMedium)
                    resolution.product.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    resolution.expiryFromCode?.let { date ->
                        Text(
                            stringResource(R.string.scanner_expiry_from_code, date.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (resolution.fromNetwork) {
                        Text(
                            stringResource(R.string.scanner_found_network),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onAddOnePackage(resolution.product) }) {
                    Text(stringResource(R.string.scanner_add_one))
                }
            },
            dismissButton = {
                TextButton(onClick = { onGoManual(resolution.product.barcode) }) {
                    Text(stringResource(R.string.scanner_details))
                }
            },
        )

        is ScanResolution.NotFound -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.scanner_not_found_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.scanner_not_found_body, resolution.code))
                    resolution.expiryFromCode?.let { date ->
                        Text(
                            stringResource(R.string.scanner_expiry_from_code, date.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    // The manual entry is not wasted work — say so up front.
                    Text(
                        stringResource(R.string.scanner_remembered_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onGoManual(resolution.code) }) {
                    Text(stringResource(R.string.scanner_add_manual))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        is ScanResolution.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.scanner_error_title)) },
            text = { Text(stringResource(R.string.scanner_error_body)) },
            confirmButton = {
                TextButton(onClick = { onGoManual(resolution.code) }) {
                    Text(stringResource(R.string.scanner_add_manual))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Confirms a quick-add with a snackbar offering to open the batch that was just created,
 * then hands control back to live scanning.
 */
@Composable
private fun LaunchedEffectAdded(
    addedBatchId: Long?,
    snackbarHost: SnackbarHostState,
    message: String,
    actionLabel: String,
    onOpenBatch: (Long) -> Unit,
    onShown: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(addedBatchId) {
        if (addedBatchId != null) {
            val result = snackbarHost.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = true,
            )
            onShown()
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                onOpenBatch(addedBatchId)
            }
        }
    }
}
