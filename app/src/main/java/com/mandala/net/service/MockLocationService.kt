@file:Suppress("DEPRECATION", "MissingPermission")
package com.mandala.net.service

import com.mandala.net.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.mandala.net.CyberTheme
import com.mandala.net.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

// Shared Manager for seamless coordinate and state updates
object MockLocationManager {
    val latitude = MutableStateFlow(-6.2088) // Default Jakarta
    val longitude = MutableStateFlow(106.8456)
    val isActive = MutableStateFlow(false)
    val showJoystick = MutableStateFlow(false)
    val speedKmh = MutableStateFlow(30.0) // speed: Motor=30, Mobil=60
    val bearing = MutableStateFlow(0.0f)
    val mockLocationError = MutableStateFlow<String?>(null)
    
    // Route simulation state
    val isSimulatingRoute = MutableStateFlow(false)
    val routeWaypoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    
    // Persistent UI route states for screen recreation survival
    val userWaypoints = MutableStateFlow<List<com.mandala.net.ui.LocationBookmark>>(emptyList())
    val routePointsList = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val routeTotalDistanceMeters = MutableStateFlow(0f)
    val routeRemainingDistanceMeters = MutableStateFlow(0f)
    val routeRemainingSeconds = MutableStateFlow(0)
    
    // Cached MapView instance for instant, flicker-free tab switches
    var cachedMapView: Any? = null
    var currentStyleName: String? = null
}

class MockLocationService : Service() {

    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var updateJob: Job? = null
    
    private var windowManager: WindowManager? = null
    private var joystickView: ComposeView? = null
    private var lifecycleOwner: CustomLifecycleOwner? = null

