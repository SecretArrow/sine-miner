package com.secretarrow.sineminer.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.secretarrow.sineminer.core.native.NativeBridge
import com.secretarrow.sineminer.data.model.MiningStatus
import com.secretarrow.sineminer.notifications.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns the live mining engine.
 *
 * - Started when the user taps "Start mining".
 * - Stops when the user taps "Stop" or the OS kills the service.
 * - Exposes [status] as a StateFlow so UI (ViewModel) can observe live updates.
 *
 * The mining engine handle is a `Long` returned by [NativeBridge.startMining].
 * For simplicity in MVP, we hold one engine at a time. Multi-engine mining
 * (CPU + GPU) is a future enhancement.
 *
 * Battery/thermal management: this service does NOT enforce battery thresholds
 * itself — that's the [MiningScheduler]'s job (it stops this service when
 * battery drops below the user's threshold).
 */
@AndroidEntryPoint
class MiningForegroundService : Service() {

    @Inject lateinit var notifications: NotificationHelper

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engineHandle: Long = 0L
    private var miningJob: Job? = null

    private val _status = MutableStateFlow(
        MiningStatus(
            isRunning = false,
            hashrateHps = 0.0,
            totalHashes = 0L,
            sharesAccepted = 0,
            sharesRejected = 0,
            elapsedMs = 0L,
            poolConnected = false,
            poolHost = null,
            currentJobId = null,
            currentHeight = 0L,
            currentDifficulty = 0.0,
            lastError = null,
        )
    )
    val status: StateFlow<MiningStatus> = _status.asStateFlow()

    private var startedAt: Long = 0L

    inner class LocalBinder : Binder() {
        fun service(): MiningForegroundService = this@MiningForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMining(intent)
            ACTION_STOP -> { stopMining(); stopSelf() }
            else -> startMining(intent)
        }
        return START_STICKY
    }

    private fun startMining(intent: Intent?) {
        val memoryKib = intent?.getLongExtra(EXTRA_MEMORY_KIB, 256 * 1024L) ?: 256 * 1024L
        val threads = intent?.getIntExtra(EXTRA_THREADS, 1) ?: 1
        val poolHost = intent?.getStringExtra(EXTRA_POOL_HOST)

        startedAt = System.currentTimeMillis()

        // Foreground notification — required by Android for FGS.
        val notification = notifications.buildMiningNotification("Mining: 0 H/s").build()
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NID_FOREGROUND,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        // Start native engine
        try {
            val seed = (System.currentTimeMillis() xor (Thread.currentThread().id shl 32))
            engineHandle = NativeBridge.startMining(memoryKib, threads, seed)
            _status.value = _status.value.copy(isRunning = true, poolHost = poolHost, lastError = null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mining", e)
            notifications.notifyError(e.message ?: "Failed to start mining")
            stopSelf()
            return
        }

        // Simulated mining loop — emits fake hashrate updates so the UI shows life.
        // The real engine integration (Stratum client + work loop) is a follow-up.
        miningJob = scope.launch {
            var totalHashes = 0L
            var hashrate = 0.0
            while (engineHandle != 0L) {
                try {
                    val elapsed = System.currentTimeMillis() - startedAt
                    // Demo: fake hashrate based on memory preset (smaller memory = faster)
                    hashrate = (1024.0 * 1024.0 / memoryKib.toDouble()) * 1_000_000.0
                    totalHashes += (hashrate * 0.5).toLong()
                    _status.value = _status.value.copy(
                        hashrateHps = hashrate,
                        totalHashes = totalHashes,
                        elapsedMs = elapsed,
                    )
                    // Update foreground notification
                    val notif = notifications.buildMiningNotification(
                        "Mining: ${formatHashrate(hashrate)} • ${formatDuration(elapsed)}",
                    ).build()
                    notifications.manager.notify(NotificationHelper.NID_FOREGROUND, notif)
                } catch (e: Exception) {
                    Log.w(TAG, "Mining loop error: ${e.message}")
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun stopMining() {
        miningJob?.cancel()
        miningJob = null
        if (engineHandle != 0L) {
            NativeBridge.stopMining(engineHandle)
            engineHandle = 0L
        }
        _status.value = _status.value.copy(isRunning = false, hashrateHps = 0.0)
    }

    override fun onDestroy() {
        stopMining()
        scope.cancel()
        super.onDestroy()
    }

    private fun formatHashrate(hps: Double): String = when {
        hps >= 1_000_000 -> "%.2f MH/s".format(hps / 1_000_000)
        hps >= 1_000 -> "%.2f kH/s".format(hps / 1_000)
        else -> "%.2f H/s".format(hps)
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%dh %02dm %02ds".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    companion object {
        private const val TAG = "MiningFGS"
        const val ACTION_START = "com.secretarrow.sineminer.action.START_MINING"
        const val ACTION_STOP = "com.secretarrow.sineminer.action.STOP_MINING"
        const val EXTRA_MEMORY_KIB = "memory_kib"
        const val EXTRA_THREADS = "threads"
        const val EXTRA_POOL_HOST = "pool_host"
        const val EXTRA_POOL_PORT = "pool_port"
    }
}
