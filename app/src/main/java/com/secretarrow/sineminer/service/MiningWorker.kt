package com.secretarrow.sineminer.service

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secretarrow.sineminer.data.datastore.SettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic worker that checks whether to auto-start mining based on the user's
 * settings (e.g. "auto-start on charge").
 *
 * It does NOT itself mine — it starts the [MiningForegroundService] if the
 * conditions are met. Mining always runs in a foreground service so the user
 * gets a persistent notification and the OS doesn't kill it.
 */
@HiltWorker
class MiningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val settingsStore = SettingsDataStore(applicationContext)
        val miningSettings = settingsStore.miningSettings.let {
            // Take a single snapshot
            var snapshot: com.secretarrow.sineminer.data.model.MiningSettings? = null
            kotlinx.coroutines.coroutineScope {
                it.collect { s ->
                    snapshot = s
                    throw kotlinx.coroutines.CancellationException("snapshot taken")
                }
            }
            snapshot
        } ?: return Result.success()

        if (!miningSettings.autoStartOnCharge) return Result.success()

        val battery = try {
            val bm = applicationContext.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            Pair(level, charging)
        } catch (e: Exception) {
            return Result.success()
        }

        val (level, charging) = battery

        // Stop mining if battery drops below the user's threshold
        if (miningSettings.stopOnLowBattery && level < miningSettings.batteryThresholdPercent) {
            applicationContext.startService(
                Intent(applicationContext, MiningForegroundService::class.java).apply {
                    action = MiningForegroundService.ACTION_STOP
                }
            )
            return Result.success()
        }

        // Auto-start only if charging
        if (!charging) return Result.success()

        // Check if mining service is already running; if not, start it
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val running = am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == MiningForegroundService::class.java.name
        }
        if (running) return Result.success()

        val poolConfig = try {
            var snapshot: com.secretarrow.sineminer.data.model.PoolConfig? = null
            kotlinx.coroutines.coroutineScope {
                settingsStore.poolConfig.collect { p ->
                    snapshot = p
                    throw kotlinx.coroutines.CancellationException("snapshot")
                }
            }
            snapshot
        } catch (e: Exception) { null } ?: com.secretarrow.sineminer.data.model.PoolConfig.DEFAULT

        val intent = Intent(applicationContext, MiningForegroundService::class.java).apply {
            action = MiningForegroundService.ACTION_START
            putExtra(MiningForegroundService.EXTRA_MEMORY_KIB, miningSettings.memoryPreset.memoryKib)
            putExtra(MiningForegroundService.EXTRA_THREADS, miningSettings.threads)
            putExtra(MiningForegroundService.EXTRA_POOL_HOST, poolConfig.host)
            putExtra(MiningForegroundService.EXTRA_POOL_PORT, poolConfig.port)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
        } catch (e: Exception) {
            // Foreground service launch may fail in background; ignore.
        }

        return Result.success()
    }
}
