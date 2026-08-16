package com.eatbefore.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.eatbefore.R

private data class BottomItem(val destination: TopLevelDestination, val icon: ImageVector, val labelRes: Int)

private val bottomItems = listOf(
    BottomItem(TopLevelDestination.HOME, Icons.Filled.Home, R.string.nav_home),
    BottomItem(TopLevelDestination.INVENTORY, Icons.Filled.Inventory2, R.string.nav_inventory),
    BottomItem(TopLevelDestination.SCANNER, Icons.Filled.QrCodeScanner, R.string.nav_scanner),
    BottomItem(TopLevelDestination.SHOPPING, Icons.Filled.ShoppingCart, R.string.nav_shopping),
    BottomItem(TopLevelDestination.MORE, Icons.Filled.MoreHoriz, R.string.nav_more),
)

/** Bottom navigation with the scanner as the emphasized central action (per the spec). */
@Composable
fun EatBeforeBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    // The bar is fixed-height chrome, so its labels cannot be allowed to grow without
    // limit: at a system font scale of 2.0 every one of them wrapped mid-word and the
    // second line was clipped by the edge of the bar ("Главн/ая", "Скане/р"). Only the
    // font scale is capped — `density` is passed through untouched, so icons, spacing
    // and touch targets still follow the device exactly. Screen content is unaffected
    // and keeps scaling all the way up; this applies to the five labels alone.
    val density = LocalDensity.current
    val barDensity = remember(density) {
        Density(density.density, density.fontScale.coerceAtMost(LABEL_MAX_FONT_SCALE))
    }
    CompositionLocalProvider(LocalDensity provides barDensity) {
        NavigationBar {
            bottomItems.forEach { item ->
                val isScanner = item.destination == TopLevelDestination.SCANNER
                NavigationBarItem(
                    // The tag is surfaced as a resource id (see EatBeforeApp) so the
                    // Baseline Profile generator can navigate regardless of device locale.
                    modifier = Modifier.testTag("nav_${item.destination.route}"),
                    selected = currentRoute == item.destination.route,
                    onClick = { onNavigate(item.destination) },
                    icon = {
                        if (isScanner) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        } else {
                            Icon(item.icon, contentDescription = null)
                        }
                    },
                    label = {
                        // One line, always. The cap above keeps the words whole; this is
                        // the guarantee that a longer translation still cannot wrap.
                        Text(
                            stringResource(item.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = if (isScanner) {
                        // The circle already carries the emphasis; hide the pill indicator.
                        NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surface,
                        )
                    } else {
                        NavigationBarItemDefaults.colors()
                    },
                )
            }
        }
    }
}

/**
 * Ceiling for the navigation labels' text scaling. 1.3 is the largest scale at which all
 * five Russian labels still fit on one line at the narrowest supported width.
 */
private const val LABEL_MAX_FONT_SCALE = 1.3f

/** Routes that show the bottom navigation bar. */
fun isTopLevelRoute(route: String?): Boolean = TopLevelDestination.entries.any { it.route == route }
