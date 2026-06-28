package com.mandala.net.database

import kotlinx.coroutines.flow.Flow

class BlockedAttemptRepository(private val dao: BlockedAttemptDao) {
    val recentAttempts: Flow<List<BlockedAttempt>> = dao.getRecentAttempts()

    suspend fun insertAttempt(packageName: String, appName: String) {
        val attempt = BlockedAttempt(
            packageName = packageName,
            appName = appName,
            timestamp = System.currentTimeMillis()
        )
        dao.insertAttempt(attempt)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
