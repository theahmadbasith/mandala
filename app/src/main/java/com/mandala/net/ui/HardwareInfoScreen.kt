package com.mandala.net.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mandala.net.CyberTheme
import com.mandala.net.HardwareInfoViewModel
import com.mandala.net.HardwareUtils
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareInfoScreen(viewModel: HardwareInfoViewModel = viewModel()) {
    val context = LocalContext.current
    val tabs = listOf("SoC", "Device", "System", "Battery", "Thermal", "Sensors")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setForeground(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setForeground(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startMonitoring(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = CyberTheme.PrimaryAccent,
            edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { 
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) { page ->
            when (page) {
                0 -> SocTab(viewModel)
                1 -> DeviceTab(viewModel)
                2 -> SystemTab(viewModel)
                3 -> BatteryTab(viewModel)
                4 -> ThermalTab(viewModel)
                5 -> SensorsTab(viewModel)
            }
        }
    }
}

@Composable
fun ThermalTab(viewModel: HardwareInfoViewModel) {
    val thermalZones by viewModel.thermalZones.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            InfoSection(title = "Thermal Zones (Real-time)") {
                if (thermalZones.isEmpty()) {
                    androidx.compose.material3.Text(
                        text = "Mengambil data thermal...",
                        fontSize = 14.sp,
                        color = CyberTheme.TextSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    thermalZones.forEach { zone ->
                        val color = when {
                            zone.temp > 45f -> CyberTheme.ErrorRed
                            zone.temp > 35f -> CyberTheme.WarningOrange
                            else -> CyberTheme.SuccessGreen
                        }
                        InfoRow(zone.name, "%.1f °C".format(zone.temp), valueColor = color)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = CyberTheme.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = CyberTheme.TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 1.dp)
}

@Composable
fun InfoSection(title: String, defaultExpanded: Boolean = true, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(bottom = if (expanded) 8.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 16.sp, color = CyberTheme.PrimaryAccent, fontWeight = FontWeight.Black)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = CyberTheme.PrimaryAccent
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column {
                    content()
                }
            }
        }
    }
}

@Composable
fun CpuHeroCard(cpuInfo: HardwareUtils.CpuInfo) {
    val (primaryGlow, secondaryGlow) = when (cpuInfo.socVendor) {
        "Qualcomm" -> Pair(Color(0xFFFF5722), Color(0xFFFF9800)) // Snapdragon Orange/Red
        "MediaTek" -> Pair(Color(0xFF00B0FF), Color(0xFF00E5FF)) // MediaTek Electric Cyan
        "Samsung" -> Pair(Color(0xFF2979FF), Color(0xFFE040FB)) // Exynos Blue/Purple
        "Google" -> Pair(Color(0xFF4CAF50), Color(0xFFFFEB3B)) // Tensor Green/Yellow
        "Unisoc" -> Pair(Color(0xFFE040FB), Color(0xFFFF5252)) // Unisoc Magenta/Pink
        "Intel" -> Pair(Color(0xFF00C853), Color(0xFF00E5FF)) // Intel Green/Blue
        else -> Pair(CyberTheme.PrimaryAccent, Color(0xFF00E5FF)) // Cyber Blue/Cyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        border = BorderStroke(1.5.dp, primaryGlow.copy(alpha = 0.5f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val dotRadius = 1.5.dp.toPx()
                val spacing = 20.dp.toPx()
                for (x in 0..size.width.toInt() step spacing.toInt()) {
                    for (y in 0..size.height.toInt() step spacing.toInt()) {
                        drawCircle(
                            color = primaryGlow.copy(alpha = 0.05f),
                            radius = dotRadius,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 2.dp.toPx()
                        val sizePx = size.width
                        val pinLen = 6.dp.toPx()

                        drawRect(
                            color = primaryGlow.copy(alpha = 0.15f),
                            size = Size(sizePx * 0.7f, sizePx * 0.7f),
                            topLeft = Offset(sizePx * 0.15f, sizePx * 0.15f)
                        )
                        
                        drawRect(
                            color = primaryGlow,
                            size = Size(sizePx * 0.7f, sizePx * 0.7f),
                            topLeft = Offset(sizePx * 0.15f, sizePx * 0.15f),
                            style = Stroke(width = strokeWidth)
                        )

                        val numPins = 4
                        val pinSpacing = (sizePx * 0.7f) / (numPins + 1)
                        for (i in 1..numPins) {
                            val offset = sizePx * 0.15f + i * pinSpacing
                            
                            drawLine(
                                color = secondaryGlow,
                                start = Offset(offset, sizePx * 0.15f),
                                end = Offset(offset, sizePx * 0.15f - pinLen),
                                strokeWidth = strokeWidth
                            )
                            drawLine(
                                color = secondaryGlow,
                                start = Offset(offset, sizePx * 0.85f),
                                end = Offset(offset, sizePx * 0.85f + pinLen),
                                strokeWidth = strokeWidth
                            )
                            drawLine(
                                color = secondaryGlow,
                                start = Offset(sizePx * 0.15f, offset),
                                end = Offset(sizePx * 0.15f - pinLen, offset),
                                strokeWidth = strokeWidth
                            )
                            drawLine(
                                color = secondaryGlow,
                                start = Offset(sizePx * 0.85f, offset),
                                end = Offset(sizePx * 0.85f + pinLen, offset),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                    
                    Text(
                        text = when (cpuInfo.socVendor) {
                            "Qualcomm" -> "SNAP"
                            "MediaTek" -> "MTK"
                            "Samsung" -> "EXY"
                            "Google" -> "G"
                            "Unisoc" -> "UNI"
                            "Intel" -> "INTC"
                            else -> "CPU"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryGlow
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = primaryGlow.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = cpuInfo.socVendor.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = primaryGlow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${cpuInfo.cores} Cores",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTheme.TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = cpuInfo.socName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberTheme.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Max Speed: ${cpuInfo.maxFrequency}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryGlow
                    )
                }
            }
        }
    }
}

@Composable
fun SocTab(viewModel: HardwareInfoViewModel) {
    val cpuInfo by viewModel.cpuInfo.collectAsState()
    val gpuInfo by viewModel.gpuInfo.collectAsState()
    val clockSpeeds by viewModel.liveClockSpeeds.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            CpuHeroCard(cpuInfo = cpuInfo)
        }
        item {
            InfoSection(title = "CPU Architecture") {
                InfoRow("Chipset Model", cpuInfo.socName)
                InfoRow("Chipset Vendor", cpuInfo.socVendor)
                InfoRow("Cores", cpuInfo.cores.toString())
                InfoRow("Architecture", cpuInfo.architecture)
                if (cpuInfo.topology.contains("\n")) {
                    cpuInfo.topology.split("\n").forEachIndexed { idx, line ->
                        InfoRow(if (idx == 0) "Topology" else "", line)
                    }
                } else {
                    InfoRow("Topology", cpuInfo.topology)
                }
                InfoRow("Revision", cpuInfo.revision)
                InfoRow("Process", cpuInfo.process)
                InfoRow("Clock Speed Range", "${cpuInfo.minFrequency} - ${cpuInfo.maxFrequency}")
                InfoRow("Scaling Governor", cpuInfo.governor)
                InfoRow("Supported ABIs", cpuInfo.supportedAbis.joinToString(", "))
            }
        }
        item {
            InfoSection(title = "Live Clock Speed") {
                clockSpeeds.forEach { (core, speed) ->
                    InfoRow("Core $core", speed)
                }
            }
        }
        item {
            InfoSection(title = "GPU Info") {
                InfoRow("Vendor", gpuInfo.vendor)
                InfoRow("Renderer", gpuInfo.renderer)
                InfoRow("GPU Load", gpuInfo.load)
            }
        }
    }
}

@Composable
fun DeviceTab(viewModel: HardwareInfoViewModel) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            InfoSection(title = "Basic Info") {
                InfoRow("Model", deviceInfo.model)
                InfoRow("Manufacturer", deviceInfo.manufacturer)
                InfoRow("Brand", deviceInfo.brand)
                InfoRow("Board", deviceInfo.board)
                InfoRow("Hardware", deviceInfo.hardware)
            }
        }
        item {
            InfoSection(title = "Memory (RAM)") {
                InfoRow("Total RAM", deviceInfo.totalRam)
                InfoRow("Available RAM", deviceInfo.availableRam)
            }
        }
        item {
            InfoSection(title = "Internal Storage") {
                InfoRow("Total Space", deviceInfo.totalStorage)
                InfoRow("Available Space", deviceInfo.availableStorage)
            }
        }
        item {
            InfoSection(title = "Display") {
                InfoRow("Resolution", deviceInfo.resolution)
                InfoRow("Density", deviceInfo.density)
            }
        }
    }
}

