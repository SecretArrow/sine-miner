package com.secretarrow.sineminer.core.di

import android.content.Context
import androidx.room.Room
import com.secretarrow.sineminer.data.datastore.SettingsDataStore
import com.secretarrow.sineminer.data.local.SineMinerDatabase
import com.secretarrow.sineminer.data.local.dao.LogEntryDao
import com.secretarrow.sineminer.data.local.dao.MiningSessionDao
import com.secretarrow.sineminer.data.local.dao.WalletDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for app-wide singletons (database, DAOs, settings store).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SineMinerDatabase =
        Room.databaseBuilder(context, SineMinerDatabase::class.java, SineMinerDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideWalletDao(db: SineMinerDatabase): WalletDao = db.walletDao()

    @Provides @Singleton
    fun provideMiningSessionDao(db: SineMinerDatabase): MiningSessionDao = db.miningSessionDao()

    @Provides @Singleton
    fun provideLogEntryDao(db: SineMinerDatabase): LogEntryDao = db.logEntryDao()

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)
}
