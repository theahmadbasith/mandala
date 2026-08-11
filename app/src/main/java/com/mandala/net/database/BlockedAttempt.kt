package com.mandala.net.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_attempts")
data class BlockedAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)
