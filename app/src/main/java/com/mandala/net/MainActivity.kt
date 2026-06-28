package com.mandala.net

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mandala.net.ui.theme.MandalaTheme
import androidx.compose.animation.core.*
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider

@Composable
fun MandalaLogo(
    modifier: Modifier = Modifier,
    spinSpeed: Int = 0
) {
    val rotation = if (spinSpeed > 0) {
        val infiniteTransition = rememberInfiniteTransition(label = "mandala_logo_spin")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(spinSpeed, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin"
        ).value
    } else {
        0f
    }

    Image(
        painter = painterResource(id = R.drawable.mandala),
        contentDescription = "Mandala Logo",
        modifier = modifier.graphicsLayer(rotationZ = rotation)
    )
}

@Composable
fun AnimatedSplashScreen(onFinish: () -> Unit) {
    // 1. Entrance Control States
    var logoTriggered by remember { mutableStateOf(false) }
    var titleTriggered by remember { mutableStateOf(false) }
    var progressValue by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("Inisialisasi Keamanan...") }

    // 2. Motion Values
    // Logo scale-up with an elegant spring bouncy effect
    val logoScale by animateFloatAsState(
        targetValue = if (logoTriggered) 1.1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    // Logo initial swift spin during entrance
    val logoEntranceRotation by animateFloatAsState(
        targetValue = if (logoTriggered) 360f else 0f,
        animationSpec = tween(durationMillis = 1400, easing = EaseOutBack),
        label = "logo_entrance_rot"
    )

    // Ambient slow rotation for continuous gentle motion
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_rot")
    val ambientRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_rotation"
    )

    // Continuous breathing scale of the logo
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    // Background glowing pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseOutQuart),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseOutQuart),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    // Title Entrance (slide-up + fade-in)
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleTriggered) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = EaseInOutCubic),
        label = "title_alpha"
    )
    val titleOffsetY by animateDpAsState(
        targetValue = if (titleTriggered) 0.dp else 20.dp,
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "title_offset"
    )

    // Subtitle Entrance (slide-up + fade-in, slightly delayed)
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (titleTriggered) 0.8f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 200, easing = EaseInOutCubic),
        label = "subtitle_alpha"
    )

    // Dynamic progress bar state
    val progressAnim by animateFloatAsState(
        targetValue = progressValue,
        animationSpec = tween(durationMillis = 3200, easing = EaseInOutQuad),
        label = "progress_val"
    )

    // Timeline sequence execution
    LaunchedEffect(Unit) {
        delay(150)
        logoTriggered = true
        delay(350)
        titleTriggered = true
        
        // Progress steps
        delay(300)
        progressValue = 0.28f
        statusText = "Menganalisis Keamanan Jaringan..."
        
        delay(700)
        progressValue = 0.65f
        statusText = "Memeriksa VPN & Firewall Mandala..."
        
        delay(900)
        progressValue = 0.90f
        statusText = "Memuat Konfigurasi Pengguna..."
        
        delay(600)
        progressValue = 1.0f
        statusText = "Sistem Siap!"
        
        delay(500)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic background grid/particles pattern overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            // Draw vertical grid lines with light alpha
            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = CyberTheme.PrimaryAccent.copy(alpha = 0.15f),
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            // Draw horizontal grid lines with light alpha
            for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = CyberTheme.PrimaryAccent.copy(alpha = 0.15f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .fillMaxWidth()
        ) {
            // Logo Container with glowing pulses
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .graphicsLayer(
                        scaleX = logoScale * breathingScale,
                        scaleY = logoScale * breathingScale
                        // Rotation removed to avoid tilt
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Expanding background glow pulse 1
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer(
                            scaleX = pulseScale,
                            scaleY = pulseScale,
                            alpha = pulseAlpha
                        )
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CyberTheme.PrimaryAccent.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )

                // Stationary soft ambient blue glow behind the logo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CyberTheme.PrimaryAccent.copy(alpha = 0.2f), Color.Transparent)
                            )
                        )
                )

                // Mandala Logo Image
                Image(
                    painter = painterResource(id = R.drawable.mandala),
                    contentDescription = "Mandala Logo",
                    modifier = Modifier
                        .fillMaxSize(0.85f)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Text Title Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer(
                    alpha = titleAlpha,
                    translationY = titleOffsetY.value
                )
            ) {
                Text(
                    text = "MANDALA",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 36.sp,
                    color = CyberTheme.TextPrimary,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(
                            color = CyberTheme.PrimaryAccent.copy(alpha = 0.4f),
                            offset = Offset(0f, 4f),
                            blurRadius = 12f
                        )
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "MOBILE ADVANCED NETWORK DEVICE AND LOCAL ACCESS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = CyberTheme.PrimaryAccent,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer(alpha = subtitleAlpha)
                )
            }

            Spacer(modifier = Modifier.height(56.dp))

            // High-quality Progress & Status Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .graphicsLayer(alpha = titleAlpha)
            ) {
                // Custom Sleek Glowing Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.5f)), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressAnim)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        CyberTheme.PrimaryAccent.copy(alpha = 0.5f),
                                        CyberTheme.PrimaryAccent
                                    )
                                ),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Info Row (Status and Percentage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = CyberTheme.TextSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = "${(progressAnim * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CyberTheme.TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Secure tag at the very bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .graphicsLayer(alpha = titleAlpha)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Icon",
                    tint = CyberTheme.PrimaryAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SECURE PROTOCOL ACTIVE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = CyberTheme.PrimaryAccent,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val redialerViewModel: RedialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val tabIndex = intent?.getIntExtra("tab_to_open", -1) ?: -1
        if (tabIndex >= 0) {
            viewModel.navigateToTab(tabIndex)
        }

        setContent {
            val amoledEnabled by viewModel.isAmoledTheme.collectAsStateWithLifecycle()
            val view = androidx.compose.ui.platform.LocalView.current
            
            androidx.compose.runtime.DisposableEffect(amoledEnabled) {
                val window = (view.context as? android.app.Activity)?.window
                if (window != null) {
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !amoledEnabled
                    controller.isAppearanceLightNavigationBars = !amoledEnabled
                }
                onDispose {}
            }

            MandalaTheme(darkTheme = amoledEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    AnimatedContent(
                        targetState = showSplash,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(700, easing = EaseInOutCubic)) togetherWith
                                    fadeOut(animationSpec = tween(700, easing = EaseInOutCubic))
                        },
                        label = "SplashToMainTransition"
                    ) { isSplash ->
                        if (isSplash) {
                            AnimatedSplashScreen(onFinish = { showSplash = false })
                        } else {
                            MainScreen(viewModel = viewModel, redialerViewModel = redialerViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadBlockedCalls()
        viewModel.refreshFirewallState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tabIndex = intent.getIntExtra("tab_to_open", -1) ?: -1
        if (tabIndex >= 0) {
            viewModel.navigateToTab(tabIndex)
        }
    }
}

// Visual Palette for "Professional Polish" Theme
object CyberTheme {
    var isDark: Boolean = false
    var isAmoled: Boolean = false

    val PrimaryAccent: Color
        get() = if (isDark || isAmoled) Color(0xFF64B5F6) else Color(0xFF0061A4)

    val SuccessGreen: Color
        get() = if (isDark || isAmoled) Color(0xFF81C784) else Color(0xFF1B5E20)

    val WarningOrange: Color
        get() = if (isDark || isAmoled) Color(0xFFFFB74D) else Color(0xFFE65100)

    val ErrorRed: Color
        get() = if (isDark || isAmoled) Color(0xFFE57373) else Color(0xFFB71C1C)

    val TextPrimary: Color
        get() = if (isDark || isAmoled) Color(0xFFFFFFFF) else Color(0xFF001D35)

    val TextSecondary: Color
        get() = if (isDark || isAmoled) Color(0xFFB0BEC5) else Color(0xFF5A6E85)

    // HTML Spec specific colors
    val SignalCardBg: Color
        get() = when {
            isAmoled -> Color(0xFF0C0C0C) // true AMOLED card color (ultra dark)
            isDark -> Color(0xFF1D2736)
            else -> Color(0xFFD1E4FF)
        }

    val SignalCardBorder: Color
        get() = when {
            isAmoled -> Color(0xFF2A374A) // High-visibility slate-blue card border
            isDark -> Color(0xFF2E3B4E)
            else -> Color(0xFFAAC7EB)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, redialerViewModel: com.mandala.net.RedialerViewModel) {
    val context = LocalContext.current
    val amoledEnabled by viewModel.isAmoledTheme.collectAsStateWithLifecycle()

    val rotationAngle by animateFloatAsState(
        targetValue = if (amoledEnabled) 360f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "theme_icon_rotation"
    )

    // Dynamically apply to CyberTheme
    CyberTheme.isDark = amoledEnabled
    CyberTheme.isAmoled = amoledEnabled

    var currentTab by remember { mutableStateOf(0) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var initialDialogSection by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val activity = context as? ComponentActivity
        val reqVpn = activity?.intent?.getBooleanExtra("req_vpn_permission", false) ?: false
        if (reqVpn) {
            currentTab = 5 // Index 5 is Firewall
        }
        
        viewModel.tabToOpen.collect { targetTab ->
            if (targetTab in 0..6) {
                currentTab = targetTab
            }
        }
    }
    
    // State of permissions
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Role request launcher for Call Screening
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    } else null

    var isCallScreeningRoleGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            } else true
        )
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        isCallScreeningRoleGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else true
        if (isCallScreeningRoleGranted) {
            Toast.makeText(context, "Call Screening Role Diaktifkan!", Toast.LENGTH_SHORT).show()
        }
        viewModel.loadBlockedCalls()
    }

    // Permission Request Launchers
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPhonePermission = permissions[android.Manifest.permission.READ_PHONE_STATE] ?: hasPhonePermission
        hasLocationPermission = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
    }

    AnimatedContent(
        targetState = amoledEnabled,
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
        },
        label = "ThemeCrossFade"
    ) { targetDark ->
        com.mandala.net.ui.theme.MandalaTheme(darkTheme = targetDark) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                topBar = {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            initialDialogSection = 0
                                            showDetailDialog = true
                                        }
                                        .padding(6.dp)
                                ) {
                                    MandalaLogo(
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "MANDALA",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CyberTheme.TextPrimary,
                                            fontSize = 18.sp,
                                            lineHeight = 20.sp
                                        )
                                        Text(
                                            text = "MOBILE ADVANCED NETWORK DEVICE AND LOCAL ACCESS",
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTheme.TextSecondary,
                                            fontSize = 8.sp,
                                            letterSpacing = 0.5.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.toggleAmoledTheme(!amoledEnabled) },
                                    modifier = Modifier.testTag("theme_toggle_btn")
                                ) {
                                    AnimatedContent(
                                        targetState = amoledEnabled,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                        },
                                        label = "theme_icon_crossfade"
                                    ) { targetAmoledState ->
                                        Icon(
                                            imageVector = if (targetAmoledState) Icons.Default.WbSunny else Icons.Default.Brightness2,
                                            contentDescription = "Toggle Theme",
                                            tint = CyberTheme.PrimaryAccent,
                                            modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = CyberTheme.TextPrimary
                            )
                        )
                        HorizontalDivider(
                            color = CyberTheme.PrimaryAccent.copy(alpha = 0.2f),
                            thickness = 1.dp
                        )
                    }
                },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                val navBarBgColor = if (CyberTheme.isAmoled) {
                    Color(0xFF1E1E1E) // Beautiful visible dark grey against true pure black background
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                }
                val navBarBorderColor = if (CyberTheme.isAmoled) {
                    CyberTheme.PrimaryAccent.copy(alpha = 0.25f) // Distinctive border to emphasize rounded corners
                } else {
                    CyberTheme.PrimaryAccent.copy(alpha = 0.15f)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = navBarBgColor,
                    border = BorderStroke(1.dp, navBarBorderColor),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Triple("Sinyal", Icons.Default.SignalCellularAlt, "dashboard_tab"),
                            Triple("Force", Icons.Default.FlashOn, "force_tab"),
                            Triple("Call", Icons.Default.Call, "call_tab"),
                            Triple("Redial", Icons.Default.Autorenew, "redial_tab"),
                            Triple("Speed", Icons.Default.Speed, "speed_tab"),
                            Triple("Firewall", Icons.Default.Shield, "firewall_tab"),
                            Triple("Device", Icons.Default.Memory, "device_tab")
                        )
                        tabs.forEachIndexed { index, (label, icon, tag) ->
                            val isSelected = currentTab == index
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.05f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "tab_scale_$index"
                            )
                            
                            val itemBgColor = if (isSelected) {
                                CyberTheme.PrimaryAccent.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            }
                            val itemBorderColor = if (isSelected) {
                                CyberTheme.PrimaryAccent.copy(alpha = 0.3f)
                            } else {
                                Color.Transparent
                            }
                            val contentColor = if (isSelected) {
                                CyberTheme.PrimaryAccent
                            } else {
                                CyberTheme.TextSecondary.copy(alpha = 0.7f)
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(itemBgColor)
                                    .border(1.dp, itemBorderColor, RoundedCornerShape(14.dp))
                                    .clickable { currentTab = index }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .testTag(tag),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!hasPhonePermission || !hasLocationPermission) {
                // Permissions Required Banner
                PermissionBanner(
                    hasPhone = hasPhonePermission,
                    onRequestPermissions = {
                        val perms = mutableListOf(
                            android.Manifest.permission.READ_PHONE_STATE,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    }
                )
            } else {
                // Core tabs content with fade animations
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "TabContentTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> {
                            DashboardTab(
                                viewModel = viewModel,
                                onInfoClick = {
                                    initialDialogSection = 0
                                    showDetailDialog = true
                                }
                            )
                        }
                        1 -> ForceNetworkTab(
                            viewModel = viewModel,
                            onInfoClick = {
                                initialDialogSection = 1
                                showDetailDialog = true
                            }
                        )
                        2 -> CallScreenTab(
                            viewModel = viewModel,
                            isRoleGranted = isCallScreeningRoleGranted,
                            onRequestRole = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                    roleLauncher.launch(intent)
                                } else {
                                    Toast.makeText(context, "Sistem Android Anda tidak memerlukan role manager ini.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        3 -> com.mandala.net.ui.RedialerScreen(viewModel = redialerViewModel)
                        4 -> SpeedTestTab(viewModel = viewModel)
                        5 -> FirewallTab(
                            viewModel = viewModel,
                            onInfoClick = {
                                initialDialogSection = 2
                                showDetailDialog = true
                            }
                        )
                        6 -> com.mandala.net.ui.HardwareInfoScreen()
                    }
                }
            }
        }
        }
    }

    if (showDetailDialog) {
        MandalaDetailDialog(
            initialSection = initialDialogSection,
            onDismiss = { showDetailDialog = false }
        )
    }
}
}