    private val CHANNEL_ID = "mock_location_channel"
    private val NOTIFICATION_ID = 2026
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "com.mandala.net:MockLocationWakeLock").apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24 hours max safe timeout
                }
                Log.d("MockLocationService", "WakeLock acquired successfully")
            } catch (e: Exception) {
                Log.e("MockLocationService", "Failed to acquire WakeLock: ${e.message}")
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            Log.d("MockLocationService", "WakeLock released")
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to release WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "ACTION_START"
        
        when (action) {
            "ACTION_START" -> {
                val prefs = getSharedPreferences("MockPrefs", Context.MODE_PRIVATE)
                val savedLat = prefs.getFloat("last_lat", -999f)
                val savedLng = prefs.getFloat("last_lng", -999f)
                
                val defaultLat = if (savedLat != -999f) savedLat.toDouble() else -6.2088
                val defaultLng = if (savedLng != -999f) savedLng.toDouble() else 106.8456

                val initLat = intent?.getDoubleExtra("EXTRA_LAT", defaultLat) ?: defaultLat
                val initLng = intent?.getDoubleExtra("EXTRA_LNG", defaultLng) ?: defaultLng
                MockLocationManager.latitude.value = initLat
                MockLocationManager.longitude.value = initLng
                MockLocationManager.isActive.value = true
                
                acquireWakeLock()
                startForegroundServiceWithNotification()
                setupMockProviders()
                startMockUpdatesLoop()
                
                // Track joystick state
                serviceScope.launch {
                    MockLocationManager.showJoystick.collect { show ->
                        if (show) {
                            showFloatingJoystick()
                        } else {
                            hideFloatingJoystick()
                        }
                    }
                }
            }
            "ACTION_STOP", "STOP_SERVICE" -> {
                stopSelf()
            }
            "ACTION_TOGGLE_JOYSTICK" -> {
                MockLocationManager.showJoystick.value = !MockLocationManager.showJoystick.value
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val title = "Mock Location Active"
        val lat = String.format("%.5f", MockLocationManager.latitude.value)
        val lng = String.format("%.5f", MockLocationManager.longitude.value)
        val text = "Spoofing to Lat: $lat, Lng: $lng"

        val stopIntent = Intent(this, MockLocationService::class.java).apply { action = "ACTION_STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val joystickIntent = Intent(this, MockLocationService::class.java).apply { action = "ACTION_TOGGLE_JOYSTICK" }
        val joystickPendingIntent = PendingIntent.getService(this, 2, joystickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val joystickActionLabel = if (MockLocationManager.showJoystick.value) "Hide Joystick" else "Show Joystick"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_compass, joystickActionLabel, joystickPendingIntent)
            .setColor(0xFF00FFCC.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mock Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays running status of fake GPS spoofing and joystick overlays"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupMockProviders() {
        setupMockProvider(LocationManager.GPS_PROVIDER)
        setupMockProvider(LocationManager.NETWORK_PROVIDER)
        setupMockProvider("fused")
        
        try {
            fusedLocationClient.setMockMode(true)
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to set mock mode on FusedLocationClient: ${e.message}")
        }
    }

    private fun setupMockProvider(provider: String) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.addTestProvider(
                provider,
                false, // requiresNetwork
                false, // requiresSatellite
                false, // requiresCell
                false, // hasMonetaryCost
                true,  // supportsAltitude
                true,  // supportsSpeed
                true,  // supportsBearing
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(provider, true)
            MockLocationManager.mockLocationError.value = null
        } catch (e: SecurityException) {
            Log.e("MockLocationService", "SecurityException setting up test provider $provider: ${e.message}")
            MockLocationManager.mockLocationError.value = "Aplikasi ini belum diizinkan sebagai Aplikasi Lokasi Palsu di Opsi Pengembang."
        } catch (e: Exception) {
            Log.e("MockLocationService", "Error setting up test provider $provider: ${e.message}")
        }
    }

    private fun startMockUpdatesLoop() {
        updateJob?.cancel()
        updateJob = serviceScope.launch(Dispatchers.Main) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            while (isActive) {
                if (MockLocationManager.isSimulatingRoute.value && MockLocationManager.routeWaypoints.value.size >= 2) {
                    val waypoints = MockLocationManager.routeWaypoints.value
                    
                    for (idx in 0 until waypoints.size - 1) {
                        if (!MockLocationManager.isSimulatingRoute.value || !isActive) break
                        
                        val startPt = waypoints[idx]
                        val endPt = waypoints[idx + 1]
                        
                        val distanceMeters = FloatArray(1)
                        android.location.Location.distanceBetween(startPt.first, startPt.second, endPt.first, endPt.second, distanceMeters)
                        
                        if (distanceMeters[0] <= 0) continue
                        
                        val speedMs = MockLocationManager.speedKmh.value / 3.6
                        val durationMs = (distanceMeters[0] / speedMs) * 1000.0
                        val updateIntervalMs = 500L
                        val steps = (durationMs / updateIntervalMs).toInt().coerceAtLeast(1)
                        
                        val latStep = (endPt.first - startPt.first) / steps
                        val lngStep = (endPt.second - startPt.second) / steps
                        
                        val startLoc = Location("").apply { latitude = startPt.first; longitude = startPt.second }
                        val endLoc = Location("").apply { latitude = endPt.first; longitude = endPt.second }
                        MockLocationManager.bearing.value = startLoc.bearingTo(endLoc)
                        
                        for (i in 0 until steps) {
                            if (!MockLocationManager.isSimulatingRoute.value || !isActive) break
                            
                            val currentLat = startPt.first + latStep * i
                            val currentLng = startPt.second + lngStep * i
                            
                            MockLocationManager.latitude.value = currentLat
                            MockLocationManager.longitude.value = currentLng
                            
                            publishMockLocation(locationManager, LocationManager.GPS_PROVIDER, currentLat, currentLng)
                            publishMockLocation(locationManager, LocationManager.NETWORK_PROVIDER, currentLat, currentLng)
                            publishMockLocation(locationManager, "fused", currentLat, currentLng)
                            
                            updateNotification()
                            delay(updateIntervalMs)
                        }
                    }
                    if (MockLocationManager.isSimulatingRoute.value) {
                        MockLocationManager.isSimulatingRoute.value = false
                        MockLocationManager.routeWaypoints.value = emptyList()
                    }
                } else {
                    val currentLat = MockLocationManager.latitude.value
                    val currentLng = MockLocationManager.longitude.value
                    
                    publishMockLocation(locationManager, LocationManager.GPS_PROVIDER, currentLat, currentLng)
                    publishMockLocation(locationManager, LocationManager.NETWORK_PROVIDER, currentLat, currentLng)
                    publishMockLocation(locationManager, "fused", currentLat, currentLng)
                    
                    updateNotification()
                    delay(500)
                }
            }
        }
    }

    private fun saveLastLocation(lat: Double, lng: Double) {
        val prefs = getSharedPreferences("MockPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat("last_lat", lat.toFloat())
            putFloat("last_lng", lng.toFloat())
            apply()
        }
    }

    private fun publishMockLocation(locationManager: LocationManager, provider: String, lat: Double, lng: Double) {
        try {
            saveLastLocation(lat, lng)
            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                altitude = 12.0
                time = System.currentTimeMillis()
                accuracy = 3.5f
                speed = (MockLocationManager.speedKmh.value / 3.6).toFloat()
                bearing = MockLocationManager.bearing.value
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 3.5f
                    speedAccuracyMetersPerSecond = 1.0f
                    bearingAccuracyDegrees = 5.0f
                }
            }
            locationManager.setTestProviderLocation(provider, mockLocation)
            
            if (provider == "fused") {
                try {
                    fusedLocationClient.setMockLocation(mockLocation)
                } catch (e: Exception) {
                    Log.e("MockLocationService", "FusedLocationClient setMockLocation failed: ${e.message}")
                }
            }
            
            MockLocationManager.mockLocationError.value = null
        } catch (e: SecurityException) {
            Log.e("MockLocationService", "Failed to publish mock location for $provider: ${e.message}")
            MockLocationManager.mockLocationError.value = "Aplikasi ini belum diizinkan sebagai Aplikasi Lokasi Palsu di Opsi Pengembang."
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to publish mock location for $provider: ${e.message}")
        }
    }

    private fun showFloatingJoystick() {
        if (joystickView != null) return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner = CustomLifecycleOwner()
        
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = CyberTheme.PrimaryAccent,
                    background = Color(0xFF0F1424),
                    surface = Color(0xFF1E2638)
                )) {
                    FloatingJoystickOverlayContent(
                        onClose = {
                            MockLocationManager.showJoystick.value = false
                        },
                        onDragWindow = { dx, dy ->
                            params.x = (params.x + dx).coerceAtLeast(0)
                            params.y = (params.y + dy).coerceAtLeast(0)
                            windowManager?.updateViewLayout(this, params)
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, params)
            joystickView = composeView
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to add floating window: ${e.message}")
            Toast.makeText(this, "Izin Tampilkan di atas aplikasi lain diperlukan!", Toast.LENGTH_LONG).show()
            MockLocationManager.showJoystick.value = false
        }
    }

    private fun hideFloatingJoystick() {
        try {
            joystickView?.let {
                windowManager?.removeView(it)
            }
            lifecycleOwner?.destroy()
        } catch (e: Exception) {
            Log.e("MockLocationService", "Error removing overlay view: ${e.message}")
        } finally {
            joystickView = null
            lifecycleOwner = null
        }
    }

    private fun cleanMockProviders() {
        removeMockProvider(LocationManager.GPS_PROVIDER)
        removeMockProvider(LocationManager.NETWORK_PROVIDER)
        removeMockProvider("fused")
        
        try {
            fusedLocationClient.setMockMode(false)
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to disable mock mode on FusedLocationClient: ${e.message}")
        }
    }

    private fun removeMockProvider(provider: String) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.removeTestProvider(provider)
        } catch (e: Exception) {
            Log.e("MockLocationService", "Failed to clean up provider $provider: ${e.message}")
        }
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceJob.cancel()
        hideFloatingJoystick()
        cleanMockProviders()
        releaseWakeLock()
        MockLocationManager.isActive.value = false
        MockLocationManager.showJoystick.value = false
        MockLocationManager.isSimulatingRoute.value = false
        super.onDestroy()
    }
}

