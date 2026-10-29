package com.secretarrow.sineminer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level navigation destinations for SineMiner.
 *
 * The order here is the order shown in the bottom navigation bar.
 */
enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    DASHBOARD(
        route = "dashboard",
        labelRes = com.secretarrow.sineminer.R.string.nav_dashboard,
        icon = Icons.Filled.Dashboard,
        iconSelected = Icons.Filled.Dashboard,
    ),
    MINING(
        route = "mining",
        labelRes = com.secretarrow.sineminer.R.string.nav_mining,
        icon = Icons.Filled.Apps,
        iconSelected = Icons.Filled.Apps,
    ),
    WALLET(
        route = "wallet",
        labelRes = com.secretarrow.sineminer.R.string.nav_wallet,
        icon = Icons.Outlined.AccountBalanceWallet,
        iconSelected = Icons.Outlined.AccountBalanceWallet,
    ),
    POOL(
        route = "pool",
        labelRes = com.secretarrow.sineminer.R.string.nav_pool,
        icon = Icons.Filled.Apps,
        iconSelected = Icons.Filled.Apps,
    ),
    LOGS(
        route = "logs",
        labelRes = com.secretarrow.sineminer.R.string.nav_logs,
        icon = Icons.AutoMirrored.Filled.ListAlt,
        iconSelected = Icons.AutoMirrored.Filled.ListAlt,
    ),
    SETTINGS(
        route = "settings",
        labelRes = com.secretarrow.sineminer.R.string.nav_settings,
        icon = Icons.Filled.Settings,
        iconSelected = Icons.Filled.Settings,
    ),
    ;

    companion object {
        val START: TopDestination = DASHBOARD
    }
}

/** Routes for nested screens (e.g. wallet create/import). */
object SineMinerRoutes {
    const val WALLET_CREATE = "wallet/create"
    const val WALLET_IMPORT = "wallet/import"
    const val WALLET_DETAIL = "wallet/detail"
    const val WALLET_BACKUP = "wallet/backup"
}
