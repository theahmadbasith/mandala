package com.mandala.net.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test_history")
data class SpeedTestHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverName: String,
    val serverLocation: String,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Long,
    val jitterMs: Long,
    val packetLossPercent: Float,
    val timestamp: Long = System.currentTimeMillis()
)
