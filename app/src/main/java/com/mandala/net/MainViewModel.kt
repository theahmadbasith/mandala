package com.mandala.net

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.mandala.net.database.AppDatabase
import com.mandala.net.database.BlockedAttempt
import com.mandala.net.database.BlockedAttemptRepository
import com.mandala.net.database.SpeedTestHistory
import com.mandala.net.database.SpeedTestHistoryRepository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AppInfo(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val isBlockedWifi: Boolean,
    val isBlockedCellular: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _tabToOpen = MutableSharedFlow<Int>(replay = 1)
    val tabToOpen = _tabToOpen.asSharedFlow()

    private val _openRedialerSubTab = MutableSharedFlow<Boolean>(replay = 1)
    val openRedialerSubTab = _openRedialerSubTab.asSharedFlow()

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _selectedDashboardSection = MutableStateFlow(0)
    val selectedDashboardSection: StateFlow<Int> = _selectedDashboardSection.asStateFlow()

    fun setCurrentTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun setSelectedDashboardSection(section: Int) {
        _selectedDashboardSection.value = section
    }

    fun navigateToTab(tabIndex: Int, openRedialer: Boolean = false) {
        viewModelScope.launch {
            _tabToOpen.emit(tabIndex)
            _openRedialerSubTab.emit(openRedialer)
            _currentTab.value = when (tabIndex) {
                3 -> 4 // Legacy Speed widget (3) -> current Speed tab (4)
                else -> if (tabIndex in 0..6) tabIndex else 0
            }
        }
    }

    private val settingsHelper = SettingsHelper(application)
    private val signalMonitor = SignalStrengthMonitor(application)
    private val speedTestManager = SpeedTestManager()

    private val database = AppDatabase.getDatabase(application)
    private val blockedAttemptRepository = BlockedAttemptRepository(database.blockedAttemptDao())
    private val speedTestHistoryRepository = SpeedTestHistoryRepository(database.speedTestHistoryDao())

    val blockedAttempts: StateFlow<List<BlockedAttempt>> = blockedAttemptRepository.recentAttempts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val speedTestHistory: StateFlow<List<SpeedTestHistory>> = speedTestHistoryRepository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _nearbyServers = MutableStateFlow<List<TestServer>>(emptyList())
    val nearbyServers: StateFlow<List<TestServer>> = _nearbyServers.asStateFlow()

    private val _isScanningServers = MutableStateFlow(false)
    val isScanningServers: StateFlow<Boolean> = _isScanningServers.asStateFlow()

    fun clearBlockedAttempts() {
        viewModelScope.launch(Dispatchers.IO) {
            blockedAttemptRepository.clearAll()
        }
    }

    // 1. Signal Strength Monitoring State
    val signalInfo: StateFlow<SignalInfo> = signalMonitor.signalFlow

    // 2. Call Screening & Blocking State
    private val _isCallBlockingEnabled = MutableStateFlow(settingsHelper.isCallBlockingEnabled)
    val isCallBlockingEnabled: StateFlow<Boolean> = _isCallBlockingEnabled.asStateFlow()

    private val _blockMode = MutableStateFlow(settingsHelper.blockMode)
    val blockMode: StateFlow<Int> = _blockMode.asStateFlow()

    private val _blockedCalls = MutableStateFlow<List<BlockedCall>>(emptyList())
    val blockedCalls: StateFlow<List<BlockedCall>> = _blockedCalls.asStateFlow()

    // 3. Speed Test State
    private val _speedTestState = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)
    val speedTestState: StateFlow<SpeedTestState> = _speedTestState.asStateFlow()

    private val _selectedTestServer = MutableStateFlow(defaultTestServers.first())
    val selectedTestServer: StateFlow<TestServer> = _selectedTestServer.asStateFlow()
    
    fun selectTestServer(server: TestServer) {
        _selectedTestServer.value = server
    }
    private var speedTestJob: Job? = null

    // 4. Firewall & App Blocker State
    private val _isFirewallEnabled = MutableStateFlow(settingsHelper.isFirewallEnabled)
    val isFirewallEnabled: StateFlow<Boolean> = _isFirewallEnabled.asStateFlow()

    private val _isGlobalKillSwitch = MutableStateFlow(settingsHelper.isGlobalKillSwitch)
    val isGlobalKillSwitch: StateFlow<Boolean> = _isGlobalKillSwitch.asStateFlow()

    private val _isAmoledTheme = MutableStateFlow(settingsHelper.isAmoledTheme)
    val isAmoledTheme: StateFlow<Boolean> = _isAmoledTheme.asStateFlow()

    private val _blockedDataStats = MutableStateFlow(settingsHelper.getBlockedDataStats())
    val blockedDataStats: StateFlow<Map<String, Long>> = _blockedDataStats.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _preferredNetworkType = MutableStateFlow("Memuat...")
    val preferredNetworkType: StateFlow<String> = _preferredNetworkType.asStateFlow()

    fun updatePreferredNetworkType() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolver = context.contentResolver
            val modesList = mutableListOf<String>()
            
            val tryGetModeVal: (Int, Int) -> Int = { subId, slotIndex ->
                val slotIndex1Based = slotIndex + 1
                val keys = listOf(
                    "preferred_network_mode$subId",
                    "preferred_network_mode_$subId",
                    "preferred_network_mode$slotIndex",
                    "preferred_network_mode_$slotIndex",
                    "preferred_network_mode$slotIndex1Based",
                    "preferred_network_mode_$slotIndex1Based",
                    "preferred_network_mode",
                    "network_mode$subId",
                    "network_mode_$subId",
                    "network_mode$slotIndex",
                    "network_mode_$slotIndex",
                    "network_mode$slotIndex1Based",
                    "network_mode_$slotIndex1Based",
                    "network_mode"
                )
                var foundVal = -1
                // Try Settings.Global first
                for (key in keys) {
                    try {
                        val v = android.provider.Settings.Global.getInt(resolver, key, -1)
                        if (v != -1) {
                            foundVal = v
                            break
                        }
                    } catch (e: Exception) {}
                }
                // Try Settings.Secure fallback
                if (foundVal == -1) {
                    for (key in keys) {
                        try {
                            val v = android.provider.Settings.Secure.getInt(resolver, key, -1)
                            if (v != -1) {
                                foundVal = v
                                break
                            }
                        } catch (e: Exception) {}
                    }
                }
                foundVal
            }

            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                val hasPhoneStatePermission = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                val activeSubscriptionInfoList = if (hasPhoneStatePermission) {
                    subscriptionManager?.activeSubscriptionInfoList
                } else null

                if (activeSubscriptionInfoList != null && activeSubscriptionInfoList.isNotEmpty()) {
                    for (subInfo in activeSubscriptionInfoList) {
                        val subId = subInfo.subscriptionId
                        val simSlotIndex = subInfo.simSlotIndex
                        val displaySlotIndex = simSlotIndex + 1
                        val carrierName = subInfo.displayName?.toString()?.ifBlank { "SIM $displaySlotIndex" } ?: "SIM $displaySlotIndex"
                        
                        var modeString = ""
                        
                        // Method 1: Query allowedNetworkTypesForReason (Android 13+)
                        if (android.os.Build.VERSION.SDK_INT >= 33 && telephonyManager != null && hasPhoneStatePermission) {
                            try {
                                val subTelephonyManager = telephonyManager.createForSubscriptionId(subId)
                                @Suppress("MissingPermission")
                                val bitmask = subTelephonyManager.getAllowedNetworkTypesForReason(android.telephony.TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
                                modeString = getNetworkModeFromBitmask(bitmask)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        }
                        
                        // Method 2: Fallback to settings provider
                        if (modeString.isEmpty() || modeString == "Tidak Diketahui") {
                            val modeVal = tryGetModeVal(subId, simSlotIndex)
                            modeString = if (modeVal != -1) mapNetworkModeToString(modeVal) else "Tidak Diketahui (Sistem Default)"
                        }
                        
                        modesList.add("• $carrierName (Slot $displaySlotIndex): $modeString")
                    }
                } else {
                    // Fallback for single SIM or missing permission
                    var modeString = ""
                    if (android.os.Build.VERSION.SDK_INT >= 33 && telephonyManager != null && hasPhoneStatePermission) {
                        try {
                            @Suppress("MissingPermission")
                            val bitmask = telephonyManager.getAllowedNetworkTypesForReason(android.telephony.TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER)
                            modeString = getNetworkModeFromBitmask(bitmask)
                        } catch (e: Exception) {}
                    }
                    
                    if (modeString.isEmpty() || modeString == "Tidak Diketahui") {
                        val modeVal = tryGetModeVal(-1, 0)
                        modeString = if (modeVal != -1) mapNetworkModeToString(modeVal) else "Tidak Diketahui (Sistem Default)"
                    }
                    modesList.add("• SIM Bawaan: $modeString")
                }
            } catch (e: Exception) {
                // Settings provider query fallback
                try {
                    val modeVal = tryGetModeVal(-1, 0)
                    val modeString = if (modeVal != -1) mapNetworkModeToString(modeVal) else "Tidak Diketahui (Sistem Default)"
                    modesList.add("• SIM Bawaan: $modeString")
                } catch (ex: Exception) {
                    modesList.add("• Tidak dapat membaca pengaturan preferred network.")
                }
            }
            
            _preferredNetworkType.value = modesList.joinToString("\n")
        }
    }

    private fun getNetworkModeFromBitmask(bitmask: Long): String {
        // Correct standard Android TelephonyManager bitmasks (1 shl (NETWORK_TYPE_X - 1)):
        // NR (20) -> 1 shl 19
        // LTE (13) -> 1 shl 12
        // LTE_CA (19) -> 1 shl 18
        val hasNr = (bitmask and (1L shl 19)) != 0L
        val hasLte = (bitmask and ((1L shl 12) or (1L shl 18))) != 0L
        // 3G includes UMTS (1 shl 2), EVDO_0 (1 shl 4), EVDO_A (1 shl 5), HSDPA (1 shl 7), HSUPA (1 shl 8), HSPA (1 shl 9), EVDO_B (1 shl 11), HSPAP (1 shl 14), TD_SCDMA (1 shl 16)
        val has3g = (bitmask and ((1L shl 2) or (1L shl 4) or (1L shl 5) or (1L shl 7) or (1L shl 8) or (1L shl 9) or (1L shl 11) or (1L shl 14) or (1L shl 16))) != 0L
        // 2G includes GPRS (1 shl 0), EDGE (1 shl 1), CDMA (1 shl 3), 1xRTT (1 shl 6), iDen (1 shl 10), GSM (1 shl 15)
        val has2g = (bitmask and ((1L shl 0) or (1L shl 1) or (1L shl 3) or (1L shl 6) or (1L shl 10) or (1L shl 15))) != 0L
        
        return when {
            hasNr && !hasLte && !has3g && !has2g -> "NR Only (Kunci 5G)"
            !hasNr && hasLte && !has3g && !has2g -> "LTE Only (Kunci 4G)"
            !hasNr && !hasLte && has3g && !has2g -> "3G Only (Kunci 3G)"
            !hasNr && !hasLte && !has3g && has2g -> "2G Only (Kunci 2G)"
            hasNr && hasLte && !has3g && !has2g -> "NR/LTE (5G/4G)"
            !hasNr && hasLte && has3g && has2g -> "LTE/GSM/WCDMA (4G/3G/2G)"
            hasNr && hasLte && has3g && has2g -> "NR/LTE/GSM/WCDMA (5G/4G/3G/2G)"
            else -> {
                val list = mutableListOf<String>()
                if (hasNr) list.add("5G")
                if (hasLte) list.add("4G")
                if (has3g) list.add("3G")
                if (has2g) list.add("2G")
                if (list.isEmpty()) "Tidak Diketahui" else list.joinToString("/")
            }
        }
    }

    private fun mapNetworkModeToString(mode: Int): String {
        return when (mode) {
            0 -> "WCDMA Preferred (GSM/WCDMA)"
            1 -> "GSM Only (Kunci 2G)"
            2 -> "WCDMA Only (Kunci 3G)"
            3 -> "GSM/WCDMA (Auto)"
            4 -> "CDMA/EvDo (Auto)"
            5 -> "CDMA Only (2G)"
            6 -> "EvDo Only (3G)"
            7 -> "GSM/WCDMA/CDMA/EvDo (Global)"
            8 -> "LTE/CDMA/EvDo"
            9 -> "LTE/GSM/WCDMA (4G/3G/2G)"
            10 -> "LTE/CDMA/EvDo/GSM/WCDMA"
            11 -> "LTE Only (Kunci 4G)"
            12 -> "LTE/WCDMA (4G/3G)"
            13 -> "TD-SCDMA Only"
            14 -> "TD-SCDMA/WCDMA"
            15 -> "TD-SCDMA/LTE"
            16 -> "TD-SCDMA/GSM/WCDMA"
            17 -> "TD-SCDMA/LTE/GSM/WCDMA"
            18 -> "TD-SCDMA/CDMA/EvDo/GSM/WCDMA"
            19 -> "TD-SCDMA/LTE/CDMA/EvDo/GSM/WCDMA"
            20 -> "LTE/TD-SCDMA/GSM/WCDMA"
            21 -> "LTE/TD-SCDMA/WCDMA"
            22 -> "LTE/TD-SCDMA/GSM"
            23 -> "LTE/TD-SCDMA"
            24 -> "LTE/TD-SCDMA/CDMA/EvDo/GSM/WCDMA"
            25 -> "NR Only (Kunci 5G)"
            26 -> "NR/LTE (5G/4G)"
            27 -> "NR/LTE/CDMA/EvDo"
            28 -> "NR/LTE/GSM/WCDMA"
            29 -> "NR/LTE/CDMA/EvDo/GSM/WCDMA"
            30 -> "NR/LTE/WCDMA"
            31 -> "NR/LTE/TD-SCDMA"
            32 -> "NR/LTE/TD-SCDMA/GSM"
            33 -> "NR/LTE/TD-SCDMA/WCDMA"
            34 -> "NR/LTE/TD-SCDMA/GSM/WCDMA"
            35 -> "NR/LTE/TD-SCDMA/CDMA/EvDo/GSM/WCDMA"
            else -> "Mode $mode (Vendor Kustom)"
        }
    }

    init {
        // Start live signal monitoring
        signalMonitor.startMonitoring()
        loadBlockedCalls()
        loadInstalledApps()
        updatePreferredNetworkType()
        scanNearbyServers()
    }

    // Refresh Call screening values
    fun loadBlockedCalls() {
        _blockedCalls.value = settingsHelper.getBlockedCalls()
    }

    fun toggleCallBlocking(enabled: Boolean) {
        settingsHelper.isCallBlockingEnabled = enabled
        _isCallBlockingEnabled.value = enabled
    }

    fun updateBlockMode(mode: Int) {
        settingsHelper.blockMode = mode
        _blockMode.value = mode
    }

    fun clearBlockLogs() {
        settingsHelper.clearBlockedCalls()
        _blockedCalls.value = emptyList()
    }

    // 4. Speed Test Controls
    fun scanNearbyServers() {
        viewModelScope.launch {
            _isScanningServers.value = true
            try {
                val scanned = speedTestManager.scanNearbyServers()
                _nearbyServers.value = scanned
            } catch (e: Exception) {
                _nearbyServers.value = emptyList()
            } finally {
                _isScanningServers.value = false
            }
        }
    }

    fun clearSpeedTestHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            speedTestHistoryRepository.clearAll()
        }
    }

    fun startSpeedTest() {
        cancelSpeedTest()
        speedTestJob = viewModelScope.launch {
            try {
                speedTestManager.runSpeedTest(_selectedTestServer.value, onServerDetected = { detectedServer ->
                    _selectedTestServer.value = detectedServer
                }).collect { state ->
                    _speedTestState.value = state
                    if (state is SpeedTestState.Completed) {
                        viewModelScope.launch(Dispatchers.IO) {
                            speedTestHistoryRepository.insertHistory(
                                serverName = _selectedTestServer.value.name,
                                serverLocation = _selectedTestServer.value.location,
                                downloadMbps = state.downloadSpeedMbps,
                                uploadMbps = state.uploadSpeedMbps,
                                pingMs = state.pingMs,
                                jitterMs = state.jitterMs,
                                packetLossPercent = state.packetLoss
                            )
                        }
                    }
                    // If a call is blocked during active speed test, refresh logs
                    if (state is SpeedTestState.Completed || state is SpeedTestState.DownloadTest || state is SpeedTestState.UploadTest) {
                        loadBlockedCalls()
                    }
                }
            } catch (e: Exception) {
                _speedTestState.value = SpeedTestState.Error("Speed test error: ${e.message}")
            }
        }
    }

    fun cancelSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        _speedTestState.value = SpeedTestState.Idle
    }

    // 5. Intent triggers to Hidden Testing Settings (Preferred Network Type)
    /**
     * Refactored Force 4G Network Diagnostic Launcher
     *
     * A robust, manufacturer-agnostic, and direct launcher targeting hidden system diagnostic
     * screens across Android 11+ and various ROM configurations (Pixel, Samsung, Xiaomi/MIUI/HyperOS,
     * Oppo, Realme, OnePlus, Motorola, MediaTek, etc.).
     *
     * To maximize compatibility without relying on user interaction with the phone dialer,
     * it systematically attempts to start explicit component activities within try-catch blocks.
     */
    fun launchPhoneInfoDirectly(context: Context): Boolean {
        // Group 1: Standard and Advanced variations of RadioInfo (highly diagnostic, direct modem/network configuration)
        val radioInfoIntents = listOf(
            // Standard RadioInfo - Action MAIN (Primary Target on Pixel, Motorola, stock-based ROMs)
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Standard RadioInfo - Default constructor (No explicit Action)
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Deep Link URI for direct secret code activity trigger (extremely powerful on AOSP/Pixel/MOTO)
            Intent(Intent.ACTION_VIEW, Uri.parse("android_secret_code://4636")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Xiaomi/MIUI/HyperOS specific RadioInfo Activity
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Generic Phone process internal RadioInfo settings screen
            Intent().apply {
                setClassName("com.android.phone", "com.android.phone.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Alternative generic RadioInfo Activity naming
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.RadioInfoActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        // Try launching the primary RadioInfo intents first
        for (intent in radioInfoIntents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Silently try next variant
            }
        }

        // Group 2: TestingSettings fallbacks (opens full diagnostic dashboard: Phone info, Usage stats, Wi-Fi info)
        val testingSettingsIntents = listOf(
            // Settings$TestingSettingsActivity - Default constructor
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Settings$TestingSettingsActivity - Action MAIN
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // TestingSettings - Action MAIN
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.TestingSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // TestingSettings - Default constructor
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.TestingSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        // Try launching full Testing Settings fallbacks
        for (intent in testingSettingsIntents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Silently try next fallback
            }
        }

        // Group 3: Manufacturer-specific hidden menus and diagnostic targets
        val manufacturerIntents = listOf(
            // Samsung hidden network settings launcher (Oreo up to Android 15)
            Intent().apply {
                setClassName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.hiddennetworksetting.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.hiddennetworksetting.HiddenNetworkSettingActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.sec.android.app.servicemodeapp", "com.sec.android.app.servicemodeapp.ServiceModeApp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Huawei/Honor specific Hidden RadioInfo Settings
            Intent().apply {
                setClassName("com.huawei.settings", "com.huawei.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // OnePlus / Oppo / Realme Engineering & Network Mode
            Intent().apply {
                setClassName("com.android.engineeringmode", "com.android.engineeringmode.manualtest.ManualTestActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.oppo.engineermode", "com.oppo.engineermode.manualtest.ManualTestActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Xiaomi/MIUI Security-centric settings
            Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.blocklist.MainMenu")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // MediaTek (MTK) Engineer Mode (Oppo, Realme, Vivo, Xiaomi MTK editions)
            Intent().apply {
                setClassName("com.mediatek.engineermode", "com.mediatek.engineermode.EngineerMode")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.mediatek.engineermode", "com.mediatek.engineermode.BandMode")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Realme / Oppo / OnePlus Telephony Network select settings
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.network.telephony.NetworkSelectSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // LG specific LTE Only configuration
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$LteOnlyActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Motorola-specific diagnostic / phone info activity
            Intent().apply {
                setClassName("com.motorola.hiddenmenu", "com.motorola.hiddenmenu.phoneinfo.PhoneInfoActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            // Standard Telephony Mobile Network Settings Activity fallback
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.network.telephony.MobileNetworkActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        // Try launching manufacturer-specific targets
        for (intent in manufacturerIntents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Silently try next custom target
            }
        }

        // Group 4: Low-level SECRET_CODE system broadcast as ultimate programmatic trigger
        try {
            val secretCodeIntent = Intent("android.telephony.action.SECRET_CODE").apply {
                data = Uri.parse("android_secret_code://4636")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.sendBroadcast(secretCodeIntent)
            return true
        } catch (e: Exception) {
            // ignore and try final OS fallbacks
        }

        // Group 5: Core Android OS Settings fallbacks (Mendapat akses ke menu preferred network type)
        val coreSettingsIntents = listOf(
            Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )

        for (intent in coreSettingsIntents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Try next core fallback
            }
        }

        return false
    }

    // Deprecated but kept for backward compatibility to avoid compile errors
    fun getTestingSettingsIntent(): Intent? {
        return Intent(Intent.ACTION_MAIN).apply {
            setClassName("com.android.settings", "com.android.settings.RadioInfo")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val blockedWifi = settingsHelper.getBlockedAppsWifi()
            val blockedCellular = settingsHelper.getBlockedAppsCellular()

            val list = apps.mapNotNull { app ->
                val name = app.loadLabel(pm).toString()
                val pkg = app.packageName
                val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Keep only launcher apps or non-system apps to avoid overwhelming list
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null || !isSystem) {
                    AppInfo(
                        name = name,
                        packageName = pkg,
                        isSystem = isSystem,
                        isBlockedWifi = blockedWifi.contains(pkg),
                        isBlockedCellular = blockedCellular.contains(pkg)
                    )
                } else {
                    null
                }
            }.sortedBy { it.name.lowercase() }

            _installedApps.value = list
        }
    }

    fun refreshFirewallState() {
        _isFirewallEnabled.value = settingsHelper.isFirewallEnabled
        _isGlobalKillSwitch.value = settingsHelper.isGlobalKillSwitch
    }

    fun toggleFirewall(context: Context, enabled: Boolean) {
        settingsHelper.isFirewallEnabled = enabled
        _isFirewallEnabled.value = enabled
        if (enabled) {
            FirewallVpnService.startService(context)
        } else {
            FirewallVpnService.stopService(context)
        }
    }

    fun toggleGlobalKillSwitch(context: Context, enabled: Boolean) {
        settingsHelper.isGlobalKillSwitch = enabled
        _isGlobalKillSwitch.value = enabled
        if (settingsHelper.isFirewallEnabled) {
            FirewallVpnService.startService(context)
        }
    }

    fun toggleAmoledTheme(enabled: Boolean) {
        settingsHelper.isAmoledTheme = enabled
        _isAmoledTheme.value = enabled
    }

    fun toggleAppWifiBlock(packageName: String, context: Context) {
        val current = settingsHelper.getBlockedAppsWifi().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        settingsHelper.setBlockedAppsWifi(current)
        loadInstalledApps()
        if (settingsHelper.isFirewallEnabled) {
            FirewallVpnService.startService(context)
        }
    }

    fun toggleAppCellularBlock(packageName: String, context: Context) {
        val current = settingsHelper.getBlockedAppsCellular().toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        settingsHelper.setBlockedAppsCellular(current)
        loadInstalledApps()
        if (settingsHelper.isFirewallEnabled) {
            FirewallVpnService.startService(context)
        }
    }

    fun refreshBlockedDataStats() {
        _blockedDataStats.value = settingsHelper.getBlockedDataStats()
    }

    fun clearBlockedStats() {
        settingsHelper.clearBlockedDataStats()
        _blockedDataStats.value = emptyMap()
    }

    fun startSignalMonitoring() {
        signalMonitor.startMonitoring()
    }

    fun stopSignalMonitoring() {
        signalMonitor.stopMonitoring()
    }

    override fun onCleared() {
        super.onCleared()
        signalMonitor.stopMonitoring()
        cancelSpeedTest()
        speedTestManager.onDestroy()
    }
}