// Draggable Window and Joystick view
@Composable
fun FloatingJoystickOverlayContent(
    onClose: () -> Unit,
    onDragWindow: (Int, Int) -> Unit
) {
    val latState by MockLocationManager.latitude.collectAsState()
    val lngState by MockLocationManager.longitude.collectAsState()
    val speedKmh by MockLocationManager.speedKmh.collectAsState()

    var activeDragOffset by remember { mutableStateOf(Offset.Zero) }
    val outerRadius = 60.dp
    val outerRadiusPx = 150f // approx in px

    // Periodic coordinate update ticker when joystick is active
    LaunchedEffect(activeDragOffset) {
        if (activeDragOffset != Offset.Zero) {
            while (true) {
                val distance = sqrt(activeDragOffset.x * activeDragOffset.x + activeDragOffset.y * activeDragOffset.y)
                if (distance > 0) {
                    val directionX = activeDragOffset.x / distance
                    val directionY = activeDragOffset.y / distance
                    val fraction = (distance / outerRadiusPx).coerceAtMost(1.0f)
                    
                    // Interval update movement
                    val speedMps = (speedKmh / 3.6) * 0.15 // 150ms step update
                    val moveMeters = speedMps * fraction
                    
                    // Convert movement to Lat / Lng
                    val deltaLat = -directionY * moveMeters / 111111.0
                    val deltaLng = directionX * moveMeters / (111111.0 * cos(Math.toRadians(latState)))
                    
                    val newLat = latState + deltaLat
                    val newLng = lngState + deltaLng
                    
                    val startLoc = android.location.Location("").apply { latitude = latState; longitude = lngState }
                    val endLoc = android.location.Location("").apply { latitude = newLat; longitude = newLng }
                    MockLocationManager.bearing.value = startLoc.bearingTo(endLoc)
                    
                    MockLocationManager.latitude.value = newLat
                    MockLocationManager.longitude.value = newLng
                }
                delay(150)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424).copy(alpha = 0.95f)),
        border = BorderStroke(1.5.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .width(180.dp)
            .padding(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            // Drag handle / Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragWindow(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                        }
                    }
                    .background(Color(0xFF161F38), RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag Window",
                    tint = CyberTheme.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "GPS JOYSTICK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.PrimaryAccent
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = CyberTheme.TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onClose() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Coordinates text
            Text(
                text = "Lat: ${String.format("%.5f", latState)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = CyberTheme.TextPrimary
            )
            Text(
                text = "Lng: ${String.format("%.5f", lngState)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = CyberTheme.TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Beautiful interactive Joystick control area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(outerRadius * 2)
                    .background(Color(0xFF161F38).copy(alpha = 0.8f), CircleShape)
                    .border(2.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.3f), CircleShape)
            ) {
                // Outer circle graphics (compass lines)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = size / 2.0f
                    // Cardinal indicators
                    drawCircle(
                        color = CyberTheme.PrimaryAccent.copy(alpha = 0.08f),
                        radius = size.minDimension / 4.0f
                    )
                }

                // Inner Joystick Handle
                val handleRadius = 22.dp
                val distance = sqrt(activeDragOffset.x * activeDragOffset.x + activeDragOffset.y * activeDragOffset.y)
                val displayOffset = if (distance > outerRadiusPx) {
                    Offset(activeDragOffset.x / distance * outerRadiusPx, activeDragOffset.y / distance * outerRadiusPx)
                } else {
                    activeDragOffset
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(displayOffset.x.roundToInt(), displayOffset.y.roundToInt()) }
                        .size(handleRadius * 2)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(CyberTheme.PrimaryAccent, Color(0xFF00AA88))
                            )
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { activeDragOffset = Offset.Zero },
                                onDragCancel = { activeDragOffset = Offset.Zero },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newOffset = activeDragOffset + dragAmount
                                    val newDistance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                                    activeDragOffset = if (newDistance > outerRadiusPx) {
                                        Offset(newOffset.x / newDistance * outerRadiusPx, newOffset.y / newDistance * outerRadiusPx)
                                    } else {
                                        newOffset
                                    }
                                }
                            )
                        }
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Handle",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Speeds row: Motor, Mobil
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val speeds = listOf(
                    Triple("Motor", 30.0, "🏍️"),
                    Triple("Mobil", 60.0, "🚗")
                )
                speeds.forEach { (label, value, emoji) ->
                    val selected = speedKmh == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) CyberTheme.PrimaryAccent else Color(0xFF161F38)
                            )
                            .clickable {
                                MockLocationManager.speedKmh.value = value
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = emoji, fontSize = 12.sp)
                            Text(
                                text = label,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.Black else CyberTheme.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

// Custom Lifecycle and SavedState registry helper to prevent crashes inside WindowManager ComposeView
class CustomLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
