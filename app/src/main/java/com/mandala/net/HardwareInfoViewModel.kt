package com.mandala.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HardwareInfoViewModel : ViewModel() {
    private val _cpuInfo = MutableStateFlow(HardwareUtils.getCpuInfo())
    val cpuInfo = _cpuInfo.asStateFlow()

    private val _gpuInfo = MutableStateFlow(HardwareUtils.GpuInfo("Detecting...", "Detecting...", "0%"))
    val gpuInfo = _gpuInfo.asStateFlow()

    private val _deviceInfo = MutableStateFlow(HardwareUtils.DeviceInfo())
    val deviceInfo = _deviceInfo.asStateFlow()

    private val _systemInfo = MutableStateFlow(HardwareUtils.SystemInfo())
    val systemInfo = _systemInfo.asStateFlow()

    private val _batteryInfo = MutableStateFlow(HardwareUtils.BatteryInfo())
    val batteryInfo = _batteryInfo.asStateFlow()

    private val _sensors = MutableStateFlow<List<HardwareUtils.SensorData>>(emptyList())
    val sensors = _sensors.asStateFlow()

    private val _thermalZones = MutableStateFlow<List<HardwareUtils.ThermalZone>>(emptyList())
    val thermalZones = _thermalZones.asStateFlow()

    private val _liveClockSpeeds = MutableStateFlow<Map<Int, String>>(emptyMap())
    val liveClockSpeeds = _liveClockSpeeds.asStateFlow()

    private val _liveAccelerometer = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val liveAccelerometer = _liveAccelerometer.asStateFlow()

    private val _liveGyroscope = MutableStateFlow(floatArrayOf(0f, 0f, 0f))
    val liveGyroscope = _liveGyroscope.asStateFlow()
    
    private var isMonitoring = false
    private var sensorManager: SensorManager? = null
    private var appContext: Context? = null
    private var batteryReceiver: BroadcastReceiver? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                when (it.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> _liveAccelerometer.value = it.values.clone()
                    Sensor.TYPE_GYROSCOPE -> _liveGyroscope.value = it.values.clone()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var isForeground = true

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
    }

    fun startMonitoring(context: Context) {
        if (isMonitoring) return
        isMonitoring = true
        
        val appCtx = context.applicationContext
        appContext = appCtx
        
        _deviceInfo.value = HardwareUtils.getDeviceInfo(appCtx)
        _gpuInfo.value = HardwareUtils.getGpuInfo(appCtx)
        _systemInfo.value = HardwareUtils.getSystemInfo(appCtx)
        
        sensorManager = appCtx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        _sensors.value = HardwareUtils.getSensorsList(sensorManager!!)
        
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                _batteryInfo.value = HardwareUtils.getBatteryInfo(intent)
            }
        }
        batteryReceiver = receiver
        appCtx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (isForeground) {
                    _liveClockSpeeds.value = HardwareUtils.getLiveClockSpeeds(_cpuInfo.value.cores)
                    _deviceInfo.value = HardwareUtils.getDeviceInfo(appCtx) // Update RAM dynamically
                    _thermalZones.value = HardwareUtils.getThermalZones(appCtx, _batteryInfo.value.temperature)
                    _systemInfo.value = _systemInfo.value.copy(uptime = HardwareUtils.formatUptime())
                    val curGpu = _gpuInfo.value
                    _gpuInfo.value = curGpu.copy(load = HardwareUtils.getGpuLoad())
                    delay(1000) // Poll every 1 second when active
                } else {
                    // Scale polling interval to reduce CPU usage in background
                    delay(5000)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            batteryReceiver?.let {
                appContext?.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            // Ignore
        }
        batteryReceiver = null
        appContext = null
        isMonitoring = false
    }
}
