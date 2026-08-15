package com.eatbefore.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eatbefore.BuildConfig
import com.eatbefore.R
import com.eatbefore.core.designsystem.component.ScreenScaffold
import com.eatbefore.core.designsystem.theme.Dimens

/** "More" tab: entry points to history, analytics, and settings (incl. backup). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.nav_more)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
        ) {
            MoreCard(
                icon = Icons.Outlined.History,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                title = stringResource(R.string.history_title),
                subtitle = stringResource(R.string.more_history_desc),
                onClick = onOpenHistory,
            )
            MoreCard(
                icon = Icons.Outlined.Insights,
                iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                title = stringResource(R.string.analytics_title),
                subtitle = stringResource(R.string.more_analytics_desc),
                onClick = onOpenAnalytics,
            )
            MoreCard(
                icon = Icons.AutoMirrored.Outlined.ListAlt,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                iconBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                title = stringResource(R.string.products_title),
                subtitle = stringResource(R.string.more_products_desc),
                onClick = onOpenProducts,
            )
            MoreCard(
                icon = Icons.Outlined.Settings,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                iconBackground = MaterialTheme.colorScheme.primaryContainer,
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.more_settings_desc),
                onClick = onOpenSettings,
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spaceLg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreCard(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
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
