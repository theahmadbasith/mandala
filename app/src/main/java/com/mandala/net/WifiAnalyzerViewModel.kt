@file:Suppress("DEPRECATION")
package com.mandala.net

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mandala.net.database.AppDatabase
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

data class WifiApInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: String, // "2.4 GHz", "5 GHz", "6 GHz"
    val capabilities: String,
    val distance: Double,
    val vendor: String,
    val isConnected: Boolean
)

data class ChannelRating(
    val channel: Int,
    val rating: Float, // 0.0 to 5.0 stars
    val apCount: Int
)

data class LanDevice(
    val ipAddress: String,
    val hostname: String,
    val macAddress: String,
    val vendor: String,
    val isLocalDevice: Boolean = false,
    val isGateway: Boolean = false,
    val deviceType: String = "Generic"
)

class WifiAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val _scanIntervalMs = MutableStateFlow(3000L)
        val scanIntervalMs: StateFlow<Long> = _scanIntervalMs.asStateFlow()

        fun setScanInterval(intervalMs: Long) {
            _scanIntervalMs.value = intervalMs
        }
    }

    private val wifiManager = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val database = AppDatabase.getDatabase(application)

    private val prefs = application.getSharedPreferences("wifi_analyzer_prefs", Context.MODE_PRIVATE)

    private val _excellentThreshold = MutableStateFlow(prefs.getInt("excellent_threshold", -50))
    val excellentThreshold: StateFlow<Int> = _excellentThreshold.asStateFlow()

    private val _poorThreshold = MutableStateFlow(prefs.getInt("poor_threshold", -70))
    val poorThreshold: StateFlow<Int> = _poorThreshold.asStateFlow()

    fun updateThresholds(excellent: Int, poor: Int) {
        _excellentThreshold.value = excellent
        _poorThreshold.value = poor
        prefs.edit()
            .putInt("excellent_threshold", excellent)
            .putInt("poor_threshold", poor)
            .apply()
    }

    private val _scanResults = MutableStateFlow<List<WifiApInfo>>(emptyList())
    val scanResults: StateFlow<List<WifiApInfo>> = _scanResults.asStateFlow()

    private val _connectedWifiDetails = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedWifiDetails: StateFlow<Map<String, String>> = _connectedWifiDetails.asStateFlow()

    private val _channelRatings24 = MutableStateFlow<List<ChannelRating>>(emptyList())
    val channelRatings24: StateFlow<List<ChannelRating>> = _channelRatings24.asStateFlow()

    private val _channelRatings50 = MutableStateFlow<List<ChannelRating>>(emptyList())
    val channelRatings50: StateFlow<List<ChannelRating>> = _channelRatings50.asStateFlow()

    // Signal History Map: BSSID -> List of Pair(TimeMillis, RSSI)
    private val _signalHistory = MutableStateFlow<Map<String, List<Pair<Long, Int>>>>(emptyMap())
    val signalHistory: StateFlow<Map<String, List<Pair<Long, Int>>>> = _signalHistory.asStateFlow()

    private val _lanDevices = MutableStateFlow<List<LanDevice>>(emptyList())
    val lanDevices: StateFlow<List<LanDevice>> = _lanDevices.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    private val _lanScanProgress = MutableStateFlow(0f)
    val lanScanProgress: StateFlow<Float> = _lanScanProgress.asStateFlow()

    private val _subTab = MutableStateFlow(0)
    val subTab: StateFlow<Int> = _subTab.asStateFlow()

    fun setSubTab(index: Int) {
        _subTab.value = index
    }

    private var scanJob: Job? = null
    private var isReceiverRegistered = false

    // Keep track of the top APs to display on the Time Graph to prevent clutter
    private val _activeHistoryBssids = MutableStateFlow<Set<String>>(emptySet())
    val activeHistoryBssids: StateFlow<Set<String>> = _activeHistoryBssids.asStateFlow()

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            Log.d("WifiAnalyzerVM", "Scan results broadcast received. Success: $success")
            processScanResults()
        }
    }

    init {
        registerReceiver()
        startScanning()
        updateConnectedWifiDetails()
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                getApplication<Application>().registerReceiver(wifiScanReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("WifiAnalyzerVM", "Failed to register scan receiver", e)
            }
        }
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                getApplication<Application>().unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.e("WifiAnalyzerVM", "Failed to unregister scan receiver", e)
            }
        }
    }

    fun triggerManualScan() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                wifiManager.startScan()
            } catch (e: Exception) {
                Log.e("WifiAnalyzerVM", "Failed manual startScan", e)
            }
            processScanResults()
        }
    }

    private fun startScanning() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // Try triggering a scan, but don't fail if throttled
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                } catch (e: Exception) {
                    Log.e("WifiAnalyzerVM", "Throttled startScan", e)
                }
                
                // Process current results anyway (since cache works too)
                processScanResults()
                updateConnectedWifiDetails()
                
                delay(scanIntervalMs.value) // Dynamic scan refresh interval
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResults() {
        try {
            val results = wifiManager.scanResults ?: emptyList()
            val currentConnection = wifiManager.connectionInfo
            val connectedBssid = currentConnection?.bssid

            val apList = results.map { result ->
                val ssid = if (result.SSID.isNullOrEmpty()) "<Hidden SSID>" else result.SSID
                val bssid = result.BSSID ?: "00:00:00:00:00:00"
                val rssi = result.level
                val freq = result.frequency
                val channel = getChannelFromFrequency(freq)
                val band = getBandFromFrequency(freq)
                val capabilities = result.capabilities ?: "None"
                val distance = calculateDistance(rssi, freq)
                val vendor = lookupVendor(bssid)
                val isConnected = bssid.equals(connectedBssid, ignoreCase = true)

                WifiApInfo(
                    ssid = ssid,
                    bssid = bssid,
                    rssi = rssi,
                    frequency = freq,
                    channel = channel,
                    band = band,
                    capabilities = capabilities,
                    distance = distance,
                    vendor = vendor,
                    isConnected = isConnected
                )
            }.sortedByDescending { it.rssi }

            _scanResults.value = apList

            // Calculate Channel Ratings
            calculateChannelRatings(apList)

            // Update Signal History
            updateSignalHistory(apList)

        } catch (e: Exception) {
            Log.e("WifiAnalyzerVM", "Error processing scan results", e)
        }
    }

    private fun updateSignalHistory(apList: List<WifiApInfo>) {
        val now = System.currentTimeMillis()
        val currentHistory = _signalHistory.value.toMutableMap()

        // Keep at most 5 strongest/favorite networks to draw to avoid messy overlap
        val topBssids = apList.take(5).map { it.bssid }.toSet()
        _activeHistoryBssids.value = topBssids

        // Initialize history lists for top APs if they don't exist
        for (bssid in topBssids) {
            if (!currentHistory.containsKey(bssid)) {
                currentHistory[bssid] = emptyList()
            }
        }

        // Add current rssi points
        val bssidToRssi = apList.associate { it.bssid to it.rssi }
        for ((bssid, historyList) in currentHistory) {
            val currentRssi = bssidToRssi[bssid] ?: -100
            val updatedList = historyList.toMutableList().apply {
                add(Pair(now, currentRssi))
                // Keep only the last 30 data points (approx 90 seconds of history)
                if (size > 30) {
                    removeAt(0)
                }
            }
            currentHistory[bssid] = updatedList
        }

        _signalHistory.value = currentHistory
    }

    private fun calculateChannelRatings(apList: List<WifiApInfo>) {
        // 2.4 GHz channels: 1 to 14
        val ratings24 = (1..14).map { chan ->
            val overlappingAps = apList.filter { it.band == "2.4 GHz" }
            var score = 5.0f
            var count = 0

            for (ap in overlappingAps) {
                val dist = kotlin.math.abs(ap.channel - chan)
                if (dist < 5) {
                    count++
                    val overlapWeight = 1.0f - (dist / 5.0f)
                    // Stronger signals cause larger penalties
                    val signalWeight = (ap.rssi + 100).coerceIn(0, 70) / 70.0f
                    val penalty = overlapWeight * signalWeight * 1.5f
                    score -= penalty
                }
            }
            ChannelRating(chan, score.coerceIn(0.5f, 5.0f), count)
        }
        _channelRatings24.value = ratings24

        // 5 GHz channels (commonly used ones)
        val channels50 = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
        val ratings50 = channels50.map { chan ->
            val overlappingAps = apList.filter { it.band == "5 GHz" }
            var score = 5.0f
            var count = 0

            for (ap in overlappingAps) {
                val dist = kotlin.math.abs(ap.channel - chan)
                if (dist == 0) {
                    count++
                    val signalWeight = (ap.rssi + 100).coerceIn(0, 70) / 70.0f
                    score -= (signalWeight * 2.0f)
                } else if (dist <= 4) { // channel bonding overlaps (e.g. 40MHz, 80MHz)
                    count++
                    val signalWeight = (ap.rssi + 100).coerceIn(0, 70) / 70.0f
                    score -= (signalWeight * 0.5f)
                }
            }
            ChannelRating(chan, score.coerceIn(1.0f, 5.0f), count)
        }
        _channelRatings50.value = ratings50
    }

    private fun updateConnectedWifiDetails() {
        try {
            val connectionInfo = wifiManager.connectionInfo
            if (connectionInfo == null || connectionInfo.bssid == null || connectionInfo.rssi == -127) {
                _connectedWifiDetails.value = emptyMap()
                return
            }

            val details = mutableMapOf<String, String>()
            details["SSID"] = if (connectionInfo.ssid.isNullOrEmpty() || connectionInfo.ssid == WifiManager.UNKNOWN_SSID) "Disconnected" else connectionInfo.ssid.replace("\"", "")
            details["BSSID"] = connectionInfo.bssid ?: "00:00:00:00:00:00"
            details["RSSI"] = "${connectionInfo.rssi} dBm"
            details["Kecepatan Link (Rx/Tx)"] = "${connectionInfo.linkSpeed} Mbps"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                details["Tx Link Speed"] = "${connectionInfo.txLinkSpeedMbps} Mbps"
                details["Rx Link Speed"] = "${connectionInfo.rxLinkSpeedMbps} Mbps"
            }
            details["Frekuensi"] = "${connectionInfo.frequency} MHz"
            details["Channel"] = getChannelFromFrequency(connectionInfo.frequency).toString()
            details["Band"] = getBandFromFrequency(connectionInfo.frequency)

            // DHCP and IP info
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo != null) {
                details["IP Address"] = formatIpAddress(dhcpInfo.ipAddress)
                details["Subnet Mask"] = formatIpAddress(dhcpInfo.netmask)
                details["Gateway"] = formatIpAddress(dhcpInfo.gateway)
                details["DNS 1"] = formatIpAddress(dhcpInfo.dns1)
                details["DNS 2"] = formatIpAddress(dhcpInfo.dns2)
                details["DHCP Server"] = formatIpAddress(dhcpInfo.serverAddress)
                details["Lease Duration"] = "${dhcpInfo.leaseDuration} s"
            }

            // Get network interfaces for MAC addresses
            val mac = getMacAddress()
            if (mac != "Unavailable") {
                details["MAC Address"] = mac
            }

            _connectedWifiDetails.value = details
        } catch (e: Exception) {
            Log.e("WifiAnalyzerVM", "Error updating connected WiFi details", e)
        }
    }

    private fun getMacAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "Unavailable"
            for (networkInterface in Collections.list(interfaces)) {
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val macBytes = networkInterface.hardwareAddress ?: return "Unavailable"
                    val res1 = StringBuilder()
                    for (b in macBytes) {
                        res1.append(String.format("%02X:", b))
                    }
                    if (res1.isNotEmpty()) {
                        res1.deleteCharAt(res1.length - 1)
                    }
                    return res1.toString()
                }
            }
        } catch (ex: Exception) {
            // ignore
        }
        return "Unavailable"
    }

    private fun formatIpAddress(ip: Int): String {
        return if (ip == 0) "0.0.0.0" else String.format(
            Locale.US,
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    private fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            frequency == 2484 -> 14
            frequency in 2412..2472 -> (frequency - 2407) / 5
            frequency in 5160..5885 -> (frequency - 5000) / 5
            frequency in 5945..7105 -> (frequency - 5940) / 5 + 1
            else -> 0
        }
    }

    private fun getBandFromFrequency(frequency: Int): String {
        return when {
            frequency in 2400..2499 -> "2.4 GHz"
            frequency in 5000..5999 -> "5 GHz"
            frequency in 6000..7200 -> "6 GHz"
            else -> "Unknown"
        }
    }

    private fun calculateDistance(rssi: Int, frequency: Int): Double {
        // Free Space Path Loss model distance estimation:
        // FSPL = 20 log10(d) + 20 log10(f) - 27.55
        // log10(d) = (FSPL - 20 log10(f) + 27.55) / 20
        // We use absolute RSSI value as approximate path loss indicator
        val exp = (27.55 - (20 * log10(frequency.toDouble())) + kotlin.math.abs(rssi)) / 20.0
        return 10.0.pow(exp)
    }

    private fun lookupVendor(bssid: String): String {
        if (bssid.length < 8) return "Generic"
        val prefix = bssid.substring(0, 8).uppercase(Locale.US)
        return when (prefix) {
            "00:11:22", "00:00:0C", "00:14:22" -> "Cisco"
            "00:05:5D", "D8:FE:E3" -> "D-Link"
            "E0:D9:E3", "18:D6:C7", "D8:07:B6", "F8:1A:67", "B0:4E:26", "A0:F3:C1", "C4:6E:1F", "24:05:0F", "EC:08:6B", "7C:8B:CA" -> "TP-Link"
            "00:1E:C9", "5C:02:14", "7C:1D:D9", "A4:3D:78", "D8:C4:67", "1C:15:1F", "28:6C:07", "34:80:B3" -> "Xiaomi"
            "00:0C:43" -> "Ralink"
            "00:03:7F" -> "Atheros"
            "00:11:50", "00:1C:DF" -> "Belkin"
            "00:22:3F", "00:26:F2", "00:1B:2F", "9C:C7:A6", "C0:3F:0E" -> "Netgear"
            "1C:7E:E5", "00:18:39", "00:23:69" -> "Linksys"
            "00:17:F2", "00:1C:B3", "00:1D:4F", "00:23:12", "00:25:00", "78:31:C1", "D8:50:E6", "64:16:66", "AC:BC:32", "FC:DB:B3" -> "Apple"
            "00:21:E9", "00:1E:10", "24:DB:AC", "28:5F:DB" -> "Huawei"
            "28:6E:D4", "10:7B:44", "04:D9:F5", "14:DD:A9", "E0:3F:49" -> "Asus"
            "74:D0:2B", "A4:34:D9", "E8:B1:FC", "4C:34:88" -> "Intel"
            "00:15:58", "00:1D:60", "00:22:5F" -> "Foxconn"
            "00:1F:E3", "00:22:64" -> "Hewlett Packard"
            "00:10:18", "00:90:4C", "BC:F2:AF" -> "Broadcom"
            "B8:27:EB", "DC:A6:32", "E4:5F:01" -> "Raspberry Pi"
            "00:1D:AA", "00:27:22", "24:A4:3C", "44:D9:E7", "74:83:C2", "80:2A:A8", "90:6C:AC" -> "Ubiquiti"
            "C8:3A:35", "50:2B:73", "00:B0:C7", "D4:61:FE", "D4:EE:07" -> "Tenda"
            "00:12:47", "00:15:B9", "18:22:7E", "30:07:4D", "34:C3:AC", "70:3E:AC" -> "Samsung"
            "00:13:CF", "00:15:C1", "00:19:C1" -> "Sony"
            "00:1C:62", "00:1E:75" -> "LG Electronics"
            "1C:5A:3E", "20:DF:B9", "3C:5C:C4", "F8:0F:F9" -> "Google"
            "18:FE:34", "24:0A:C4", "30:AE:A4", "54:5A:16", "80:7D:3A", "A0:20:A6", "C8:2B:96" -> "Espressif"
            "00:00:85", "00:1E:8F" -> "Canon"
            "00:00:48", "00:26:AB" -> "Epson"
            else -> "Generic/OUI: $prefix"
        }
    }

    private var lanScanJob: Job? = null

    fun startLanScan() {
        lanScanJob?.cancel()
        _isScanningLan.value = true
        _lanScanProgress.value = 0f
        _lanDevices.value = emptyList()

        lanScanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val myIp = getMyIp()
                val gatewayIp = getGatewayIp()
                val baseIp = getSubnetPrefix()

                if (baseIp == null || myIp == "0.0.0.0") {
                    _lanDevices.value = emptyList()
                    _isScanningLan.value = false
                    return@launch
                }

                val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
                val activeDevices = java.util.Collections.synchronizedList(mutableListOf<LanDevice>())

                val deferreds = (1..254).map { i ->
                    async(Dispatchers.IO) {
                        try {
                            val ip = baseIp + i
                            val isMe = ip == myIp
                            val isGW = ip == gatewayIp

                            if (isMe) {
                                val myMac = getMacAddress()
                                val myVendor = if (myMac != "Unavailable") lookupVendor(myMac) else "Google/OEM"
                                val device = LanDevice(
                                    ipAddress = ip,
                                    hostname = Build.MODEL ?: "Perangkat Ini",
                                    macAddress = myMac,
                                    vendor = myVendor,
                                    isLocalDevice = true,
                                    isGateway = false,
                                    deviceType = "ThisDevice"
                                )
                                activeDevices.add(device)
                            } else {
                                val alive = isIpReachable(ip)
                                if (alive) {
                                    val mac = getMacFromArpCache(ip) ?: "Tidak Tersedia"
                                    val vendor = if (mac != "Tidak Tersedia") lookupVendor(mac) else "Generic Vendor"
                                    
                                    var resolvedName = if (isGW) "Gateway / Router" else "Perangkat Terhubung"
                                    try {
                                        val inet = java.net.InetAddress.getByName(ip)
                                        val host = inet.hostName
                                        if (host != ip) {
                                            resolvedName = host
                                        }
                                    } catch (e: Exception) {}

                                    val lowercaseName = resolvedName.lowercase()
                                    val lowercaseVendor = vendor.lowercase()
                                    val devType = when {
                                        isGW -> "Gateway"
                                        lowercaseName.contains("android") || lowercaseName.contains("phone") || lowercaseName.contains("samsung") || lowercaseName.contains("huawei") || lowercaseName.contains("oppo") || lowercaseName.contains("vivo") || lowercaseName.contains("xiaomi") || lowercaseName.contains("redmi") || lowercaseName.contains("realme") || lowercaseName.contains("oneplus") || lowercaseName.contains("nokia") || lowercaseName.contains("smartphone") ||
                                        lowercaseVendor.contains("samsung") || lowercaseVendor.contains("huawei") || lowercaseVendor.contains("oppo") || lowercaseVendor.contains("vivo") || lowercaseVendor.contains("oneplus") || lowercaseVendor.contains("realme") || lowercaseVendor.contains("xiaomi") || lowercaseVendor.contains("motorola") || lowercaseVendor.contains("hmd") || lowercaseVendor.contains("zte") -> "Phone"
                                        lowercaseName.contains("iphone") || lowercaseName.contains("ipad") || lowercaseName.contains("ios") ||
                                        lowercaseVendor.contains("apple") && (lowercaseName.contains("iphone") || lowercaseName.contains("ipad") || lowercaseName.contains("phone")) -> "Phone"
                                        lowercaseName.contains("pc") || lowercaseName.contains("desktop") || lowercaseName.contains("laptop") || lowercaseName.contains("computer") || lowercaseName.contains("macbook") || lowercaseName.contains("windows") || lowercaseName.contains("notebook") || lowercaseName.contains("lenovo") || lowercaseName.contains("asus") || lowercaseName.contains("hp-") || lowercaseName.contains("dell") || lowercaseName.contains("thinkpad") ||
                                        lowercaseVendor.contains("intel") || lowercaseVendor.contains("dell") || lowercaseVendor.contains("hewlett") || lowercaseVendor.contains("hp") || lowercaseVendor.contains("lenovo") || lowercaseVendor.contains("asus") || lowercaseVendor.contains("acer") || lowercaseVendor.contains("msi") || lowercaseVendor.contains("gigabyte") || (lowercaseVendor.contains("apple") && lowercaseName.contains("mac")) -> "PC"
                                        lowercaseName.contains("tv") || lowercaseName.contains("smarttv") || lowercaseName.contains("chromecast") || lowercaseName.contains("firestick") || lowercaseName.contains("roku") || lowercaseName.contains("shield") || lowercaseName.contains("apple tv") || lowercaseName.contains("tcl") || lowercaseName.contains("toshiba") || lowercaseName.contains("panasonic") || lowercaseName.contains("sony") ||
                                        lowercaseVendor.contains("sony") || lowercaseVendor.contains("lg ") || lowercaseVendor.contains("tcl") || lowercaseVendor.contains("hisense") || lowercaseVendor.contains("roku") -> "TV"
                                        lowercaseName.contains("printer") || lowercaseName.contains("epson") || lowercaseName.contains("hp") || lowercaseName.contains("canon") || lowercaseName.contains("brother") || lowercaseName.contains("laserjet") || lowercaseName.contains("pixma") ||
                                        lowercaseVendor.contains("epson") || lowercaseVendor.contains("canon") || lowercaseVendor.contains("brother") || lowercaseVendor.contains("xerox") || lowercaseVendor.contains("lexmark") || lowercaseVendor.contains("ricoh") -> "Printer"
                                        lowercaseName.contains("esp") || lowercaseName.contains("raspberry") || lowercaseName.contains("smart") || lowercaseName.contains("iot") || lowercaseName.contains("node") || lowercaseName.contains("camera") || lowercaseName.contains("bulb") || lowercaseName.contains("plug") || lowercaseName.contains("sonoff") || lowercaseName.contains("tuya") || lowercaseName.contains("aqara") || lowercaseName.contains("shelly") || lowercaseName.contains("nest") || lowercaseName.contains("alexa") || lowercaseName.contains("echo") ||
                                        lowercaseVendor.contains("raspberry") || lowercaseVendor.contains("espressif") || lowercaseVendor.contains("tuya") || lowercaseVendor.contains("sonoff") || lowercaseVendor.contains("aqara") || lowercaseVendor.contains("shelly") || lowercaseVendor.contains("philips") || lowercaseVendor.contains("ikea") || lowercaseVendor.contains("amazon") || lowercaseVendor.contains("nest") -> "IoT"
                                        lowercaseVendor.contains("tp-link") || lowercaseVendor.contains("d-link") || lowercaseVendor.contains("netgear") || lowercaseVendor.contains("linksys") || lowercaseVendor.contains("cisco") || lowercaseVendor.contains("ubiquiti") || lowercaseVendor.contains("tenda") || lowercaseVendor.contains("mercusys") || lowercaseVendor.contains("zyxel") || lowercaseVendor.contains("mikrotik") || lowercaseVendor.contains("synology") -> "Gateway"
                                        else -> "Generic"
                                    }

                                    val device = LanDevice(
                                        ipAddress = ip,
                                        hostname = resolvedName,
                                        macAddress = mac,
                                        vendor = vendor,
                                        isLocalDevice = false,
                                        isGateway = isGW,
                                        deviceType = devType
                                    )
                                    activeDevices.add(device)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("WifiAnalyzerVM", "Error scanning IP $i", e)
                        } finally {
                            val count = completedCount.incrementAndGet()
                            _lanScanProgress.value = count / 254f
                        }
                    }
                }

                deferreds.awaitAll()
                
                // Sort results: gateway first, then local device, then others by IP octet
                val sorted = activeDevices.sortedWith(
                    compareByDescending<LanDevice> { it.isGateway }
                        .thenByDescending { it.isLocalDevice }
                        .thenBy { 
                            val parts = it.ipAddress.split(".")
                            if (parts.size == 4) parts[3].toIntOrNull() ?: 0 else 0
                        }
                )
                _lanDevices.value = sorted
            } catch (e: CancellationException) {
                // Ignore
            } finally {
                _isScanningLan.value = false
            }
        }
    }

    private fun isIpReachable(ip: String): Boolean {
        try {
            val address = java.net.InetAddress.getByName(ip)
            if (address.isReachable(200)) return true
        } catch (e: Exception) {}

        // Fallback checks on standard ports
        val commonPorts = listOf(80, 443, 22, 135, 445, 8080)
        for (port in commonPorts) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(ip, port), 80)
                    return true
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return false
    }

    private fun getMacFromArpCache(ipAddress: String): String? {
        try {
            val file = java.io.File("/proc/net/arp")
            if (file.exists() && file.canRead()) {
                file.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 4 && parts[0] == ipAddress) {
                            val mac = parts[3]
                            if (mac != "00:00:00:00:00:00" && mac.matches("..:..:..:..:..:..".toRegex())) {
                                return mac
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun getMyIp(): String {
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) formatIpAddress(dhcpInfo.ipAddress) else "0.0.0.0"
    }

    private fun getGatewayIp(): String {
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) formatIpAddress(dhcpInfo.gateway) else "0.0.0.0"
    }

    private fun getSubnetPrefix(): String? {
        val ip = getMyIp()
        if (ip == "0.0.0.0" || ip.isEmpty()) return null
        val lastDot = ip.lastIndexOf('.')
        if (lastDot == -1) return null
        return ip.substring(0, lastDot + 1)
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiver()
        scanJob?.cancel()
        lanScanJob?.cancel()
    }
}
