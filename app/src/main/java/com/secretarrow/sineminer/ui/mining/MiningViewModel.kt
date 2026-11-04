package com.secretarrow.sineminer.ui.mining

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretarrow.sineminer.data.model.MemoryPreset
import com.secretarrow.sineminer.data.model.MiningSettings
import com.secretarrow.sineminer.data.model.MiningStatus
import com.secretarrow.sineminer.data.model.PoolConfig
import com.secretarrow.sineminer.data.repo.SettingsRepository
import com.secretarrow.sineminer.service.MiningForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MiningUiState(
    val isRunning: Boolean = false,
    val hashrateHps: Double = 0.0,
    val totalHashes: Long = 0L,
    val sharesAccepted: Int = 0,
    val sharesRejected: Int = 0,
    val elapsedMs: Long = 0L,
    val poolHost: String? = null,
    val poolPort: Int? = null,
    val poolConnected: Boolean = false,
    val currentJobId: String? = null,
    val currentHeight: Long = 0L,
    val currentDifficulty: Double = 0.0,
    val lastError: String? = null,
    val memoryPreset: MemoryPreset = MemoryPreset.DEFAULT,
    val threads: Int = 1,
)

@HiltViewModel
class MiningViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _liveStatus = MutableStateFlow(MiningStatus(
        isRunning = false, hashrateHps = 0.0, totalHashes = 0L,
        sharesAccepted = 0, sharesRejected = 0, elapsedMs = 0L,
        poolConnected = false, poolHost = null, currentJobId = null,
        currentHeight = 0L, currentDifficulty = 0.0, lastError = null,
    ))
    val liveStatus: StateFlow<MiningStatus> = _liveStatus.asStateFlow()

    val uiState: StateFlow<MiningUiState> = combine(
        settingsRepo.miningSettings,
        settingsRepo.poolConfig,
        _liveStatus,
    ) { settings: MiningSettings, pool: PoolConfig, status: MiningStatus ->
        MiningUiState(
            isRunning = status.isRunning,
            hashrateHps = status.hashrateHps,
            totalHashes = status.totalHashes,
            sharesAccepted = status.sharesAccepted,
            sharesRejected = status.sharesRejected,
            elapsedMs = status.elapsedMs,
            poolHost = pool.host,
            poolPort = pool.port,
            poolConnected = status.poolConnected,
            currentJobId = status.currentJobId,
            currentHeight = status.currentHeight,
            currentDifficulty = status.currentDifficulty,
            lastError = status.lastError,
            memoryPreset = settings.memoryPreset,
            threads = settings.threads,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MiningUiState(),
    )

    fun startMining() {
        val current = uiState.value
        val intent = Intent(context, MiningForegroundService::class.java).apply {
            action = MiningForegroundService.ACTION_START
            putExtra(MiningForegroundService.EXTRA_MEMORY_KIB, current.memoryPreset.memoryKib)
            putExtra(MiningForegroundService.EXTRA_THREADS, current.threads)
            putExtra(MiningForegroundService.EXTRA_POOL_HOST, current.poolHost)
            putExtra(MiningForegroundService.EXTRA_POOL_PORT, current.poolPort ?: 3333)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            _liveStatus.value = _liveStatus.value.copy(lastError = e.message ?: "Failed to start mining")
        }
    }

    fun stopMining() {
        val intent = Intent(context, MiningForegroundService::class.java).apply {
            action = MiningForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateLiveStatus(status: MiningStatus) {
        _liveStatus.value = status
    }
}
