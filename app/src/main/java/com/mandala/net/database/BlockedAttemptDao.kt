package com.mandala.net.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAttemptDao {
    @Query("SELECT * FROM blocked_attempts ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAttempts(): Flow<List<BlockedAttempt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: BlockedAttempt)

    @Query("DELETE FROM blocked_attempts")
    suspend fun clearAll()
}
