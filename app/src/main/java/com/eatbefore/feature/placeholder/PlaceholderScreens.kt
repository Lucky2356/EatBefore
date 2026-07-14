package com.eatbefore.feature.placeholder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
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
import com.eatbefore.core.designsystem.component.EmptyState

/**
 * Foundation-milestone placeholders for features arriving in later milestones. They are
 * honest "coming soon" screens — never dead buttons — and the scanner offers the working
 * manual-add path so the user is never blocked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingPlaceholderScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_shopping)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                message = stringResource(R.string.shopping_placeholder),
                icon = Icons.Outlined.ShoppingCart,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorePlaceholderScreen(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.history_title)) },
                leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenHistory),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
            HorizontalDivider()
            EmptyState(
                message = stringResource(R.string.more_placeholder),
                icon = Icons.Outlined.Construction,
            )
        }
    }
}
