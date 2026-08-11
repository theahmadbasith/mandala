package com.mandala.net

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class BlockedCall(
    val id: String,
    val phoneNumber: String,
    val timestamp: Long,
    val rxtype: String // e.g., "SILENT_REJECT"
)

class SettingsHelper(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("net_mode_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CALL_BLOCKING_ENABLED = "call_blocking_enabled"
        private const val KEY_BLOCK_MODE = "block_mode" // 0 = Block All, 1 = Block Private/Unknown
        private const val KEY_BLOCKED_LOGS = "blocked_logs"
    }

    var isCallBlockingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALL_BLOCKING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CALL_BLOCKING_ENABLED, value).apply()

    var blockMode: Int
        get() = prefs.getInt(KEY_BLOCK_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_BLOCK_MODE, value).apply()

    fun getBlockedCalls(): List<BlockedCall> {
        val jsonStr = prefs.getString(KEY_BLOCKED_LOGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<BlockedCall>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BlockedCall(
                        id = obj.optString("id", i.toString()),
                        phoneNumber = obj.optString("phoneNumber", "Unknown"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        rxtype = obj.optString("rxtype", "SILENT_REJECT")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addBlockedCall(phoneNumber: String) {
        val currentLogs = getBlockedCalls().toMutableList()
        val newCall = BlockedCall(
            id = System.nanoTime().toString(),
            phoneNumber = phoneNumber.ifBlank { "Private Number" },
            timestamp = System.currentTimeMillis(),
            rxtype = "SILENT_REJECT"
        )
        currentLogs.add(0, newCall)
        
        // Keep only last 50 logs for memory efficiency
        val trimmed = if (currentLogs.size > 50) currentLogs.take(50) else currentLogs

        val arr = JSONArray()
        trimmed.forEach { call ->
            val obj = JSONObject().apply {
                put("id", call.id)
                put("phoneNumber", call.phoneNumber)
                put("timestamp", call.timestamp)
                put("rxtype", call.rxtype)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_BLOCKED_LOGS, arr.toString()).apply()
    }

    fun clearBlockedCalls() {
        prefs.edit().putString(KEY_BLOCKED_LOGS, "[]").apply()
    }

    // --- Firewall Settings ---
    var isFirewallEnabled: Boolean
        get() = prefs.getBoolean("firewall_enabled", false)
        set(value) = prefs.edit().putBoolean("firewall_enabled", value).apply()

    var isGlobalKillSwitch: Boolean
        get() = prefs.getBoolean("global_kill_switch", false)
        set(value) = prefs.edit().putBoolean("global_kill_switch", value).apply()

    var isAmoledTheme: Boolean
        get() = prefs.getBoolean("amoled_theme", false)
        set(value) = prefs.edit().putBoolean("amoled_theme", value).apply()

    fun getBlockedAppsWifi(): Set<String> {
        return prefs.getStringSet("blocked_apps_wifi", emptySet())?.toSet() ?: emptySet()
    }

    fun setBlockedAppsWifi(apps: Set<String>) {
        prefs.edit().putStringSet("blocked_apps_wifi", apps).apply()
    }

    fun getBlockedAppsCellular(): Set<String> {
        return prefs.getStringSet("blocked_apps_cellular", emptySet())?.toSet() ?: emptySet()
    }

    fun setBlockedAppsCellular(apps: Set<String>) {
        prefs.edit().putStringSet("blocked_apps_cellular", apps).apply()
    }

    // Blocked data stats: map of packageName -> blockedBytes
    fun getBlockedDataStats(): Map<String, Long> {
        val jsonStr = prefs.getString("blocked_data_stats", "{}") ?: "{}"
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getLong(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun addBlockedDataBytes(packageName: String, bytes: Long) {
        val stats = getBlockedDataStats().toMutableMap()
        val current = stats[packageName] ?: 0L
        stats[packageName] = current + bytes
        
        val obj = JSONObject()
        stats.forEach { (key, value) ->
            obj.put(key, value)
        }
        prefs.edit().putString("blocked_data_stats", obj.toString()).apply()
    }

    fun clearBlockedDataStats() {
        prefs.edit().putString("blocked_data_stats", "{}").apply()
    }
}
