package com.secretarrow.sineminer

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.secretarrow.sineminer.core.native.NativeBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * SineMiner application entry point. Bootstraps Hilt DI, loads the native Rust
 * library, and registers the WorkManager configuration.
 */
@HiltAndroidApp
class SineMinerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Load the native library. The file `libsineminer_core.so` is per-ABI
        // (arm64-v8a, x86_64) and is packaged by the Rust build task.
        try {
            System.loadLibrary(NativeBridge.CORE_LIB_NAME)
            Log.i(TAG, "Loaded native library: ${NativeBridge.CORE_LIB_NAME} (core v${NativeBridge.coreVersion()})")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library ${NativeBridge.CORE_LIB_NAME}", e)
            // We do not crash — the UI will surface the error and disable mining features.
        }
    }

    companion object {
        private const val TAG = "SineMinerApp"
    }
}
