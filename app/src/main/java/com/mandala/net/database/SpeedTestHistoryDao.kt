package com.mandala.net.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestHistoryDao {
    @Query("SELECT * FROM speed_test_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SpeedTestHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: SpeedTestHistory)

    @Query("DELETE FROM speed_test_history")
    suspend fun clearAll()
}
