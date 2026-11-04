package com.secretarrow.sineminer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.secretarrow.sineminer.data.local.dao.LogEntryDao
import com.secretarrow.sineminer.data.local.dao.MiningSessionDao
import com.secretarrow.sineminer.data.local.dao.WalletDao

@Database(
    entities = [
        EntityWallet::class,
        EntityMiningSession::class,
        EntityLogEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SineMinerDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun miningSessionDao(): MiningSessionDao
    abstract fun logEntryDao(): LogEntryDao

    companion object {
        const val DATABASE_NAME = "sineminer.db"
    }
}