@Composable
fun SystemTab(viewModel: HardwareInfoViewModel) {
    val systemInfo by viewModel.systemInfo.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            InfoSection(title = "Operating System") {
                InfoRow("Android Version", systemInfo.androidVersion)
                InfoRow("API Level", systemInfo.apiLevel)
                InfoRow("Security Patch Level", systemInfo.securityPatch)
                InfoRow("Bootloader", systemInfo.bootloader)
                InfoRow("Build ID", systemInfo.buildId)
                InfoRow("Java VM", systemInfo.javaVm)
                InfoRow("OpenGL ES", systemInfo.openGlEs)
                InfoRow("Kernel Architecture", systemInfo.kernelArch)
                InfoRow("Kernel Version", systemInfo.kernelVersion)
                val rootColor = if (systemInfo.isRooted) CyberTheme.WarningOrange else CyberTheme.SuccessGreen
                InfoRow("Root Access", if (systemInfo.isRooted) "Yes" else "No", valueColor = rootColor)
                InfoRow("Google Play Services", systemInfo.googlePlayServices)
                InfoRow("System Uptime", systemInfo.uptime)
            }
        }
    }
}

@Composable
fun BatteryTab(viewModel: HardwareInfoViewModel) {
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    
    val levelColor = when {
        batteryInfo.level < 15 -> CyberTheme.ErrorRed
        batteryInfo.level < 30 -> CyberTheme.WarningOrange
        else -> CyberTheme.SuccessGreen
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            InfoSection(title = "Battery Status") {
                InfoRow("Health", batteryInfo.health)
                InfoRow("Level", "${batteryInfo.level}%", valueColor = levelColor)
                InfoRow("Power Source", batteryInfo.powerSource)
                InfoRow("Status", batteryInfo.status)
                InfoRow("Technology", batteryInfo.technology)
                InfoRow("Voltage", "${batteryInfo.voltage} mV")
            }
        }
    }
}

@Composable
fun SensorsTab(viewModel: HardwareInfoViewModel) {
    val sensors by viewModel.sensors.collectAsState()
    val liveAccel by viewModel.liveAccelerometer.collectAsState()
    val liveGyro by viewModel.liveGyroscope.collectAsState()
    
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            InfoSection(title = "Live Sensor Data") {
                InfoRow("Accelerometer (m/s²)", "X: ${"%.2f".format(liveAccel[0])}, Y: ${"%.2f".format(liveAccel[1])}, Z: ${"%.2f".format(liveAccel[2])}")
                InfoRow("Gyroscope (rad/s)", "X: ${"%.2f".format(liveGyro[0])}, Y: ${"%.2f".format(liveGyro[1])}, Z: ${"%.2f".format(liveGyro[2])}")
            }
        }
        item {
            InfoSection(title = "Available Sensors (${sensors.size})") {
                sensors.forEach { sensor ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(text = sensor.name, fontSize = 14.sp, color = CyberTheme.TextPrimary, fontWeight = FontWeight.Bold)
                        Text(text = "${sensor.vendor} - ${sensor.power} mA", fontSize = 12.sp, color = CyberTheme.TextSecondary)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
