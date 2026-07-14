package com.eatbefore.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eatbefore.core.designsystem.component.LoadingState
import com.eatbefore.feature.addmanual.AddManualScreen
import com.eatbefore.feature.history.HistoryScreen
import com.eatbefore.feature.home.HomeScreen
import com.eatbefore.feature.inventory.InventoryScreen
import com.eatbefore.feature.ocr.OcrScreen
import com.eatbefore.feature.onboarding.OnboardingScreen
import com.eatbefore.feature.placeholder.MorePlaceholderScreen
import com.eatbefore.feature.product.ProductScreen
import com.eatbefore.feature.shopping.ShoppingScreen
import com.eatbefore.feature.scanner.ScannerScreen
import com.eatbefore.feature.settings.SettingsScreen
import com.eatbefore.navigation.EatBeforeBottomBar
import com.eatbefore.navigation.Routes
import com.eatbefore.navigation.TopLevelDestination
import com.eatbefore.navigation.isTopLevelRoute

@Composable
fun EatBeforeApp(
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val rootState by rootViewModel.state.collectAsStateWithLifecycle()

    when (val s = rootState) {
        is RootState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is RootState.Ready -> MainNavigation(
            startDestination = if (s.onboardingCompleted) Routes.HOME else Routes.ONBOARDING,
        )
    }
}

@Composable
private fun MainNavigation(startDestination: String) {
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
                    onOpenBatch = { navController.navigate(Routes.product(it)) },
                )
            }

            composable(Routes.INVENTORY) {
                InventoryScreen(onOpenBatch = { navController.navigate(Routes.product(it)) })
            }

            composable(Routes.SCANNER) {
                ScannerScreen(
                    onOpenBatch = { navController.navigate(Routes.product(it)) },
                    onAddManual = { barcode -> navController.navigate(Routes.addManual(barcode)) },
                )
            }

            composable(Routes.SHOPPING) { ShoppingScreen() }

            composable(Routes.MORE) {
                MorePlaceholderScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.ADD_MANUAL,
                arguments = listOf(
                    navArgument(Routes.ADD_MANUAL_ARG_BARCODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
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
                ProductScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
