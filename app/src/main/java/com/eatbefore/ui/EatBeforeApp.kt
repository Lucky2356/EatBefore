package com.eatbefore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eatbefore.core.datastore.ThemeMode
import com.eatbefore.core.designsystem.component.LoadingState
import com.eatbefore.core.designsystem.theme.EatBeforeTheme
import com.eatbefore.feature.addmanual.AddManualScreen
import com.eatbefore.feature.analytics.AnalyticsScreen
import com.eatbefore.feature.history.HistoryScreen
import com.eatbefore.feature.home.HomeScreen
import com.eatbefore.feature.inventory.InventoryScreen
import com.eatbefore.feature.locations.LocationsScreen
import com.eatbefore.feature.more.MoreScreen
import com.eatbefore.feature.ocr.OcrScreen
import com.eatbefore.feature.onboarding.OnboardingScreen
import com.eatbefore.feature.product.ProductScreen
import com.eatbefore.feature.products.ProductsScreen
import com.eatbefore.feature.scanner.ScannerScreen
import com.eatbefore.feature.settings.SettingsHomeScreen
import com.eatbefore.feature.settings.sections.AboutSettingsScreen
import com.eatbefore.feature.settings.sections.AppearanceSettingsScreen
import com.eatbefore.feature.settings.sections.CatalogSettingsScreen
import com.eatbefore.feature.settings.sections.DataSettingsScreen
import com.eatbefore.feature.settings.sections.InventorySettingsScreen
import com.eatbefore.feature.settings.sections.NotificationsSettingsScreen
import com.eatbefore.feature.settings.sections.SharingSettingsScreen
import com.eatbefore.feature.shopping.ShoppingScreen
import com.eatbefore.navigation.EatBeforeBottomBar
import com.eatbefore.navigation.Routes
import com.eatbefore.navigation.TopLevelDestination
import com.eatbefore.navigation.isTopLevelRoute

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EatBeforeApp(
    openInventory: Boolean = false,
    onOpenInventoryHandled: () -> Unit = {},
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val rootState by rootViewModel.state.collectAsStateWithLifecycle()

    // The theme follows user settings, so it lives here rather than in the activity.
    val darkTheme = when (val s = rootState) {
        is RootState.Ready -> when (s.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        else -> isSystemInDarkTheme()
    }
    val dynamicColor = (rootState as? RootState.Ready)?.dynamicColors ?: false

    EatBeforeTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        Surface(
            // Exposes Compose testTags as view resource ids. UI Automator (used by the
            // Baseline Profile generator) cannot see semantics otherwise, and matching on
            // visible text would break on the English locale.
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
        ) {
            when (val s = rootState) {
                is RootState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                is RootState.Ready -> MainNavigation(
                    startDestination = if (s.onboardingCompleted) Routes.HOME else Routes.ONBOARDING,
                    // Only honour the deep link once onboarding is done.
                    openInventory = openInventory && s.onboardingCompleted,
                    onOpenInventoryHandled = onOpenInventoryHandled,
                )
            }
        }
    }
}

