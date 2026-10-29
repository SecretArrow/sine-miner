package com.secretarrow.sineminer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wallets", indices = [Index(value = ["address"], unique = true)])
data class EntityWallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val address: String,
    val networkId: String,
    val encryptedBlob: String,
    val createdAt: Long,
    val isPrimary: Boolean = false,
)

@Entity(tableName = "mining_sessions")
data class EntityMiningSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "log_entries")
data class EntityLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val source: String,
    val message: String,
)
