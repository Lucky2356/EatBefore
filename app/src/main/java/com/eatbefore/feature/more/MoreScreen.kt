package com.eatbefore.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eatbefore.R

/** "More" tab: entry points to history, analytics, and settings (incl. backup). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.history_title)) },
                leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenHistory),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.analytics_title)) },
                leadingContent = { Icon(Icons.Outlined.Insights, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAnalytics),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
            HorizontalDivider()
        }
    }
}
