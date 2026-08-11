@file:Suppress("DEPRECATION")
package com.mandala.net.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockedAttempt::class, 
        SpeedTestHistory::class
    ], 
    version = 6, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedAttemptDao(): BlockedAttemptDao
    abstract fun speedTestHistoryDao(): SpeedTestHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mandala_net_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
