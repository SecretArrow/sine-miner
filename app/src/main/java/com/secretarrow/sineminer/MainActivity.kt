package com.secretarrow.sineminer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.secretarrow.sineminer.ui.SineMinerApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the entire Compose UI. Navigation handles all in-app
 * routing; this activity just provides the window, edge-to-edge layout, and
 * the splash screen hand-off.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen — installs a window background that fades out when we
        // call setcontent. Compose takes over from there.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SineMinerApp()
        }
    }
}
