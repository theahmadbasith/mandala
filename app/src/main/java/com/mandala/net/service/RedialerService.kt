package com.mandala.net.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.mandala.net.CallStateMonitor
import com.mandala.net.RedialerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RedialerServiceState(
    val state: RedialerState = RedialerState.IDLE,
    val callCount: Int = 0,
    val countdown: Int = 0,
    val targetNumber: String = ""
)

class RedialerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var callStateMonitor: CallStateMonitor? = null

    private var targetNumber: String = ""
    private var delaySeconds: Int = 5
    private var ringDurationSeconds: Int = 15
    private var maxRetries: Int = 100

    private var callCount = 0
    private var countdown = 0
    private var redialJob: Job? = null
    private var ringTimerJob: Job? = null

    // Overlay fields
    private var overlayView: android.view.View? = null
    private var windowManager: android.view.WindowManager? = null
    private var overlayStatusText: android.widget.TextView? = null
    private var overlaySubText: android.widget.TextView? = null
    private var overlayCardBackground: android.graphics.drawable.GradientDrawable? = null
    private var overlayPauseButton: android.widget.Button? = null
    private var isPaused = false

    companion object {
        private val _serviceState = MutableStateFlow(RedialerServiceState())
        val serviceState: StateFlow<RedialerServiceState> = _serviceState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MandalaNet::RedialerWakeLock"
        )
        callStateMonitor = CallStateMonitor(this) { state ->
            onCallStateChanged(state)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "ACTION_START_REDIAL"

        if (action == "ACTION_STOP_REDIAL" || action == "STOP_SERVICE") {
            stopRedialProcess()
            return START_NOT_STICKY
        }

        if (action == "ACTION_TOGGLE_PAUSE") {
            if (isPaused) {
                resumeRedialing()
            } else {
                pauseRedialing()
            }
            return START_STICKY
        }

        // Extract settings
        targetNumber = intent?.getStringExtra("EXTRA_TARGET_NUMBER") ?: ""
        delaySeconds = intent?.getIntExtra("EXTRA_DELAY_SECONDS", 5) ?: 5
        ringDurationSeconds = intent?.getIntExtra("EXTRA_RING_DURATION", 15) ?: 15
        maxRetries = intent?.getIntExtra("EXTRA_MAX_RETRIES", 100) ?: 100

        if (targetNumber.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
        }

        // Reset counters & state
        callCount = 0
        countdown = 0
        
        showInitialNotification()
        
        // Show floating window overlay on top of other apps
        showOverlay()

        // Start call monitoring
        callStateMonitor?.startMonitoring()

        // Trigger the very first call
        triggerCall()

        return START_STICKY
    }

    private fun triggerCall() {
        if (callCount >= maxRetries) {
            updateState(RedialerState.STOPPED)
            stopRedialProcess()
            return
        }

        callCount++
        updateState(RedialerState.CALLING)
        updateNotification("Memanggil: $targetNumber (Percobaan ke-$callCount)")

        val sanitized = targetNumber.replace(Regex("[^0-9+]"), "")
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$sanitized")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(callIntent)
            } catch (t: Throwable) {
                t.printStackTrace()
                stopRedialProcess()
            }
        } else {
            stopRedialProcess()
        }
    }

    private fun onCallStateChanged(state: Int) {
        val current = _serviceState.value.state
        if (current == RedialerState.IDLE || current == RedialerState.STOPPED || current == RedialerState.CALL_SUCCESS) {
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call is outgoing or active. Start the ring duration timer only if not already active.
                if (ringTimerJob == null || ringTimerJob?.isActive == false) {
                    if (!isPaused) {
                        ringTimerJob = serviceScope.launch {
                            delay(ringDurationSeconds * 1000L)
                            // If call lasts more than threshold, consider it either voicemail, answered, or done
                            endCall()
                        }
                    }
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended
                ringTimerJob?.cancel()
                if (isPaused) {
                    updateState(RedialerState.PAUSED)
                } else {
                    scheduleRedial()
                }
            }
        }
    }

    private fun pauseRedialing() {
        isPaused = true
        redialJob?.cancel()
        ringTimerJob?.cancel()
        updateState(RedialerState.PAUSED)
        updateNotification("Auto Redialer ditangguhkan sementara")
    }

    private fun resumeRedialing() {
        isPaused = false
        scheduleRedial()
    }

    private fun scheduleRedial() {
        if (isPaused) {
            updateState(RedialerState.PAUSED)
            return
        }
        if (callCount >= maxRetries) {
            updateState(RedialerState.STOPPED)
            stopRedialProcess()
            return
        }

        updateState(RedialerState.WAITING_TO_REDIAL)
        redialJob?.cancel()
        redialJob = serviceScope.launch {
            for (i in delaySeconds downTo 1) {
                if (isPaused) {
                    updateState(RedialerState.PAUSED)
                    break
                }
                countdown = i
                updateState(RedialerState.WAITING_TO_REDIAL)
                updateNotification("Menunggu Redial dalam $i detik...")
                delay(1000)
            }
            if (!isPaused) {
                countdown = 0
                updateState(RedialerState.WAITING_TO_REDIAL)
                triggerCall()
            }
        }
    }

    private fun endCall() {
        var callEnded = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    telecomManager?.endCall()
                    callEnded = true
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Root fallback to forcefully hang up the call on rooted devices / custom ROMs
        if (!callEnded) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent 6"))
            } catch (e: Exception) {
                // Ignore if not rooted or execution fails
            }
        }
    }

    private fun stopRedialProcess() {
        redialJob?.cancel()
        ringTimerJob?.cancel()
        callStateMonitor?.stopMonitoring()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        removeOverlay()
        updateState(RedialerState.STOPPED)
        stopForeground(true)
        stopSelf()
    }

    private fun updateState(state: RedialerState) {
        _serviceState.value = RedialerServiceState(
            state = state,
            callCount = callCount,
            countdown = countdown,
            targetNumber = targetNumber
        )
        // Update overlay UI on main thread
        serviceScope.launch(Dispatchers.Main) {
            updateOverlayUI(state)
        }
    }

    private fun isDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun showOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            return
        }
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            android.view.WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 80
            y = 250
        }

        // Root container (Card/Layout)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val paddingVal = dpToPx(12f).toInt()
            setPadding(paddingVal, paddingVal, paddingVal, paddingVal)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            minimumWidth = dpToPx(210f).toInt()
        }

        val isDark = isDarkMode()

        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (isDark) 0xF20F172A.toInt() else 0xF9F8FAFC.toInt())
            cornerRadius = dpToPx(16f)
            val strokeColor = if (isDark) 0xFF334155.toInt() else 0xFFCBD5E1.toInt()
            setStroke(dpToPx(1.5f).toInt(), strokeColor)
        }
        container.background = bgDrawable
        overlayCardBackground = bgDrawable

        // Add a small drag handle line at the top
        val dragHandle = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(36f).toInt(), dpToPx(4f).toInt()).apply {
                bottomMargin = dpToPx(10f).toInt()
            }
            val handleBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDark) 0xFF475569.toInt() else 0xFF94A3B8.toInt()) // slate-600 or slate-400
                cornerRadius = dpToPx(2f)
            }
            background = handleBg
        }
        container.addView(dragHandle)

        // Title/Header
        val titleText = android.widget.TextView(this).apply {
            text = "AUTO REDIAL"
            textSize = 9.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (isDark) 0xFF94A3B8.toInt() else 0xFF475569.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(4f).toInt())
        }
        container.addView(titleText)

        // Status Text (e.g. MENGHUBUNGI...)
        val statusText = android.widget.TextView(this).apply {
            text = "MEMULAI..."
            textSize = 13.5f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setTextColor(if (isDark) 0xFF00E5FF.toInt() else 0xFF0891B2.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(4f).toInt())
        }
        container.addView(statusText)
        overlayStatusText = statusText

        // Sub Text (Single line to prevent wrapping or collision)
        val subText = android.widget.TextView(this).apply {
            text = "Percobaan #$callCount • $targetNumber"
            textSize = 10.5f
            typeface = android.graphics.Typeface.DEFAULT
            setTextColor(if (isDark) 0xFFCBD5E1.toInt() else 0xFF1E293B.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, dpToPx(10f).toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(subText)
        overlaySubText = subText

        // Buttons Container (Horizontal Layout)
        val buttonContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Pause/Mulai Button
        val pauseBtn = android.widget.Button(this).apply {
            text = if (isPaused) "MULAI" else "JEDA"
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 0)
            
            val btnBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isPaused) 0xFF22C55E.toInt() else 0xFFF97316.toInt()) // green / orange
                cornerRadius = dpToPx(10f)
            }
            background = btnBg
            
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                dpToPx(34f).toInt(),
                1f
            ).apply {
                rightMargin = dpToPx(4f).toInt()
            }
            
            setOnClickListener {
                val toggleIntent = Intent(this@RedialerService, RedialerService::class.java).apply {
                    action = "ACTION_TOGGLE_PAUSE"
                }
                startService(toggleIntent)
            }
        }
        buttonContainer.addView(pauseBtn)
        overlayPauseButton = pauseBtn

        // Stop Button
        val stopBtn = android.widget.Button(this).apply {
            text = "STOP"
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 0)
            
            val btnBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFEF4444.toInt()) // Soft red
                cornerRadius = dpToPx(10f)
            }
            background = btnBg
            
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                dpToPx(34f).toInt(),
                1f
            ).apply {
                leftMargin = dpToPx(4f).toInt()
            }
            
            setOnClickListener {
                stopRedialProcess()
            }
        }
        buttonContainer.addView(stopBtn)

        container.addView(buttonContainer)

        // Add touch/drag support
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = container
        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        val overlay = overlayView
        if (overlay != null) {
            try {
                windowManager?.removeView(overlay)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            overlayStatusText = null
            overlaySubText = null
            overlayCardBackground = null
            overlayPauseButton = null
        }
    }

    private fun updateOverlayUI(state: RedialerState) {
        val statusText = overlayStatusText ?: return
        val subText = overlaySubText ?: return
        val bg = overlayCardBackground ?: return
        val pauseBtn = overlayPauseButton

        if (pauseBtn != null) {
            if (isPaused || state == RedialerState.PAUSED) {
                pauseBtn.text = "MULAI"
                val btnBg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF22C55E.toInt()) // green
                    cornerRadius = dpToPx(10f)
                }
                pauseBtn.background = btnBg
            } else {
                pauseBtn.text = "JEDA"
                val btnBg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFF97316.toInt()) // orange
                    cornerRadius = dpToPx(10f)
                }
                pauseBtn.background = btnBg
            }
        }

        val isDark = isDarkMode()

        when (state) {
            RedialerState.CALLING -> {
                statusText.text = "MENGHUBUNGI..."
                val color = if (isDark) 0xFF00E5FF.toInt() else 0xFF0891B2.toInt()
                statusText.setTextColor(color)
                bg.setStroke(dpToPx(1.5f).toInt(), color)
                subText.text = "Percobaan #$callCount • $targetNumber"
            }
            RedialerState.WAITING_TO_REDIAL -> {
                statusText.text = "REDIAL: ${countdown}s"
                val color = if (isDark) 0xFFF97316.toInt() else 0xFFEA580C.toInt()
                statusText.setTextColor(color)
                bg.setStroke(dpToPx(1.5f).toInt(), color)
                subText.text = "Bersiap mengulang • $targetNumber"
            }
            RedialerState.CALL_SUCCESS -> {
                statusText.text = "TERHUBUNG!"
                val color = if (isDark) 0xFF22C55E.toInt() else 0xFF16A34A.toInt()
                statusText.setTextColor(color)
                bg.setStroke(dpToPx(1.5f).toInt(), color)
                subText.text = "Panggilan sukses • $targetNumber"
            }
            RedialerState.PAUSED -> {
                statusText.text = "DITANGGUHKAN"
                statusText.setTextColor(if (isDark) 0xFFE2E8F0.toInt() else 0xFF475569.toInt())
                bg.setStroke(dpToPx(1.5f).toInt(), 0xFF64748B.toInt())
                subText.text = "Panggilan dijeda • $targetNumber"
            }
            else -> {
                statusText.text = "DIHENTIKAN"
                statusText.setTextColor(if (isDark) 0xFF94A3B8.toInt() else 0xFF64748B.toInt())
                bg.setStroke(dpToPx(1.5f).toInt(), if (isDark) 0xFF334155.toInt() else 0xFFCBD5E1.toInt())
                subText.text = "Redialer dinonaktifkan"
            }
        }
    }

    private fun showInitialNotification() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "RedialerChannel")
            .setContentTitle("Auto Redialer Aktif")
            .setContentText("Memulai Auto Redialer...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(2, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(2, notification)
        }
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, "RedialerChannel")
            .setContentTitle("Auto Redialer Aktif")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(2, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        callStateMonitor?.stopMonitoring()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        removeOverlay()
        _serviceState.value = RedialerServiceState(state = RedialerState.IDLE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "RedialerChannel",
                "Redialer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
