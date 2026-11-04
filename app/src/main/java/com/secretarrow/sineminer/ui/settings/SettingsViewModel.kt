package com.secretarrow.sineminer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretarrow.sineminer.data.model.MemoryPreset
import com.secretarrow.sineminer.data.model.MiningSettings
import com.secretarrow.sineminer.data.model.NotificationSettings
import com.secretarrow.sineminer.data.model.ThemeMode
import com.secretarrow.sineminer.data.model.ThemeSettings
import com.secretarrow.sineminer.data.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val memoryPreset: MemoryPreset = MemoryPreset.DEFAULT,
    val threads: Int = 1,
    val autoStartOnCharge: Boolean = false,
    val stopOnLowBattery: Boolean = true,
    val batteryThresholdPercent: Int = 20,
    val notifMining: Boolean = true,
    val notifShares: Boolean = true,
    val notifBlocks: Boolean = true,
    val notifErrors: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepo.themeSettings,
        settingsRepo.miningSettings,
        settingsRepo.notificationSettings,
    ) { theme: ThemeSettings, mining: MiningSettings, notif: NotificationSettings ->
        SettingsUiState(
            themeMode = theme.mode,
            useDynamicColor = theme.useDynamicColor,
            memoryPreset = mining.memoryPreset,
            threads = mining.threads,
            autoStartOnCharge = mining.autoStartOnCharge,
            stopOnLowBattery = mining.stopOnLowBattery,
            batteryThresholdPercent = mining.batteryThresholdPercent,
            notifMining = notif.mining,
            notifShares = notif.shares,
            notifBlocks = notif.blocks,
            notifErrors = notif.errors,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setThemeSettings(ThemeSettings(mode = mode, useDynamicColor = current.useDynamicColor))
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setThemeSettings(ThemeSettings(mode = current.themeMode, useDynamicColor = enabled))
        }
    }

    fun setMemoryPreset(preset: MemoryPreset) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setMiningSettings(
                MiningSettings(
                    memoryPreset = preset,
                    threads = current.threads,
                    autoStartOnCharge = current.autoStartOnCharge,
                    stopOnLowBattery = current.stopOnLowBattery,
                    batteryThresholdPercent = current.batteryThresholdPercent,
                )
            )
        }
    }

    fun setThreads(n: Int) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setMiningSettings(
                MiningSettings(
                    memoryPreset = current.memoryPreset,
                    threads = n.coerceIn(1, 8),
                    autoStartOnCharge = current.autoStartOnCharge,
                    stopOnLowBattery = current.stopOnLowBattery,
                    batteryThresholdPercent = current.batteryThresholdPercent,
                )
            )
        }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setMiningSettings(
                MiningSettings(
                    memoryPreset = current.memoryPreset,
                    threads = current.threads,
                    autoStartOnCharge = enabled,
                    stopOnLowBattery = current.stopOnLowBattery,
                    batteryThresholdPercent = current.batteryThresholdPercent,
                )
            )
        }
    }

    fun setStopOnLowBattery(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setMiningSettings(
                MiningSettings(
                    memoryPreset = current.memoryPreset,
                    threads = current.threads,
                    autoStartOnCharge = current.autoStartOnCharge,
                    stopOnLowBattery = enabled,
                    batteryThresholdPercent = current.batteryThresholdPercent,
                )
            )
        }
    }

    fun setBatteryThreshold(pct: Int) {
        viewModelScope.launch {
            val current = uiState.value
            settingsRepo.setMiningSettings(
                MiningSettings(
                    memoryPreset = current.memoryPreset,
                    threads = current.threads,
                    autoStartOnCharge = current.autoStartOnCharge,
                    stopOnLowBattery = current.stopOnLowBattery,
                    batteryThresholdPercent = pct.coerceIn(5, 50),
                )
            )
        }
    }

    fun setNotif(mining: Boolean, shares: Boolean, blocks: Boolean, errors: Boolean) {
        viewModelScope.launch {
            settingsRepo.setNotificationSettings(
                NotificationSettings(mining = mining, shares = shares, blocks = blocks, errors = errors)
            )
        }
    }
}