@Composable
private fun MainNavigation(
    startDestination: String,
    openInventory: Boolean = false,
    onOpenInventoryHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun navigateTopLevel(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // "Open list" in the expiry notification lands directly on Inventory.
    androidx.compose.runtime.LaunchedEffect(openInventory) {
        if (openInventory) {
            navigateTopLevel(TopLevelDestination.INVENTORY)
            onOpenInventoryHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (isTopLevelRoute(currentRoute)) {
                EatBeforeBottomBar(currentRoute = currentRoute, onNavigate = ::navigateTopLevel)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { forwardEnter() },
            exitTransition = { forwardExit() },
            popEnterTransition = { backEnter() },
            popExitTransition = { backExit() },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onScan = { navController.navigate(Routes.SCANNER) },
                    onAddManual = { navController.navigate(Routes.addManual()) },
                    onOpenShopping = { navController.navigate(Routes.SHOPPING) },
                    // Through the same path as the bottom bar, so the "expired" tile
                    // switches tabs instead of stacking a second inventory on the back stack.
                    onOpenInventory = { navigateTopLevel(TopLevelDestination.INVENTORY) },
                    onOpenBatch = { navController.navigate(Routes.product(it)) },
                )
            }

            composable(Routes.INVENTORY) { entry ->
                // Same handshake as the OCR screen: the camera writes its answer into this
                // entry's saved state and pops, so the code survives the screen that read it.
                val scannedCode by entry.savedStateHandle
                    .getStateFlow<String?>(Routes.SCAN_LOOKUP_RESULT, null)
                    .collectAsStateWithLifecycle()
                InventoryScreen(
                    onOpenBatch = { navController.navigate(Routes.product(it)) },
                    onScanToSearch = { navController.navigate(Routes.SCAN_LOOKUP) },
                    scannedCode = scannedCode,
                    onScannedCodeUsed = {
                        entry.savedStateHandle[Routes.SCAN_LOOKUP_RESULT] = null
                    },
                )
            }

            composable(Routes.SCAN_LOOKUP) {
                ScannerScreen(
                    onOpenBatch = { navController.navigate(Routes.product(it)) },
                    onAddManual = { barcode, expiryEpochDay ->
                        navController.navigate(Routes.addManual(barcode, expiryEpochDay))
                    },
                    onCodeFound = { code ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(Routes.SCAN_LOOKUP_RESULT, code)
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.SCANNER) {
                ScannerScreen(
                    onOpenBatch = { navController.navigate(Routes.product(it)) },
                    onAddManual = { barcode, expiryEpochDay ->
                        navController.navigate(Routes.addManual(barcode, expiryEpochDay))
                    },
                )
            }

            composable(Routes.SHOPPING) { ShoppingScreen() }

            composable(Routes.MORE) {
                MoreScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                    onOpenProducts = { navController.navigate(Routes.PRODUCTS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsHomeScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSection = { section -> navController.navigate(section.route) },
                )
            }

            composable(Routes.SETTINGS_LOCATIONS) {
                LocationsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_APPEARANCE) {
                AppearanceSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_INVENTORY) {
                InventorySettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLocations = { navController.navigate(Routes.SETTINGS_LOCATIONS) },
                )
            }

            composable(Routes.SETTINGS_NOTIFICATIONS) {
                NotificationsSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_DATA) {
                DataSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_SHARING) {
                SharingSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_CATALOG) {
                CatalogSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS_ABOUT) {
                AboutSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.ANALYTICS) {
                AnalyticsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PRODUCTS) {
                ProductsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.ADD_MANUAL,
                arguments = listOf(
                    navArgument(Routes.ADD_MANUAL_ARG_BARCODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Routes.ADD_MANUAL_ARG_EXPIRY) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val recognizedDate by entry.savedStateHandle
                    .getStateFlow<Long?>(Routes.OCR_RESULT_EPOCH_DAY, null)
                    .collectAsStateWithLifecycle()
                AddManualScreen(
                    onSaved = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                    onCaptureExpiry = { navController.navigate(Routes.OCR) },
                    recognizedExpiryEpochDay = recognizedDate,
                )
            }

            composable(Routes.OCR) {
                OcrScreen(
                    onDatePicked = { date ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(Routes.OCR_RESULT_EPOCH_DAY, date.toEpochDay())
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PRODUCT,
                arguments = listOf(navArgument(Routes.PRODUCT_BATCH_ARG) { type = NavType.LongType }),
            ) {
                ProductScreen(
                    onBack = { navController.popBackStack() },
                    // Switching to a sibling batch replaces this card rather than stacking
                    // on top of it: after comparing three cartons of milk, Back has to
                    // return to the list, not walk back through every carton looked at.
                    onOpenBatch = { batchId ->
                        navController.navigate(Routes.product(batchId)) {
                            popUpTo(Routes.PRODUCT) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
