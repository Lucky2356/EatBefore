package com.eatbefore.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.theme.Dimens

/**
 * The frame every settings section shares: its own title, a back arrow, and the snackbar
 * the view model talks through.
 *
 * Each section gets its own [SettingsViewModel] instance — they are cheap, and the state
 * they show lives in DataStore, so two of them never disagree.
 */
@Composable
fun SettingsSectionScaffold(
    titleRes: Int,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it) }

    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHost.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    ScreenScaffold(
        title = stringResource(titleRes),
        onBack = onBack,
        snackbarHostState = snackbarHost,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            content = content,
        )
    }
}

/** Explanatory line under a section title — the same quiet grey everywhere. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