@Composable
fun PermissionBanner(
    hasPhone: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = CyberTheme.ErrorRed,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Izin Diperlukan",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Untuk mendeteksi kekuatan sinyal seluler secara real-time, sistem memerlukan izin akses perangkat.",
            fontSize = 14.sp,
            color = CyberTheme.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("grant_permissions_btn")
        ) {
            Text("Beri Izin Sekarang", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------
// TAB 1: DASHBOARD (SIGNAL STRENGTH & MONITORING)
// ----------------------------------------------------
@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = CyberTheme.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = CyberTheme.TextPrimary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun SignalHistoryGraph(cellHistory: List<Int>, wifiHistory: List<Int>) {
    val textSecondary = CyberTheme.TextSecondary
    val textPrimary = CyberTheme.TextPrimary
    val accentColor = CyberTheme.PrimaryAccent
    val wifiColor = CyberTheme.WarningOrange

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GRAFIK RIWAYAT KEKUATAN SINYAL (dBm)",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textSecondary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            val cellEntries = cellHistory.mapIndexed { index, dbm -> FloatEntry(index.toFloat(), dbm.toFloat()) }
            val wifiEntries = wifiHistory.mapIndexed { index, dbm -> FloatEntry(index.toFloat(), dbm.toFloat()) }

            if (cellEntries.isNotEmpty() || wifiEntries.isNotEmpty()) {
                val chartEntryModel = entryModelOf(cellEntries, wifiEntries)
                
                Chart(
                    chart = lineChart(
                        lines = listOf(
                            lineSpec(lineColor = accentColor, lineBackgroundShader = null),
                            lineSpec(lineColor = wifiColor, lineBackgroundShader = null)
                        ),
                        axisValuesOverrider = AxisValuesOverrider.fixed(
                            minY = -120f,
                            maxY = -50f
                        )
                    ),
                    model = chartEntryModel,
                    startAxis = rememberStartAxis(
                        label = axisLabelComponent(color = textSecondary),
                        valueFormatter = { value, _ -> "${value.toInt()} dBm" }
                    ),
                    bottomAxis = rememberBottomAxis(
                        label = axisLabelComponent(color = textSecondary)
                    ),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("Menunggu data sinyal...", color = textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).background(accentColor, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Seluler dBm", fontSize = 11.sp, color = textPrimary)
                Spacer(modifier = Modifier.width(20.dp))
                Box(modifier = Modifier.size(10.dp).background(wifiColor, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Wi-Fi dBm", fontSize = 11.sp, color = textPrimary)
            }
        }
    }
}

@Composable
fun DashboardTab(viewModel: MainViewModel, onInfoClick: () -> Unit) {
    val signal by viewModel.signalInfo.collectAsState()
    val scrollState = rememberScrollState()
    var selectedDashboardSection by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .animateContentSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation pills inside the dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sections = mutableListOf(
                "Sinyal Utama",
                "Info Wi-Fi",
                "Menara Seluler"
            )
            if (signal.isDualSim) {
                sections.add(2, "Dual SIM Info")
            }

            sections.forEachIndexed { index, title ->
                Box(
                    modifier = Modifier
                        .background(
                            color = if (selectedDashboardSection == index) CyberTheme.PrimaryAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selectedDashboardSection == index) CyberTheme.PrimaryAccent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedDashboardSection = index }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedDashboardSection == index) MaterialTheme.colorScheme.onPrimary else CyberTheme.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Find the correct section index considering dynamic tabs
        val actualSection = if (signal.isDualSim) {
            selectedDashboardSection
        } else {
            if (selectedDashboardSection >= 2) selectedDashboardSection + 1 else selectedDashboardSection
        }

        AnimatedContent(
            targetState = actualSection,
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                } else {
                    slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
                }
            },
            label = "SignalTabTransition"
        ) { section ->
            when (section) {
                0 -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // SECTION 1: MAIN INFO & SIGNAL STRENGTH GAUGE + GRAPH
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CyberTheme.SignalCardBg),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "KEKUATAN SINYAL AKTIF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyberTheme.TextPrimary.copy(alpha = 0.6f),
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = signal.carrierName.uppercase(Locale.ROOT),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberTheme.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Status: Terhubung",
                                        fontSize = 13.sp,
                                        color = CyberTheme.SuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = CyberTheme.TextPrimary,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = signal.networkType,
                                        color = if (CyberTheme.isDark || CyberTheme.isAmoled) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
        
                        Spacer(modifier = Modifier.height(24.dp))
        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(220.dp).align(Alignment.CenterHorizontally)
                        ) {
                            val animatedSweepAngle by animateFloatAsState(
                                targetValue = (signal.qualityPercent / 100f) * 270f,
                                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                            )
        
                            val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                            Canvas(modifier = Modifier.size(200.dp)) {
                                drawArc(
                                    color = surfaceVariantColor,
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                )
                                
                                val colorSweep = when {
                                    signal.qualityPercent > 75 -> CyberTheme.SuccessGreen
                                    signal.qualityPercent > 45 -> CyberTheme.WarningOrange
                                    else -> CyberTheme.ErrorRed
                                }
                                
                                drawArc(
                                    color = colorSweep,
                                    startAngle = 135f,
                                    sweepAngle = animatedSweepAngle.coerceAtLeast(1f),
                                    useCenter = false,
                                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${signal.dbm}",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberTheme.TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "dBm",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ASU: ${signal.asu}",
                                    fontSize = 13.sp,
                                    color = CyberTheme.PrimaryAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
        
                        Spacer(modifier = Modifier.height(8.dp))
        
                        Text(
                            text = signal.levelDescription,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                signal.qualityPercent > 75 -> CyberTheme.SuccessGreen
                                signal.qualityPercent > 45 -> CyberTheme.WarningOrange
                                else -> CyberTheme.ErrorRed
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
        
                        Spacer(modifier = Modifier.height(16.dp))
        
                        // Real-time Signal Strength dBm Graph
                        SignalHistoryGraph(cellHistory = signal.cellHistory, wifiHistory = signal.wifiHistory)
                    }
                }

            1 -> {
                // SECTION 2: WI-FI DIAGNOSTICS CARD
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "KONEKSI WI-FI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyberTheme.TextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = signal.wifiSsid,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyberTheme.TextPrimary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (signal.isWifiConnected) CyberTheme.SuccessGreen.copy(alpha = 0.15f) else CyberTheme.ErrorRed.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (signal.isWifiConnected) "Terhubung" else "Terputus",
                                    color = if (signal.isWifiConnected) CyberTheme.SuccessGreen else CyberTheme.ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (signal.isWifiConnected) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val wifiBand = when {
                                signal.wifiFrequency in 2400..2500 -> "2.4 GHz"
                                signal.wifiFrequency in 4900..5900 -> "5 GHz"
                                signal.wifiFrequency in 5925..7125 -> "6 GHz"
                                else -> "Unknown Band"
                            }

                            DetailItem(label = "BSSID", value = signal.wifiBssid)
                            DetailItem(label = "Kekuatan Sinyal (RSSI)", value = "${signal.wifiDbm} dBm")
                            DetailItem(label = "Kecepatan Tautan", value = "${signal.wifiLinkSpeed} Mbps")
                            DetailItem(label = "Pita (Band)", value = wifiBand)
                            DetailItem(label = "Frekuensi", value = "${signal.wifiFrequency} MHz")
                            DetailItem(label = "Kanal (Channel)", value = "${signal.wifiChannel}")
                            DetailItem(label = "IP Address", value = signal.wifiIp)
                            DetailItem(label = "MAC Address", value = signal.wifiMac)
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sambungkan perangkat Anda ke jaringan Wi-Fi untuk memantau status kekuatan sinyal nirkabel secara langsung.",
                                fontSize = 12.sp,
                                color = CyberTheme.TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            2 -> {
                // SECTION 3: DUAL SIM CELLULAR DETAILS
                Column(modifier = Modifier.fillMaxWidth()) {
                    // SIM 1 CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "SIM SLOT 1 (PRIMARY)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyberTheme.TextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = signal.sim1Carrier,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberTheme.TextPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (signal.sim1Cid != -1) CyberTheme.SuccessGreen.copy(alpha = 0.15f) else CyberTheme.TextSecondary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (signal.sim1Cid != -1) "Aktif" else "Tidak Aktif",
                                        color = if (signal.sim1Cid != -1) CyberTheme.SuccessGreen else CyberTheme.TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (signal.sim1Cid != -1) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))

                                DetailItem(label = "Kekuatan Sinyal", value = "${signal.sim1Dbm} dBm")
                                DetailItem(label = "Tipe Jaringan", value = signal.sim1NetworkType)
                                DetailItem(label = "Pita Frekuensi (Band)", value = "${signal.sim1Band} (${signal.sim1FreqRange})")
                                DetailItem(label = "Cell ID (CID / CI)", value = "${signal.sim1Cid}")
                                DetailItem(label = "Physical Cell ID (PCI)", value = if (signal.sim1Pci != -1) "${signal.sim1Pci}" else "N/A")
                                DetailItem(label = "eNodeB ID (eNB)", value = if (signal.sim1Enb != -1) "${signal.sim1Enb}" else "N/A")
                                DetailItem(label = "LAC / TAC", value = if (signal.sim1LacTac != -1) "${signal.sim1LacTac}" else "N/A")
                                DetailItem(label = "MCC / MNC", value = "${signal.sim1Mcc} / ${signal.sim1Mnc}")
                                DetailItem(label = "ARFCN / EARFCN", value = if (signal.sim1Arfcn != -1) "${signal.sim1Arfcn}" else "N/A")
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "SIM 1 tidak terdeteksi atau sedang dalam pencarian sinyal aktif.",
                                    fontSize = 12.sp,
                                    color = CyberTheme.TextSecondary
                                )
                            }
                        }
                    }

                    // SIM 2 CARD (Gives identical visual richness for Dual SIMs)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "SIM SLOT 2 (SECONDARY)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyberTheme.TextSecondary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = signal.sim2Carrier,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyberTheme.TextPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (signal.sim2Cid != -1) CyberTheme.SuccessGreen.copy(alpha = 0.15f) else CyberTheme.TextSecondary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (signal.sim2Cid != -1) "Aktif" else "Tidak Aktif",
                                        color = if (signal.sim2Cid != -1) CyberTheme.SuccessGreen else CyberTheme.TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (signal.sim2Cid != -1) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))

                                DetailItem(label = "Kekuatan Sinyal", value = "${signal.sim2Dbm} dBm")
                                DetailItem(label = "Tipe Jaringan", value = signal.sim2NetworkType)
                                DetailItem(label = "Pita Frekuensi (Band)", value = "${signal.sim2Band} (${signal.sim2FreqRange})")
                                DetailItem(label = "Cell ID (CID / CI)", value = "${signal.sim2Cid}")
                                DetailItem(label = "Physical Cell ID (PCI)", value = if (signal.sim2Pci != -1) "${signal.sim2Pci}" else "N/A")
                                DetailItem(label = "eNodeB ID (eNB)", value = if (signal.sim2Enb != -1) "${signal.sim2Enb}" else "N/A")
                                DetailItem(label = "LAC / TAC", value = if (signal.sim2LacTac != -1) "${signal.sim2LacTac}" else "N/A")
                                DetailItem(label = "MCC / MNC", value = "${signal.sim2Mcc} / ${signal.sim2Mnc}")
                                DetailItem(label = "ARFCN / EARFCN", value = if (signal.sim2Arfcn != -1) "${signal.sim2Arfcn}" else "N/A")
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "SIM 2 tidak terdeteksi atau slot dalam keadaan kosong.",
                                    fontSize = 12.sp,
                                    color = CyberTheme.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            3 -> {
                // SECTION 4: CELL TOWER LIST
                val towers = signal.towers
                if (towers.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = CyberTheme.TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tidak Ada Menara Seluler Terdeteksi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTheme.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Izinkan akses izin lokasi presisi tinggi untuk memetakan dan mendeteksi BTS pemancar di sekitar Anda secara langsung.",
                                fontSize = 11.sp,
                                color = CyberTheme.TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "MENARA SELULER TERDEKAT (${towers.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberTheme.TextSecondary,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        towers.forEach { tower ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tower.isServing) CyberTheme.SignalCardBg else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (tower.isServing) CyberTheme.SignalCardBorder else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (tower.isServing) CyberTheme.SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sensors,
                                                contentDescription = null,
                                                tint = if (tower.isServing) CyberTheme.SuccessGreen else CyberTheme.TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = tower.type,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyberTheme.TextPrimary
                                                )
                                                if (tower.isServing) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(CyberTheme.SuccessGreen, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "SERVING",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = if (CyberTheme.isDark) Color.Black else Color.White
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "PCI: ${tower.pci} | LAC/TAC: ${tower.lacTac}",
                                                fontSize = 11.sp,
                                                color = CyberTheme.TextSecondary
                                            )
                                            Text(
                                                text = "CID: ${tower.cid}",
                                                fontSize = 11.sp,
                                                color = CyberTheme.TextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${tower.dbm} dBm",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = when {
                                            tower.dbm > -80 -> CyberTheme.SuccessGreen
                                            tower.dbm > -100 -> CyberTheme.WarningOrange
                                            else -> CyberTheme.ErrorRed
                                        },
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
        }
    }
}

