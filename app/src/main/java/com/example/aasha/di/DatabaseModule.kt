package com.example.aasha.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aasha.data.local.AppDatabase
import com.example.aasha.data.local.MachineIssueDao
import com.example.aasha.data.local.SlotBookingDao
import com.example.aasha.data.local.WashingMachineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<AppDatabase>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "laundryslot_db"
        )
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            databaseProvider.get().washingMachineDao().insertMachines(
                                AppDatabase.createSeedMachines()
                            )
                        } catch (e: Exception) {
                            Log.w("DatabaseModule", "Seed callback skipped: ${e.message}")
                        }
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideWashingMachineDao(db: AppDatabase): WashingMachineDao = db.washingMachineDao()

    @Provides
    fun provideSlotBookingDao(db: AppDatabase): SlotBookingDao = db.slotBookingDao()

    @Provides
    fun provideMachineIssueDao(db: AppDatabase): MachineIssueDao = db.machineIssueDao()
}
