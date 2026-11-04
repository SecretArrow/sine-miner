package com.secretarrow.sineminer.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.secretarrow.sineminer.ui.dashboard.DashboardScreen
import com.secretarrow.sineminer.ui.logs.LogsScreen
import com.secretarrow.sineminer.ui.mining.MiningScreen
import com.secretarrow.sineminer.ui.pool.PoolScreen
import com.secretarrow.sineminer.ui.settings.SettingsScreen
import com.secretarrow.sineminer.ui.wallet.CreateWalletScreen
import com.secretarrow.sineminer.ui.wallet.ImportWalletScreen
import com.secretarrow.sineminer.ui.wallet.WalletScreen
/**
 * SineMiner navigation graph.
 *
 * - Top destinations use no transition (instant switch, since they live in a
 *   Scaffold with bottom nav).
 * - Nested wallet screens use a slide-in / slide-out transition.
 */
@Composable
fun SineMinerNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopDestination.START.route,
        modifier = modifier,
    ) {
        // Top destinations
        composable(TopDestination.DASHBOARD.route) {
            DashboardScreen(
                onNavigateToMining = { navController.navigate(TopDestination.MINING.route) },
                onNavigateToWallet = { navController.navigate(TopDestination.WALLET.route) },
            )
        }
        composable(TopDestination.MINING.route) {
            MiningScreen()
        }
        composable(TopDestination.WALLET.route) {
            WalletScreen(
                onCreateWallet = { navController.navigate(SineMinerRoutes.WALLET_CREATE) },
                onImportWallet = { navController.navigate(SineMinerRoutes.WALLET_IMPORT) },
            )
        }
        composable(TopDestination.POOL.route) {
            PoolScreen()
        }
        composable(TopDestination.LOGS.route) {
            LogsScreen()
        }
        composable(TopDestination.SETTINGS.route) {
            SettingsScreen()
        }

        // Nested wallet screens
        composable(
            route = SineMinerRoutes.WALLET_CREATE,
            enterTransition = { slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { slideOutHorizontally(tween(180)) { it } + fadeOut(tween(180)) },
        ) {
            CreateWalletScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = SineMinerRoutes.WALLET_IMPORT,
            enterTransition = { slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { slideOutHorizontally(tween(180)) { it } + fadeOut(tween(180)) },
        ) {
            ImportWalletScreen(onBack = { navController.popBackStack() })
        }
    }
}
