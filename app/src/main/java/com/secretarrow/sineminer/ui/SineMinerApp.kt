package com.secretarrow.sineminer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secretarrow.sineminer.ui.components.SineMinerBottomBar
import com.secretarrow.sineminer.ui.navigation.SineMinerNavGraph
import com.secretarrow.sineminer.ui.navigation.TopDestination
import com.secretarrow.sineminer.ui.theme.SineMinerTheme

/**
 * SineMiner app root composable. Owns the Scaffold + bottom navigation + nav host.
 *
 * This is the single entry point called from MainActivity.
 */
@Composable
fun SineMinerApp() {
    SineMinerTheme {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        // Show bottom bar only on top destinations.
        val showBottomBar = TopDestination.entries.any { it.route == currentRoute }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    SineMinerBottomBar(
                        currentRoute = currentRoute ?: TopDestination.START.route,
                        onNavigate = { dest ->
                            if (dest.route != currentRoute) {
                                navController.navigate(dest.route) {
                                    // Pop back to start to avoid building a stack.
                                    popUpTo(TopDestination.START.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            SineMinerNavGraph(
                navController = navController,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
