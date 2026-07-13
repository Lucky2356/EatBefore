package com.eatbefore.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.eatbefore.R

private data class BottomItem(
    val destination: TopLevelDestination,
    val icon: ImageVector,
    val labelRes: Int,
)

private val bottomItems = listOf(
    BottomItem(TopLevelDestination.HOME, Icons.Filled.Home, R.string.nav_home),
    BottomItem(TopLevelDestination.INVENTORY, Icons.Filled.Inventory2, R.string.nav_inventory),
    BottomItem(TopLevelDestination.SCANNER, Icons.Filled.QrCodeScanner, R.string.nav_scanner),
    BottomItem(TopLevelDestination.SHOPPING, Icons.Filled.ShoppingCart, R.string.nav_shopping),
    BottomItem(TopLevelDestination.MORE, Icons.Filled.MoreHoriz, R.string.nav_more),
)

@Composable
fun EatBeforeBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.destination.route,
                onClick = { onNavigate(item.destination) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}

/** Routes that show the bottom navigation bar. */
fun isTopLevelRoute(route: String?): Boolean =
    TopLevelDestination.entries.any { it.route == route }