@Composable
fun SignalRangeRow(range: String, desc: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = desc, fontSize = 12.sp, color = CyberTheme.TextPrimary)
        }
        Text(text = range, fontSize = 12.sp, color = CyberTheme.TextSecondary, fontFamily = FontFamily.Monospace)
    }
}

// ----------------------------------------------------
// TAB 2: FORCE NETWORK (4G/5G ONLY SETTING)
// ----------------------------------------------------
@Composable
fun ForceNetworkTab(viewModel: MainViewModel, onInfoClick: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Force Network (Lock 4G/5G)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Kunci jaringan ke mode LTE Only (4G) atau NR Only (5G) untuk koneksi internet super cepat dan stabil.",
            fontSize = 13.sp,
            color = CyberTheme.TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Status Card showing active Preferred Network Type
        val preferredNetworkInfo by viewModel.preferredNetworkType.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.updatePreferredNetworkType()
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tipe Jaringan Prioritas Aktif",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTheme.TextPrimary
                    )
                    
                    IconButton(
                        onClick = { viewModel.updatePreferredNetworkType() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyberTheme.PrimaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Konfigurasi mode jaringan (Preferred Network Type) saat ini dari pengaturan sistem Android Anda:",
                    fontSize = 12.sp,
                    color = CyberTheme.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        preferredNetworkInfo.split("\n").forEach { line ->
                            val isLocked = line.contains("Kunci 4G") || line.contains("Kunci 5G")
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (isLocked) CyberTheme.SuccessGreen else CyberTheme.PrimaryAccent,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = line.removePrefix("• "),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isLocked) CyberTheme.SuccessGreen else CyberTheme.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons to hidden menu
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Metode Penguncian Jaringan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val launched = viewModel.launchPhoneInfoDirectly(context)
                        if (launched) {
                            Toast.makeText(context, "Berhasil membuka Menu Rahasia!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Metode 1 Gagal. ROM HP Anda memblokir akses langsung.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka Menu Rahasia (Radio Info)", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val intents = listOf(
                            Intent(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                            Intent(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS),
                            Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
                            Intent(android.provider.Settings.ACTION_SETTINGS)
                        )
                        var success = false
                        for (intent in intents) {
                            try {
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                                success = true
                                break
                            } catch (e: Exception) {
                                // Try next fallback
                            }
                        }
                        if (success) {
                            Toast.makeText(context, "Membuka Pengaturan Seluler...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Metode 2 Gagal membuka pengaturan.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.SuccessGreen),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka Pengaturan Seluler", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }

            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ----------------------------------------------------
// TAB 3: CALL MANAGEMENT (CALL BLOCKER WITH ROLE)
// ----------------------------------------------------
@Composable
fun CallScreenTab(
    viewModel: MainViewModel,
    isRoleGranted: Boolean,
    onRequestRole: () -> Unit
) {
    val isEnabled by viewModel.isCallBlockingEnabled.collectAsState()
    val blockMode by viewModel.blockMode.collectAsState()
    val blockedLogs by viewModel.blockedCalls.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Call Screening Manager",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Blokir panggilan telepon masuk secara senyap (Silent Block) agar koneksi data 4G/5G tidak terputus saat bermain game atau streaming.",
            fontSize = 13.sp,
            color = CyberTheme.TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // System Role status card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isRoleGranted) MaterialTheme.colorScheme.surface else CyberTheme.ErrorRed.copy(alpha = 0.05f)
            ),
            border = BorderStroke(1.dp, if (isRoleGranted) MaterialTheme.colorScheme.surfaceVariant else CyberTheme.ErrorRed),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Izin Call Screening",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTheme.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRoleGranted) "Sistem mengizinkan penyaringan" else "Izin Khusus Dibutuhkan",
                            fontSize = 12.sp,
                            color = if (isRoleGranted) CyberTheme.SuccessGreen else CyberTheme.ErrorRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (!isRoleGranted) {
                        Button(
                            onClick = onRequestRole,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.ErrorRed),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("Izinkan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            tint = CyberTheme.SuccessGreen,
                            contentDescription = "Active",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle Switch Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aktifkan Pemblokir Panggilan",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTheme.TextPrimary
                        )
                        Text(
                            text = "Intersepsi panggilan secara senyap di latar belakang.",
                            fontSize = 12.sp,
                            color = CyberTheme.TextSecondary
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.toggleCallBlocking(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = CyberTheme.PrimaryAccent,
                            uncheckedThumbColor = CyberTheme.TextSecondary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("block_switch")
                    )
                }

                if (isEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Metode Pemblokiran",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Block Modes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateBlockMode(0) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = blockMode == 0,
                            onClick = { viewModel.updateBlockMode(0) },
                            colors = RadioButtonDefaults.colors(selectedColor = CyberTheme.PrimaryAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Blokir Semua Panggilan", fontSize = 13.sp, color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Koneksi data 100% aman tidak terganggu sama sekali.", fontSize = 11.sp, color = CyberTheme.TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateBlockMode(1) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = blockMode == 1,
                            onClick = { viewModel.updateBlockMode(1) },
                            colors = RadioButtonDefaults.colors(selectedColor = CyberTheme.PrimaryAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Blokir Hanya Nomor Tidak Dikenal/Privat", fontSize = 13.sp, color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Nomor di kontak Anda tetap bisa berdering.", fontSize = 11.sp, color = CyberTheme.TextSecondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // History list Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Log Panggilan Terblokir (${blockedLogs.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTheme.TextPrimary
            )
            if (blockedLogs.isNotEmpty()) {
                Text(
                    text = "Bersihkan",
                    fontSize = 13.sp,
                    color = CyberTheme.ErrorRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { viewModel.clearBlockLogs() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History Log List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (blockedLogs.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = CyberTheme.SuccessGreen.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum ada panggilan terblokir.",
                                fontSize = 13.sp,
                                color = CyberTheme.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(blockedLogs, key = { it.id }) { log ->
                    BlockedCallItem(log = log)
                }
            }
        }
    }
}

@Composable
fun BlockedCallItem(log: BlockedCall) {
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) { formatter.format(Date(log.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CyberTheme.ErrorRed.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        tint = CyberTheme.ErrorRed,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = log.phoneNumber.ifBlank { "Nomor Tersembunyi" },
                        color = CyberTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Ditolak Senyap (Silent Reject)",
                        color = CyberTheme.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = formattedDate,
                fontSize = 12.sp,
                color = CyberTheme.TextSecondary
            )
        }
    }
}

// ----------------------------------------------------
// TAB 4: REAL-TIME SPEED TEST
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestTab(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.speedTestState.collectAsState()
    val selectedServer by viewModel.selectedTestServer.collectAsState()
    val scrollState = rememberScrollState()

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val activity = context as? android.app.Activity
            if (activity == null || !activity.isChangingConfigurations) {
                viewModel.cancelSpeedTest()
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
            text = "Penguji Kecepatan Real-time",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Uji kecepatan download, upload, dan ping secara presisi pada server berkecepatan tinggi.",
            fontSize = 13.sp,
            color = CyberTheme.TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Server selection dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (state is SpeedTestState.Idle || state is SpeedTestState.Completed || state is SpeedTestState.Error) expanded = it }
        ) {
            OutlinedTextField(
                value = "${selectedServer.name} - ${selectedServer.location}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Pilih Server Uji") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedBorderColor = CyberTheme.PrimaryAccent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = CyberTheme.TextPrimary,
                    unfocusedTextColor = CyberTheme.TextPrimary
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                defaultTestServers.forEach { server ->
                    DropdownMenuItem(
                        text = { Text("${server.name} - ${server.location}") },
                        onClick = {
                            viewModel.selectTestServer(server)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Speedometer Gauge implementation based on JurajKusnier's compose-speed-test style
        val currentDisplaySpeed = when (state) {
            is SpeedTestState.DownloadTest -> (state as SpeedTestState.DownloadTest).currentSpeedMbps
            is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).currentSpeedMbps
            is SpeedTestState.Completed -> (state as SpeedTestState.Completed).downloadSpeedMbps
            else -> 0.0
        }

        val animatedSpeed by animateFloatAsState(
            targetValue = currentDisplaySpeed.toFloat(),
            animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
            label = "SpeedAnimation"
        )
        
        // Coerce value to display in speedometer (0..100 scale, but we use a non-linear scale visually or just a max)
        val maxSpeed = 100f
        val gaugeProgress = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
        
        val progressColor = when (state) {
            is SpeedTestState.PingTest -> CyberTheme.PrimaryAccent
            is SpeedTestState.DownloadTest -> CyberTheme.SuccessGreen
            is SpeedTestState.UploadTest -> CyberTheme.WarningOrange
            is SpeedTestState.Completed -> CyberTheme.SuccessGreen
            is SpeedTestState.Error -> CyberTheme.ErrorRed
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(220.dp) // Adjusted height for half circle
        ) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val strokeWidth = 24.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                
                // Draw Track
                drawArc(
                    color = trackColor,
                    startAngle = 140f,
                    sweepAngle = 260f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Draw Progress
                drawArc(
                    color = progressColor,
                    startAngle = 140f,
                    sweepAngle = 260f * gaugeProgress,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                
                // Add some tick marks
                val tickLength = 12.dp.toPx()
                val tickWidth = 2.dp.toPx()
                for (i in 0..10) {
                    val angle = 140f + (260f * i / 10f)
                    val angleRad = Math.toRadians(angle.toDouble())
                    
                    val innerRadius = radius - strokeWidth/2 - 8.dp.toPx()
                    val outerRadius = innerRadius - tickLength
                    
                    val start = androidx.compose.ui.geometry.Offset(
                        x = center.x + (innerRadius * kotlin.math.cos(angleRad)).toFloat(),
                        y = center.y + (innerRadius * kotlin.math.sin(angleRad)).toFloat()
                    )
                    
                    val end = androidx.compose.ui.geometry.Offset(
                        x = center.x + (outerRadius * kotlin.math.cos(angleRad)).toFloat(),
                        y = center.y + (outerRadius * kotlin.math.sin(angleRad)).toFloat()
                    )
                    
                    drawLine(
                        color = tickColor,
                        start = start,
                        end = end,
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Text display inside speedo
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 48.dp)) {
                Text(
                    text = String.format(Locale.US, "%.1f", animatedSpeed),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = CyberTheme.TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Mbps",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.TextSecondary,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status and Progress bar for active test
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val phaseText = when (state) {
                is SpeedTestState.PingTest -> "MENGUKUR PING..."
                is SpeedTestState.DownloadTest -> "MENGUKUR DOWNLOAD..."
                is SpeedTestState.UploadTest -> "MENGUKUR UPLOAD..."
                is SpeedTestState.Completed -> "PENGUJIAN SELESAI"
                is SpeedTestState.Error -> "PENGUJIAN GAGAL"
                else -> "SIAP MENGUJI"
            }
            
            val progressVal = when (state) {
                is SpeedTestState.PingTest -> (state as SpeedTestState.PingTest).progress
                is SpeedTestState.DownloadTest -> (state as SpeedTestState.DownloadTest).progress
                is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).progress
                is SpeedTestState.Completed -> 1f
                else -> 0f
            }

            val animatedProgressVal by animateFloatAsState(
                targetValue = progressVal,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                label = "LinearProgressAnimation"
            )
            
            val indicatorColor = when (state) {
                is SpeedTestState.PingTest -> CyberTheme.PrimaryAccent
                is SpeedTestState.DownloadTest -> CyberTheme.SuccessGreen
                is SpeedTestState.UploadTest -> CyberTheme.WarningOrange
                is SpeedTestState.Completed -> CyberTheme.SuccessGreen
                is SpeedTestState.Error -> CyberTheme.ErrorRed
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = phaseText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor,
                    fontFamily = FontFamily.Monospace
                )
                if (state !is SpeedTestState.Idle && state !is SpeedTestState.Error && state !is SpeedTestState.Completed) {
                    Text(
                        text = "${(animatedProgressVal * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = indicatorColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgressVal },
                color = indicatorColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Card (Ping, Download, Upload values)
        val finalPing = when (state) {
            is SpeedTestState.PingTest -> (state as SpeedTestState.PingTest).currentPingMs
            is SpeedTestState.DownloadTest -> (state as SpeedTestState.DownloadTest).pingMs
            is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).pingMs
            is SpeedTestState.Completed -> (state as SpeedTestState.Completed).pingMs
            else -> 0L
        }

        val finalDownload = when (state) {
            is SpeedTestState.DownloadTest -> (state as SpeedTestState.DownloadTest).currentSpeedMbps
            is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).downloadSpeedMbps
            is SpeedTestState.Completed -> (state as SpeedTestState.Completed).downloadSpeedMbps
            else -> 0.0
        }

        val finalUpload = when (state) {
            is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).currentSpeedMbps
            is SpeedTestState.Completed -> (state as SpeedTestState.Completed).uploadSpeedMbps
            else -> 0.0
        }

        val finalJitter = when (state) {
            is SpeedTestState.PingTest -> (state as SpeedTestState.PingTest).jitterMs
            is SpeedTestState.DownloadTest -> (state as SpeedTestState.DownloadTest).jitterMs
            is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).jitterMs
            is SpeedTestState.Completed -> (state as SpeedTestState.Completed).jitterMs
            else -> 0L
        }

        val finalPacketLoss = when (state) {
            is SpeedTestState.PingTest -> (state as SpeedTestState.PingTest).packetLoss
            is SpeedTestState.DownloadTest -> (state as SpeedTestState.DownloadTest).packetLoss
            is SpeedTestState.UploadTest -> (state as SpeedTestState.UploadTest).packetLoss
            is SpeedTestState.Completed -> (state as SpeedTestState.Completed).packetLoss
            else -> 0f
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SpeedMetric(
                        label = "Ping (Latency)",
                        value = if (finalPing > 0) "$finalPing ms" else "-- ms",
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        iconColor = CyberTheme.PrimaryAccent,
                        isActive = state is SpeedTestState.PingTest
                    )
                    
                    VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(48.dp))
    
                    SpeedMetric(
                        label = "Download",
                        value = if (finalDownload > 0) String.format(Locale.US, "%.1f Mb/s", finalDownload) else "-- Mb/s",
                        icon = Icons.Default.ArrowDownward,
                        iconColor = CyberTheme.SuccessGreen,
                        isActive = state is SpeedTestState.DownloadTest
                    )
    
                    VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(48.dp))
    
                    SpeedMetric(
                        label = "Upload",
                        value = if (finalUpload > 0) String.format(Locale.US, "%.1f Mb/s", finalUpload) else "-- Mb/s",
                        icon = Icons.Default.ArrowUpward,
                        iconColor = CyberTheme.WarningOrange,
                        isActive = state is SpeedTestState.UploadTest
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedMetric(
                        label = "Jitter",
                        value = if (finalPing > 0) "$finalJitter ms" else "-- ms",
                        icon = Icons.Default.Timeline,
                        iconColor = CyberTheme.TextSecondary,
                        isActive = state is SpeedTestState.PingTest
                    )
                    
                    VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(48.dp))
                    
                    SpeedMetric(
                        label = "Packet Loss",
                        value = if (finalPing > 0) String.format(Locale.US, "%.1f %%", finalPacketLoss) else "-- %",
                        icon = Icons.Default.WarningAmber,
                        iconColor = if (finalPacketLoss > 0f) CyberTheme.ErrorRed else CyberTheme.TextSecondary,
                        isActive = state is SpeedTestState.PingTest
                    )
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error log display if any
        if (state is SpeedTestState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (state as SpeedTestState.Error).message,
                color = CyberTheme.ErrorRed,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Start/Stop Action Buttons (Non-scrolling, at bottom)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state is SpeedTestState.Idle || state is SpeedTestState.Completed || state is SpeedTestState.Error) {
                Button(
                    onClick = { viewModel.startSpeedTest() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("start_speed_test_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mulai Uji Jaringan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = { viewModel.cancelSpeedTest() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.ErrorRed),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("cancel_speed_test_btn")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Batalkan Pengujian", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun SpeedMetric(
    label: String,
    value: String,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isActive: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                tint = if (isActive) iconColor else iconColor.copy(alpha = 0.5f),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .then(
                        if (isActive) Modifier.background(
                            iconColor.copy(alpha = 0.1f),
                            CircleShape
                        ) else Modifier
                    )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = CyberTheme.TextSecondary, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ProfessionalShieldIcon(isFirewallEnabled: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val shieldColor = if (isFirewallEnabled) CyberTheme.SuccessGreen else CyberTheme.TextSecondary
    val accentGlow = if (isFirewallEnabled) CyberTheme.SuccessGreen.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(110.dp)
            .padding(8.dp)
    ) {
        // Outer pulsing glow ring
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = glowAlpha
                }
                .background(accentGlow, CircleShape)
                .border(1.5.dp, shieldColor.copy(alpha = 0.4f), CircleShape)
        )

        // Inner solid professional card with shadow/elevation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(70.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isFirewallEnabled) Color(0xFF0C2417) else Color(0xFF1E1E1E),
                            if (isFirewallEnabled) Color(0xFF05120B) else Color(0xFF0F0F0F)
                        )
                    ),
                    shape = CircleShape
                )
                .border(2.dp, shieldColor, CircleShape)
        ) {
            // High-tech decorative radial patterns on canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                // Draw tech lines
                drawCircle(
                    color = shieldColor.copy(alpha = 0.2f),
                    radius = radius * 0.8f,
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = shieldColor.copy(alpha = 0.1f),
                    radius = radius * 0.6f,
                    style = Stroke(width = 1f)
                )
            }

            // Real Shield Icon layered on top of the futuristic circle
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Shield Icon",
                tint = shieldColor,
                modifier = Modifier.size(36.dp)
            )

            // Nested icon or symbol for ultra professionalism
            if (isFirewallEnabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active Check",
                    tint = Color.White,
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.Center)
                        .offset(y = 2.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked State",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.Center)
                        .offset(y = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallTab(viewModel: MainViewModel, onInfoClick: () -> Unit) {
    val context = LocalContext.current
    val isFirewallEnabled by viewModel.isFirewallEnabled.collectAsStateWithLifecycle()
    val isGlobalKillSwitch by viewModel.isGlobalKillSwitch.collectAsStateWithLifecycle()
    val isAmoledTheme by viewModel.isAmoledTheme.collectAsStateWithLifecycle()
    val blockedStats by viewModel.blockedDataStats.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showEfficiencyDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    // Launcher for VPN permission
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.toggleFirewall(context, true)
            Toast.makeText(context, "Firewall VPN Diaktifkan!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Izin VPN ditolak. Tidak dapat mengaktifkan firewall.", Toast.LENGTH_LONG).show()
            viewModel.toggleFirewall(context, false)
        }
    }

    // Launcher for POST_NOTIFICATIONS permission
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = try {
                VpnService.prepare(context.applicationContext)
            } catch (e: SecurityException) {
                null
            }
            if (intent != null) {
                vpnLauncher.launch(intent)
            } else {
                viewModel.toggleFirewall(context, true)
            }
        } else {
            Toast.makeText(context, "Izin notifikasi ditolak, status mungkin tidak muncul di status bar.", Toast.LENGTH_SHORT).show()
            val intent = try {
                VpnService.prepare(context.applicationContext)
            } catch (e: SecurityException) {
                null
            }
            if (intent != null) {
                vpnLauncher.launch(intent)
            } else {
                viewModel.toggleFirewall(context, true)
            }
        }
    }

    // Refresh stats and apps on launch
    LaunchedEffect(Unit) {
        viewModel.refreshBlockedDataStats()
        viewModel.loadInstalledApps()
    }

    // Calculate total savings
    val totalBytesSaved = remember(blockedStats) {
        blockedStats.values.sum()
    }
    val totalSavingsStr = remember(totalBytesSaved) {
        val kb = totalBytesSaved / 1024.0
        if (kb > 1024) {
            String.format("%.2f MB", kb / 1024.0)
        } else {
            String.format("%.2f KB", kb)
        }
    }

    // Filter apps based on query & sort blocked apps to the top
    val filteredApps = remember(installedApps, searchQuery) {
        val baseList = if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        // Group/sort so blocked apps float to the top first, then alphabetical sorting
        baseList.sortedWith(
            compareByDescending<AppInfo> { it.isBlockedWifi || it.isBlockedCellular }
                .thenBy { it.name.lowercase() }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // 1. MASTER SHIELD CARD
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFirewallEnabled) CyberTheme.SignalCardBg else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, if (isFirewallEnabled) CyberTheme.SignalCardBorder else MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().testTag("master_firewall_card")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProfessionalShieldIcon(isFirewallEnabled = isFirewallEnabled)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isFirewallEnabled) "FIREWALL AKTIF" else "FIREWALL NON-AKTIF",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFirewallEnabled) CyberTheme.SuccessGreen else CyberTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isFirewallEnabled) "Melindungi koneksi internet perangkat secara lokal melalui VpnTunnel." else "Aktifkan firewall untuk membatasi akses internet aplikasi pilihan.",
                        fontSize = 13.sp,
                        color = CyberTheme.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = {
                            if (!isFirewallEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = try {
                                        VpnService.prepare(context.applicationContext)
                                    } catch (e: SecurityException) {
                                        null
                                    }
                                    if (intent != null) {
                                        vpnLauncher.launch(intent)
                                    } else {
                                        viewModel.toggleFirewall(context, true)
                                    }
                                }
                            } else {
                                viewModel.toggleFirewall(context, false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFirewallEnabled) CyberTheme.ErrorRed else CyberTheme.PrimaryAccent
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("master_firewall_btn")
                    ) {
                        Icon(
                            imageVector = if (isFirewallEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFirewallEnabled) "Matikan Firewall" else "Aktifkan Firewall",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. CONTROLS CARD (Kill Switch)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Kontrol & Kustomisasi",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Global Lock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = CyberTheme.WarningOrange, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Global Kill Switch", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberTheme.TextPrimary)
                            }
                            Text("Blokir seluruh lalu lintas internet perangkat", fontSize = 11.sp, color = CyberTheme.TextSecondary)
                        }
                        Switch(
                            checked = isGlobalKillSwitch,
                            onCheckedChange = { viewModel.toggleGlobalKillSwitch(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = CyberTheme.WarningOrange
                            ),
                            modifier = Modifier.testTag("kill_switch_toggle")
                        )
                    }
                }
            }
        }

        // 3. ACTION BUTTONS (Dashboard & Log)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showEfficiencyDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.DataUsage, contentDescription = null, tint = CyberTheme.PrimaryAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Efisiensi", color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                
                Button(
                    onClick = { showLogDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, tint = CyberTheme.WarningOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Blokir", color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 5. APP INTERNET ACCESS SELECTION HEADER
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Konfigurasi Firewall Aplikasi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberTheme.TextPrimary
                )
                Text(
                    text = "Ketuk ikon untuk memblokir WiFi atau Seluler",
                    fontSize = 12.sp,
                    color = CyberTheme.TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari aplikasi...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberTheme.TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = CyberTheme.TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = CyberTheme.PrimaryAccent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = CyberTheme.TextPrimary,
                        unfocusedTextColor = CyberTheme.TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("app_search_input")
                )
            }
        }

        // Apps Lazy List
        if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aplikasi tidak ditemukan", color = CyberTheme.TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                AppFirewallRow(app = app, viewModel = viewModel, context = context)
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showEfficiencyDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showEfficiencyDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DataUsage, contentDescription = null, tint = CyberTheme.PrimaryAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Efisiensi Kuota", color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Total Savings Area
                    Text("Total Kebocoran Data Dicegah:", fontSize = 12.sp, color = CyberTheme.TextSecondary)
                    Text(
                        text = totalSavingsStr,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberTheme.PrimaryAccent,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // List Section (takes remaining space)
                    Text("Aktivitas Pemblokiran Aplikasi:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (blockedStats.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Belum ada data pemblokiran", color = CyberTheme.TextSecondary, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(blockedStats.entries.toList().sortedByDescending { it.value }) { (pkg, bytes) ->
                                    val appName = try {
                                        val pm = context.packageManager
                                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                    } catch (e: Exception) {
                                        pkg.substringAfterLast(".")
                                    }
                                    val formattedBytes = if (bytes > 1024) String.format("%.1f KB", bytes / 1024.0) else "$bytes B"
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(appName, fontSize = 12.sp, color = CyberTheme.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                                        Text(formattedBytes, fontSize = 12.sp, color = CyberTheme.ErrorRed, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Bottom Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (blockedStats.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearBlockedStats() }) {
                                Text("Hapus Data", color = CyberTheme.ErrorRed, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        TextButton(onClick = { showEfficiencyDialog = false }) {
                            Text("Tutup", color = CyberTheme.PrimaryAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    
    if (showLogDialog) {
        val blockedAttempts by viewModel.blockedAttempts.collectAsStateWithLifecycle()
        
        androidx.compose.ui.window.Dialog(onDismissRequest = { showLogDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = CyberTheme.WarningOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Log Percobaan Diblokir", color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // List Section (takes remaining space)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (blockedAttempts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Belum Ada Koneksi yang Diblokir", color = CyberTheme.TextSecondary, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(blockedAttempts.take(20)) { attempt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .background(CyberTheme.SignalCardBg, RoundedCornerShape(12.dp))
                                            .border(1.dp, CyberTheme.SignalCardBorder, RoundedCornerShape(12.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(CyberTheme.ErrorRed.copy(alpha = 0.1f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Block, contentDescription = null, tint = CyberTheme.ErrorRed, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = attempt.appName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CyberTheme.TextPrimary,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = attempt.packageName,
                                                fontSize = 9.sp,
                                                color = CyberTheme.TextSecondary,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val formattedTime = try {
                                            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                            sdf.format(java.util.Date(attempt.timestamp))
                                        } catch (e: Exception) {
                                            ""
                                        }
                                        Text(
                                            text = formattedTime,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTheme.TextSecondary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Bottom Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (blockedAttempts.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearBlockedAttempts() }) {
                                Text("Hapus Riwayat", color = CyberTheme.ErrorRed, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        TextButton(onClick = { showLogDialog = false }) {
                            Text("Tutup", color = CyberTheme.PrimaryAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppFirewallRow(app: AppInfo, viewModel: MainViewModel, context: Context) {
    val pm = context.packageManager
    
    // Lazy load real app icon safely as ImageBitmap to avoid heavy AndroidView inside lists
    val iconBitmap = remember(app.packageName) {
        try {
            val drawable = pm.getApplicationIcon(app.packageName)
            val width = drawable.intrinsicWidth.coerceAtLeast(48)
            val height = drawable.intrinsicHeight.coerceAtLeast(48)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real Android app icon view - fully optimized in Compose
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = CyberTheme.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = app.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyberTheme.TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 10.sp,
                        color = CyberTheme.TextSecondary,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // WiFi Block Toggle
                IconButton(
                    onClick = { viewModel.toggleAppWifiBlock(app.packageName, context) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (app.isBlockedWifi) CyberTheme.ErrorRed.copy(alpha = 0.15f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (app.isBlockedWifi) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = "Toggle Wifi Access",
                        tint = if (app.isBlockedWifi) CyberTheme.ErrorRed else CyberTheme.PrimaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Cellular Block Toggle
                IconButton(
                    onClick = { viewModel.toggleAppCellularBlock(app.packageName, context) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (app.isBlockedCellular) CyberTheme.ErrorRed.copy(alpha = 0.15f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (app.isBlockedCellular) Icons.Default.SignalCellularOff else Icons.Default.SignalCellular4Bar,
                        contentDescription = "Toggle Cellular Access",
                        tint = if (app.isBlockedCellular) CyberTheme.ErrorRed else CyberTheme.PrimaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MandalaDetailDialog(initialSection: Int, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemInfoProvider = remember { SystemInfoProvider(context) }
    val systemInfo = remember { systemInfoProvider.getSystemInfo() }
    var selectedSection by remember { mutableStateOf(initialSection) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header / Developer Info with a stunning glowing badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF031634),
                                    Color(0xFF010A15)
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Glowing Profile Photo Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.25f), Color.Transparent)
                                    )
                                )
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.basith),
                                contentDescription = "Developer Profile",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .border(BorderStroke(1.5.dp, Color(0xFF00E5FF)), CircleShape)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "DEVELOPER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF),
                            letterSpacing = 2.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Ahmad Abdul Basith, S.Tr.I.P.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 6f
                                )
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Text(
                            text = "Spesialis Arsitektur Keamanan & Jaringan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF90A4AE),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Symmetrical navigation pills for the dialog sections distributing space evenly
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sections = listOf(
                        "Panduan Sinyal",
                        "Kunci Jaringan",
                        "Privasi VPN",
                        "Info Sistem"
                    )
                    sections.forEachIndexed { index, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (selectedSection == index) CyberTheme.PrimaryAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selectedSection == index) CyberTheme.PrimaryAccent else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedSection = index }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedSection == index) MaterialTheme.colorScheme.onPrimary else CyberTheme.TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dialog Content area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedSection) {
                        0 -> {
                            // Signal Guide
                            Text(
                                text = "Panduan Kualitas Sinyal (dBm)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CyberTheme.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Kualitas sinyal seluler diukur dalam satuan dBm (decibel-milliwatts). Semakin mendekati nol, semakin kuat sinyal Anda.",
                                fontSize = 11.sp,
                                color = CyberTheme.TextSecondary,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            SignalRangeDialogRow(range = "-50 s/d -80 dBm", desc = "Sangat Baik", color = CyberTheme.SuccessGreen)
                            SignalRangeDialogRow(range = "-81 s/d -90 dBm", desc = "Baik", color = CyberTheme.SuccessGreen.copy(alpha = 0.7f))
                            SignalRangeDialogRow(range = "-91 s/d -105 dBm", desc = "Sedang", color = CyberTheme.WarningOrange)
                            SignalRangeDialogRow(range = "-106 s/d -120 dBm", desc = "Lemah", color = CyberTheme.ErrorRed.copy(alpha = 0.7f))
                            SignalRangeDialogRow(range = "-121+ dBm", desc = "Tidak Ada Sinyal", color = CyberTheme.ErrorRed)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ASU (Arbitrary Strength Unit) adalah nilai linear hasil konversi dari dBm. Nilai ASU yang tinggi berbanding lurus dengan kecepatan internet yang lebih stabil dan kencang.",
                                fontSize = 11.sp,
                                color = CyberTheme.TextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                        1 -> {
                            // Network Lock Guide
                            Text(
                                text = "Panduan Fitur Kunci Jaringan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CyberTheme.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mengunci jaringan seluler Anda ke 'LTE Only' (4G) atau 'NR Only' (5G) berguna untuk mencegah HP mendownload data pada jaringan 2G/3G yang lambat.",
                                fontSize = 11.sp,
                                color = CyberTheme.TextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            BulletPoint("Batasan Android OS", "Di Android 10+, aplikasi non-sistem dilarang memicu pergantian jaringan secara otomatis demi keamanan panggilan darurat. Oleh karena itu, Anda harus mengaturnya secara manual di dalam menu RadioInfo.")
                            Spacer(modifier = Modifier.height(8.dp))
                            BulletPoint("Khusus Pengguna Samsung", "Samsung memblokir kode dial standar. Jika tombol Buka Menu tidak memicu RadioInfo, silakan masuk ke dialer bawaan Anda dan ketik kode khusus *#2263# atau *#0011#.")
                            Spacer(modifier = Modifier.height(8.dp))
                            BulletPoint("Catatan Panggilan Telepon", "Koneksi LTE/NR Only di HP tanpa VoLTE (Voice over LTE) aktif akan mematikan panggilan telepon biasa. Kembalikan ke otomatis jika Anda selesai.")
                        }
                        2 -> {
                            // VPN & Privacy Guide
                            Text(
                                text = "Privasi VPN & Kepatuhan Google",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CyberTheme.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "MANDALA dirancang dengan filosofi keamanan tanpa kompromi. Seluruh fitur firewall bekerja secara offline.",
                                fontSize = 11.sp,
                                color = CyberTheme.TextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            BulletPoint("100% Offline Lokal", "Aplikasi membuat koneksi VPN Tunnel lokal di perangkat Anda. Lalu lintas paket data disaring langsung di tingkat kernel perangkat.")
                            Spacer(modifier = Modifier.height(8.dp))
                            BulletPoint("Tanpa Server Eksternal", "Kami tidak memiliki server VPN eksternal. Tidak ada data internet Anda yang dikirim keluar atau didekripsi.")
                            Spacer(modifier = Modifier.height(8.dp))
                            BulletPoint("Sesuai Regulasi Play Protect", "Memenuhi kebijakan VPN Google Play Store karena tidak melakukan aktivitas pengumpulan data rahasia maupun peretasan paket.")
                        }
                        3 -> {
                            // System Info Detail
                            Text(
                                text = "Informasi Sistem Perangkat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = CyberTheme.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Metadata spesifikasi hardware dan jaringan aktif perangkat Anda saat ini.",
                                fontSize = 11.sp,
                                color = CyberTheme.TextSecondary,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            SystemInfoRow(label = "Model Perangkat", value = systemInfo.deviceName)
                            SystemInfoRow(label = "Versi Android", value = "Android ${systemInfo.osVersion} (API ${systemInfo.sdkInt})")
                            SystemInfoRow(label = "Patch Keamanan", value = systemInfo.securityPatch)
                            SystemInfoRow(label = "Operator Seluler", value = systemInfo.networkOperator)
                            SystemInfoRow(label = "Nama Operator SIM", value = systemInfo.simOperator)
                            SystemInfoRow(label = "Jenis Jaringan", value = systemInfo.networkType)
                            SystemInfoRow(label = "Arsitektur CPU", value = systemInfo.cpuArch)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Tutup", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = CyberTheme.TextSecondary
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun BulletPoint(title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(CyberTheme.PrimaryAccent, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberTheme.TextPrimary)
            Text(body, fontSize = 11.sp, color = CyberTheme.TextSecondary, lineHeight = 15.sp)
        }
    }
}

@Composable
fun SignalRangeDialogRow(range: String, desc: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = desc, fontSize = 11.sp, color = CyberTheme.TextPrimary)
        }
        Text(text = range, fontSize = 11.sp, color = CyberTheme.TextSecondary, fontFamily = FontFamily.Monospace)
    }
}

