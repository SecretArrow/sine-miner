package com.secretarrow.sineminer.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.secretarrow.sineminer.data.model.MemoryPreset
import com.secretarrow.sineminer.data.model.MiningSettings
import com.secretarrow.sineminer.data.model.NotificationSettings
import com.secretarrow.sineminer.data.model.PoolConfig
import com.secretarrow.sineminer.data.model.ThemeMode
import com.secretarrow.sineminer.data.model.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sineminer_settings")

/**
 * Settings persisted via Jetpack DataStore.
 *
 * Wallet keystore blobs are NOT stored here — they go in the Room database.
 */
class SettingsDataStore(private val context: Context) {

    private val KEY_POOL_HOST = stringPreferencesKey("pool_host")
    private val KEY_POOL_PORT = intPreferencesKey("pool_port")
    private val KEY_POOL_WORKER = stringPreferencesKey("pool_worker")
    private val KEY_POOL_PASSWORD = stringPreferencesKey("pool_password")

    private val KEY_MEMORY_PRESET = stringPreferencesKey("memory_preset")
    private val KEY_THREADS = intPreferencesKey("threads")
    private val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_charge")
    private val KEY_STOP_LOW_BATT = booleanPreferencesKey("stop_on_low_battery")
    private val KEY_BATT_THRESHOLD = intPreferencesKey("battery_threshold_percent")

    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

    private val KEY_NOTIF_MINING = booleanPreferencesKey("notif_mining")
    private val KEY_NOTIF_SHARES = booleanPreferencesKey("notif_shares")
    private val KEY_NOTIF_BLOCKS = booleanPreferencesKey("notif_blocks")
    private val KEY_NOTIF_ERRORS = booleanPreferencesKey("notif_errors")

    val poolConfig: Flow<PoolConfig> = context.settingsDataStore.data.map { p ->
        PoolConfig(
            host = p[KEY_POOL_HOST] ?: PoolConfig.DEFAULT.host,
            port = p[KEY_POOL_PORT] ?: PoolConfig.DEFAULT.port,
            worker = p[KEY_POOL_WORKER] ?: PoolConfig.DEFAULT.worker,
            password = p[KEY_POOL_PASSWORD] ?: PoolConfig.DEFAULT.password,
        )
    }

    suspend fun setPoolConfig(cfg: PoolConfig) {
        context.settingsDataStore.edit { p ->
            p[KEY_POOL_HOST] = cfg.host
            p[KEY_POOL_PORT] = cfg.port
            p[KEY_POOL_WORKER] = cfg.worker
            p[KEY_POOL_PASSWORD] = cfg.password
        }
    }

    val miningSettings: Flow<MiningSettings> = context.settingsDataStore.data.map { p ->
        MiningSettings(
            memoryPreset = runCatching { MemoryPreset.valueOf(p[KEY_MEMORY_PRESET] ?: "LOW") }.getOrDefault(MemoryPreset.DEFAULT),
            threads = p[KEY_THREADS] ?: 1,
            autoStartOnCharge = p[KEY_AUTO_START] ?: false,
            stopOnLowBattery = p[KEY_STOP_LOW_BATT] ?: true,
            batteryThresholdPercent = p[KEY_BATT_THRESHOLD] ?: 20,
        )
    }

    suspend fun setMiningSettings(cfg: MiningSettings) {
        context.settingsDataStore.edit { p ->
            p[KEY_MEMORY_PRESET] = cfg.memoryPreset.name
            p[KEY_THREADS] = cfg.threads
            p[KEY_AUTO_START] = cfg.autoStartOnCharge
            p[KEY_STOP_LOW_BATT] = cfg.stopOnLowBattery
            p[KEY_BATT_THRESHOLD] = cfg.batteryThresholdPercent
        }
    }

    val themeSettings: Flow<ThemeSettings> = context.settingsDataStore.data.map { p ->
        ThemeSettings(
            mode = runCatching { ThemeMode.valueOf(p[KEY_THEME_MODE] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            useDynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun setThemeSettings(cfg: ThemeSettings) {
        context.settingsDataStore.edit { p ->
            p[KEY_THEME_MODE] = cfg.mode.name
            p[KEY_DYNAMIC_COLOR] = cfg.useDynamicColor
        }
    }

    val notificationSettings: Flow<NotificationSettings> = context.settingsDataStore.data.map { p ->
        NotificationSettings(
            mining = p[KEY_NOTIF_MINING] ?: true,
            shares = p[KEY_NOTIF_SHARES] ?: true,
            blocks = p[KEY_NOTIF_BLOCKS] ?: true,
            errors = p[KEY_NOTIF_ERRORS] ?: true,
        )
    }

    suspend fun setNotificationSettings(cfg: NotificationSettings) {
        context.settingsDataStore.edit { p ->
            p[KEY_NOTIF_MINING] = cfg.mining
            p[KEY_NOTIF_SHARES] = cfg.shares
            p[KEY_NOTIF_BLOCKS] = cfg.blocks
            p[KEY_NOTIF_ERRORS] = cfg.errors
        }
    }
}
