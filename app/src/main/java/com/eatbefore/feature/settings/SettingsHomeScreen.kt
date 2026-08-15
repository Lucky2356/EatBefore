package com.eatbefore.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.theme.Dimens

/**
 * The way into settings: a short list of sections, and a search box for when you know the
 * name of the setting but not which section someone filed it under.
 *
 * It used to be one screen with everything on it. That was fine at three sections and
 * unusable at seven — changing the theme meant scrolling past backups and sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf("") }
    val matches = searchSettings(query)
    val prefs by viewModel.state.collectAsStateWithLifecycle()
    // A found release is worth a dot next to "About" and nothing more: it is news the user
    // reads when they happen to be here, not a reason to interrupt them.
    val sectionWithNews = SettingsSection.ABOUT.takeIf { prefs.availableUpdateVersion != null }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.inventory_search_clear),
                            )
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceSm),
            )

            when {
                query.isBlank() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    items(SettingsSection.entries, key = { it.name }) { section ->
                        SectionRow(
                            section = section,
                            hasNews = section == sectionWithNews,
                            onClick = { onOpenSection(section) },
                        )
                    }
                }

                matches.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(Dimens.spaceLg),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        stringResource(R.string.settings_search_nothing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                ) {
                    items(matches, key = { it.titleRes }) { entry ->
                        // The section is named on every result: half of finding a setting
                        // is learning where it lives, so the next search is not needed.
                        ResultRow(
                            title = stringResource(entry.titleRes),
                            section = stringResource(entry.section.titleRes),
                            onClick = { onOpenSection(entry.section) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Settings whose name, section or keywords contain [query]. Empty query matches nothing
 * here — the caller shows the sections instead.
 */
@Composable
private fun searchSettings(query: String): List<SettingEntry> {
    if (query.isBlank()) return emptyList()
    val needle = query.trim().lowercase()
    return SETTINGS_INDEX.filter { entry ->
        val title = stringResource(entry.titleRes).lowercase()
        val section = stringResource(entry.section.titleRes).lowercase()
        val keywords = entry.keywordsRes?.let { stringResource(it).lowercase() }.orEmpty()
        needle in title || needle in section || needle in keywords
    }
}

@Composable
private fun SectionRow(section: SettingsSection, hasNews: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            Box(
                modifier = Modifier
                    .size(SECTION_ICON_SIZE)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(section.titleRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(section.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasNews) {
                Box(
                    modifier = Modifier
                        .size(NEWS_DOT_SIZE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultRow(title: String, section: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    section,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val SECTION_ICON_SIZE = 40.dp
private val NEWS_DOT_SIZE = 8.dp
