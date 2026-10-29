package com.secretarrow.sineminer.data.model

/**
 * A wallet entry stored in the local database.
 *
 * The mnemonic is never stored — only the encrypted keystore blob. Mnemonic
 * is shown once at creation time and the user is expected to back it up.
 */
data class Wallet(
    val id: Long,
    val label: String,
    val address: String,
    val networkId: String,
    /** Encrypted keystore JSON (AES-256-GCM, PBKDF2-derived key). */
    val encryptedBlob: String,
    val createdAt: Long,
    val isPrimary: Boolean = false,
)

/**
 * Mining session record — one row per start/stop cycle.
 */
data class MiningSession(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val totalHashes: Long,
    val sharesAccepted: Int,
    val sharesRejected: Int,
    val blocksFound: Int,
    val threads: Int,
    val memoryKib: Long,
    val poolHost: String?,
    val poolPort: Int?,
)

/**
 * A single log entry.
 */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Aggregate mining stats (computed across all sessions).
 */
data class MiningStats(
    val totalHashes: Long,
    val sharesAccepted: Int,
    val sharesRejected: Int,
    val blocksFound: Int,
    val uptimeSeconds: Long,
    val averageHashrate: Double,
    val currentHashrate: Double,
)

/**
 * Pool configuration.
 */
data class PoolConfig(
    val host: String,
    val port: Int,
    val worker: String,
    val password: String,
) {
    companion object {
        val DEFAULT = PoolConfig(
            host = "bntpool.com",
            port = 3333,
            worker = "",
            password = "x",
        )
    }
}

/**
 * Mining configuration — what the user picked in Settings.
 */
data class MiningSettings(
    val memoryPreset: MemoryPreset,
    val threads: Int,
    val autoStartOnCharge: Boolean,
    val stopOnLowBattery: Boolean,
    val batteryThresholdPercent: Int,
)

enum class MemoryPreset(val memoryKib: Long, val labelRes: Int) {
    LOW(256 * 1024, com.secretarrow.sineminer.R.string.mining_preset_low),
    MEDIUM(1_024 * 1024, com.secretarrow.sineminer.R.string.mining_preset_medium),
    PRODUCTION(2_097_152, com.secretarrow.sineminer.R.string.mining_preset_production);

    companion object {
        val DEFAULT = LOW
    }
}

/**
 * UI theme preference.
 */
data class ThemeSettings(
    val mode: ThemeMode,
    val useDynamicColor: Boolean,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Notification settings.
 */
data class NotificationSettings(
    val mining: Boolean,
    val shares: Boolean,
    val blocks: Boolean,
    val errors: Boolean,
)

/**
 * Live mining state — emitted by the MiningForegroundService to UI.
 */
data class MiningStatus(
    val isRunning: Boolean,
    val hashrateHps: Double,
    val totalHashes: Long,
    val sharesAccepted: Int,
    val sharesRejected: Int,
    val elapsedMs: Long,
    val poolConnected: Boolean,
    val poolHost: String?,
    val currentJobId: String?,
    val currentHeight: Long,
    val currentDifficulty: Double,
    val lastError: String?,
)
