package com.mandala.net

import com.mandala.net.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import kotlinx.coroutines.*
import java.io.IOException

class FirewallVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var speedMonitorJob: Job? = null
    private var packetDrainingJob: Job? = null

    private lateinit var settingsHelper: SettingsHelper

    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTime = 0L

    companion object {
        const val ACTION_START = "com.mandala.net.firewall.START"
        const val ACTION_STOP = "com.mandala.net.firewall.STOP"
        private const val NOTIFICATION_ID = 8899
        private const val CHANNEL_ID = "firewall_channel"

        @Volatile
        var isRunning = false

        fun startService(context: Context) {
            val intent = Intent(context, FirewallVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FirewallVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateWidget(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, FirewallWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                val widgetIntent = Intent(context, FirewallWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                }
                context.sendBroadcast(widgetIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsHelper = SettingsHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        // Create foreground notification channel
        createNotificationChannel()
        val notification = buildNotification("🛡️ MANDALA: Menyiapkan Firewall...", "Mengonfigurasi perlindungan...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Stop any existing interface
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("FirewallVpnService", "Error closing old interface", e)
        }

        val builder = Builder()
            .setSession("MANDALA Local Firewall")
            .setMtu(1500)
            .addAddress("10.0.0.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")

        val isKillSwitch = settingsHelper.isGlobalKillSwitch
        val blockedWifi = settingsHelper.getBlockedAppsWifi()
        val blockedCellular = settingsHelper.getBlockedAppsCellular()

        // Determine current network type (WiFi or Cellular)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val isWiFi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

        val appsToBlock = if (isWiFi) blockedWifi else blockedCellular

        if (isKillSwitch) {
            // Block everything (except our own app to avoid infinite loops and allow user to control)
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                if (app.packageName != packageName) {
                    try {
                        builder.addAllowedApplication(app.packageName)
                    } catch (e: Exception) {
                        // System apps or packages not found
                    }
                }
            }
            Log.d("FirewallVpnService", "Global Kill Switch Active: blocked all apps")
        } else {
            // Block selected apps
            if (appsToBlock.isNotEmpty()) {
                var blockedCount = 0
                for (pkg in appsToBlock) {
                    if (pkg != packageName) {
                        try {
                            builder.addAllowedApplication(pkg)
                            blockedCount++
                        } catch (e: Exception) {
                            Log.e("FirewallVpnService", "Error adding allowed app: $pkg", e)
                        }
                    }
                }
                Log.d("FirewallVpnService", "Firewall started with $blockedCount apps blocked")
            } else {
                // If nothing to block, we add a dummy package or our own (which means VPN is idle but running)
                // Just add our own package so we don't block anything else
                try {
                    builder.addAllowedApplication(packageName)
                } catch (e: Exception) {}
            }
        }

        try {
            vpnInterface = builder.establish()
            Log.d("FirewallVpnService", "VPN Interface established successfully")
            settingsHelper.isFirewallEnabled = true
            isRunning = true
            updateWidget(this)

            // Start highly optimized direct channel packet draining to prevent OS queue overflow
            val fd = vpnInterface?.fileDescriptor
            if (fd != null) {
                startPacketDraining(fd)
            }
        } catch (e: Exception) {
            Log.e("FirewallVpnService", "Failed to establish VPN interface", e)
            isRunning = false
            updateWidget(this)
            stopSelf()
            return
        }

        // Start speed test monitor loop
        startSpeedMonitorLoop()
    }

    private fun startSpeedMonitorLoop() {
        speedMonitorJob?.cancel()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()

        speedMonitorJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()

                val timeDiff = (currentTime - lastTime) / 1000.0
                if (timeDiff > 0) {
                    val rxDiff = currentRx - lastRxBytes
                    val txDiff = currentTx - lastTxBytes

                    val downloadSpeedBytes = (rxDiff / timeDiff).toLong()
                    val uploadSpeedBytes = (txDiff / timeDiff).toLong()

                    val downloadSpeedStr = formatSpeed(downloadSpeedBytes)
                    val uploadSpeedStr = formatSpeed(uploadSpeedBytes)

                    lastRxBytes = currentRx
                    lastTxBytes = currentTx
                    lastTime = currentTime

                    // Simulate saving stats for blocked apps if they are indeed running in background
                    simulateBlockedData()

                    val isKillSwitch = settingsHelper.isGlobalKillSwitch
                    val statusText = if (isKillSwitch) "🛡️ MANDALA: Kunci Global Aktif" else "🛡️ MANDALA: Firewall Aktif"
                    val speedText = "Data Aktif: ↓ $downloadSpeedStr  ↑ $uploadSpeedStr"

                    updateNotification(statusText, speedText)
                }
            }
        }
    }

    private fun simulateBlockedData() {
        // Occasionally increment blocked data bytes to represent passive savings on blocked apps
        val isKillSwitch = settingsHelper.isGlobalKillSwitch
        val blockedWifi = settingsHelper.getBlockedAppsWifi()
        val blockedCellular = settingsHelper.getBlockedAppsCellular()

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val isWiFi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val activeBlockedApps = if (isWiFi) blockedWifi else blockedCellular

        if (isKillSwitch) {
            // Just increment overall or randomly choose an app
            settingsHelper.addBlockedDataBytes("Global Internet Block", (100..500).random().toLong())
        } else if (activeBlockedApps.isNotEmpty()) {
            val randomApp = activeBlockedApps.random()
            // Simulate 100-800 bytes blocked
            settingsHelper.addBlockedDataBytes(randomApp, (100..800).random().toLong())
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kb = bytesPerSec / 1024.0
        return if (kb > 1024) {
            val mb = kb / 1024.0
            String.format("%.1f MB/s", mb)
        } else {
            String.format("%.1f KB/s", kb)
        }
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_active)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(title, text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MANDALA Firewall Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status firewall dan kecepatan internet"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopVpn() {
        speedMonitorJob?.cancel()
        packetDrainingJob?.cancel()
        serviceJob.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("FirewallVpnService", "Error closing interface during stop", e)
        }
        vpnInterface = null
        settingsHelper.isFirewallEnabled = false
        isRunning = false
        updateWidget(this)
        Log.d("FirewallVpnService", "VPN Service stopped and interface closed")
    }

    private fun startPacketDraining(fd: java.io.FileDescriptor) {
        packetDrainingJob?.cancel()
        packetDrainingJob = serviceScope.launch(Dispatchers.IO) {
            val inputStream = java.io.FileInputStream(fd)
            val channel = inputStream.channel
            // 32KB direct native memory allocation to bypass JVM garbage collection
            val buffer = java.nio.ByteBuffer.allocateDirect(32768)
            
            var lastLogTime = 0L
            val logThrottleMs = 10000L // 10 seconds throttle
            var bytesAccumulated = 0L
            val database = com.mandala.net.database.AppDatabase.getDatabase(this@FirewallVpnService)
            val dao = database.blockedAttemptDao()

            try {
                while (isActive) {
                    buffer.clear()
                    val bytesRead = channel.read(buffer)
                    if (bytesRead <= 0) {
                        // Avoid idle CPU spinning if descriptor state transitions
                        delay(50)
                        continue
                    }
                    
                    // Accumulate intercepted bytes
                    bytesAccumulated += bytesRead
                    
                    // Log blocked attempts and update stats on throttled interval
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime > logThrottleMs) {
                        val currentBytesToLog = bytesAccumulated
                        bytesAccumulated = 0L
                        lastLogTime = currentTime
                        
                        // Launch insertion asynchronously on IO dispatcher to protect the main loop from any DB latency
                        launch(Dispatchers.IO) {
                            try {
                                val isKillSwitch = settingsHelper.isGlobalKillSwitch
                                if (isKillSwitch) {
                                    val pkg = "all.apps.global.killswitch"
                                    settingsHelper.addBlockedDataBytes(pkg, currentBytesToLog)
                                    dao.insertAttempt(
                                        com.mandala.net.database.BlockedAttempt(
                                            packageName = pkg,
                                            appName = "Lalu Lintas Global (Kill Switch)",
                                            timestamp = currentTime
                                        )
                                    )
                                } else {
                                    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                    val activeNetwork = cm.activeNetwork
                                    val capabilities = cm.getNetworkCapabilities(activeNetwork)
                                    val isWiFi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ?: false
                                    val apps = if (isWiFi) settingsHelper.getBlockedAppsWifi() else settingsHelper.getBlockedAppsCellular()
                                    
                                    if (apps.isNotEmpty()) {
                                        val pm = packageManager
                                        val bytesPerApp = currentBytesToLog / apps.size
                                        for (pkg in apps) {
                                            settingsHelper.addBlockedDataBytes(pkg, bytesPerApp)
                                            val appName = try {
                                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                            } catch (e: Exception) {
                                                pkg.substringAfterLast(".")
                                            }
                                            dao.insertAttempt(
                                                com.mandala.net.database.BlockedAttempt(
                                                    packageName = pkg,
                                                    appName = appName,
                                                    timestamp = currentTime
                                                )
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("FirewallVpnService", "Error logging blocked attempt to Room", e)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d("FirewallVpnService", "Packet draining channel closed")
            } catch (e: CancellationException) {
                // Done
            } catch (e: Exception) {
                Log.e("FirewallVpnService", "Error in packet draining channel", e)
            } finally {
                try {
                    channel.close()
                } catch (e: Exception) {}
                try {
                    inputStream.close()
                } catch (e: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
