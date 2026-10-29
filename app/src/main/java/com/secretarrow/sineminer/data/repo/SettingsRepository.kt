package com.secretarrow.sineminer.data.repo

import com.secretarrow.sineminer.data.datastore.SettingsDataStore
import com.secretarrow.sineminer.data.model.MiningSettings
import com.secretarrow.sineminer.data.model.PoolConfig
import com.secretarrow.sineminer.data.model.NotificationSettings
import com.secretarrow.sineminer.data.model.ThemeSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings repository — thin wrapper over [SettingsDataStore] that we can mock
 * in unit tests.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore,
) {
    val poolConfig get() = store.poolConfig
    val miningSettings get() = store.miningSettings
    val themeSettings get() = store.themeSettings
    val notificationSettings get() = store.notificationSettings

    suspend fun setPoolConfig(cfg: PoolConfig) = store.setPoolConfig(cfg)
    suspend fun setMiningSettings(cfg: MiningSettings) = store.setMiningSettings(cfg)
    suspend fun setThemeSettings(cfg: ThemeSettings) = store.setThemeSettings(cfg)
    suspend fun setNotificationSettings(cfg: NotificationSettings) = store.setNotificationSettings(cfg)
}
