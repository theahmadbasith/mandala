package com.mandala.net.database

import kotlinx.coroutines.flow.Flow

class SpeedTestHistoryRepository(private val dao: SpeedTestHistoryDao) {
    val allHistory: Flow<List<SpeedTestHistory>> = dao.getAllHistory()

    suspend fun insertHistory(
        serverName: String,
        serverLocation: String,
        downloadMbps: Double,
        uploadMbps: Double,
        pingMs: Long,
        jitterMs: Long,
        packetLossPercent: Float
    ) {
        val history = SpeedTestHistory(
            serverName = serverName,
            serverLocation = serverLocation,
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            pingMs = pingMs,
            jitterMs = jitterMs,
            packetLossPercent = packetLossPercent,
            timestamp = System.currentTimeMillis()
        )
        dao.insertHistory(history)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
