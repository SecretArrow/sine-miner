package com.secretarrow.sineminer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secretarrow.sineminer.data.local.EntityWallet
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntityWallet>>

    @Query("SELECT * FROM wallets WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): EntityWallet?

    @Query("SELECT * FROM wallets WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): EntityWallet?

    @Query("SELECT * FROM wallets WHERE isPrimary = 1 LIMIT 1")
    fun observePrimary(): Flow<EntityWallet?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: EntityWallet): Long

    @Delete
    suspend fun delete(wallet: EntityWallet)

    @Query("UPDATE wallets SET isPrimary = (id = :id)")
    suspend fun setPrimary(id: Long)

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int
}
