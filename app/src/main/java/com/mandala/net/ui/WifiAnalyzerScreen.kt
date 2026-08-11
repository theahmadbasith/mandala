@file:Suppress("DEPRECATION")
package com.mandala.net.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandala.net.CyberTheme
import com.mandala.net.WifiApInfo
import com.mandala.net.ChannelRating
import com.mandala.net.WifiAnalyzerViewModel
import androidx.compose.ui.graphics.drawscope.Fill
import java.util.*
import kotlin.math.abs

@Composable
fun WifiAnalyzerScreen(viewModel: WifiAnalyzerViewModel) {
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val connectedDetails by viewModel.connectedWifiDetails.collectAsStateWithLifecycle()
    val ratings24 by viewModel.channelRatings24.collectAsStateWithLifecycle()
    val ratings50 by viewModel.channelRatings50.collectAsStateWithLifecycle()
    val signalHistory by viewModel.signalHistory.collectAsStateWithLifecycle()
    val activeBssids by viewModel.activeHistoryBssids.collectAsStateWithLifecycle()

    val subTab by viewModel.subTab.collectAsStateWithLifecycle()

    val cardBg = CyberTheme.SignalCardBg
    val cardBorder = CyberTheme.SignalCardBorder

    Row(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Left: Sidebar Navigation Rail
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .background(cardBg.copy(alpha = 0.5f))
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val subTabs = listOf(
                Icons.Default.List to "Daftar AP",
                Icons.Default.Star to "Saluran",
                Icons.Default.BarChart to "Grafik Saluran",
                Icons.Default.Timeline to "Grafik Waktu",
                Icons.Default.Info to "Detail",
                Icons.Default.Devices to "Perangkat"
            )
            subTabs.forEachIndexed { index, (icon, label) ->
                val isSelected = subTab == index
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                // Animated Color Transitions
                val bgAlpha by animateFloatAsState(
                    targetValue = when {
                        isSelected -> 0.16f
                        isHovered -> 0.08f
                        else -> 0f
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "bgAlpha"
                )

                val borderAlpha by animateFloatAsState(
                    targetValue = when {
                        isSelected -> 0.6f
                        isHovered -> 0.3f
                        else -> 0f
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "borderAlpha"
                )

                val iconScale by animateFloatAsState(
                    targetValue = when {
                        isSelected -> 1.15f
                        isHovered -> 1.08f
                        else -> 1.0f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconScale"
                )

                val iconColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> CyberTheme.PrimaryAccent
                        isHovered -> CyberTheme.PrimaryAccent.copy(alpha = 0.8f)
                        else -> CyberTheme.TextSecondary.copy(alpha = 0.6f)
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "iconColor"
                )

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberTheme.PrimaryAccent.copy(alpha = bgAlpha))
                        .border(
                            width = 1.dp,
                            color = CyberTheme.PrimaryAccent.copy(alpha = borderAlpha),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null, // Custom visual states are smoother
                            onClick = { viewModel.setSubTab(index) }
                        )
                        .testTag("wifi_subtab_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconColor,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(
                                scaleX = iconScale,
                                scaleY = iconScale
                            )
                    )

                    // Cyber-style Glow/Neon Tooltip popup on Hover!
                    if (isHovered) {
                        Popup(
                            alignment = Alignment.CenterEnd,
                            offset = IntOffset(
                                x = with(LocalDensity.current) { 12.dp.roundToPx() },
                                y = 0
                            ),
                            properties = PopupProperties(
                                focusable = false,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = CyberTheme.SignalCardBg.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = CyberTheme.PrimaryAccent.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = CyberTheme.PrimaryAccent,
                                                shape = RoundedCornerShape(3.dp)
                                            )
                                    )
                                    Text(
                                        text = label,
                                        color = CyberTheme.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(
            color = cardBorder,
            thickness = 1.dp,
            modifier = Modifier.fillMaxHeight()
        )

        // Right: Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp)
        ) {
            // Action controls (Refresh)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status Wi-Fi",
                        color = CyberTheme.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (connectedDetails.isNotEmpty()) "${connectedDetails["SSID"]}" else "Mencari jaringan...",
                        color = if (connectedDetails.isNotEmpty()) CyberTheme.SuccessGreen else CyberTheme.PrimaryAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = { viewModel.triggerManualScan() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberTheme.PrimaryAccent.copy(alpha = 0.12f),
                        contentColor = CyberTheme.PrimaryAccent
                    ),
                    border = BorderStroke(1.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("wifi_scan_refresh")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pindai", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(
                color = CyberTheme.PrimaryAccent.copy(alpha = 0.15f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Sub-Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = subTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "WifiSubTabTransition"
                ) { targetSubTab ->
                    when (targetSubTab) {
                        0 -> ApListScreen(scanResults)
                        1 -> ChannelRatingScreen(ratings24, ratings50)
                        2 -> ChannelGraphScreen(scanResults)
                        3 -> TimeGraphScreen(scanResults, signalHistory, activeBssids)
                        4 -> ConnectedWifiDetailScreen(connectedDetails)
                        5 -> ConnectedDevicesScreen(viewModel)
                    }
                }
            }
        }
    }
}

// 1. Access Point List Screen
@Composable
fun ApListScreen(scanResults: List<WifiApInfo>) {
    if (scanResults.isEmpty()) {
        EmptyWifiState("Tidak ada jaringan Wi-Fi terdeteksi.\nPastikan izin lokasi dan Wi-Fi Anda telah aktif.")
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedBand by remember { mutableStateOf("Semua") } // "Semua", "2.4 GHz", "5 GHz", "6 GHz"
    var sortBy by remember { mutableStateOf(0) } // 0: Sinyal, 1: SSID, 2: Saluran

    val filteredList = remember(scanResults, searchQuery, selectedBand, sortBy) {
        var list = scanResults.filter { ap ->
            (selectedBand == "Semua" || ap.band == selectedBand) &&
                    (searchQuery.isEmpty() || ap.ssid.contains(searchQuery, ignoreCase = true) || ap.bssid.contains(searchQuery, ignoreCase = true))
        }
        list = when (sortBy) {
            0 -> list.sortedByDescending { it.rssi }
            1 -> list.sortedBy { it.ssid.lowercase() }
            2 -> list.sortedBy { it.channel }
            else -> list
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar styled beautifully matching CyberTheme
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari SSID atau MAC...", fontSize = 11.sp, color = CyberTheme.TextSecondary.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = CyberTheme.PrimaryAccent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(16.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = CyberTheme.TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CyberTheme.SignalCardBg,
                unfocusedContainerColor = CyberTheme.SignalCardBg,
                focusedBorderColor = CyberTheme.PrimaryAccent,
                unfocusedBorderColor = CyberTheme.SignalCardBorder,
                focusedTextColor = CyberTheme.TextPrimary,
                unfocusedTextColor = CyberTheme.TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .height(42.dp)
        )

        // Filter & Sort chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pita:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
            
            val bands = listOf("Semua", "2.4 GHz", "5 GHz")
            bands.forEach { band ->
                val isSelected = selectedBand == band
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) CyberTheme.PrimaryAccent.copy(alpha = 0.16f) else CyberTheme.SignalCardBg)
                        .border(1.dp, if (isSelected) CyberTheme.PrimaryAccent else CyberTheme.SignalCardBorder, RoundedCornerShape(6.dp))
                        .clickable { selectedBand = band }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(band, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CyberTheme.PrimaryAccent else CyberTheme.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            VerticalDivider(modifier = Modifier.height(12.dp), color = CyberTheme.SignalCardBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.width(4.dp))

            Text("Urut:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)

            val sorts = listOf("Sinyal", "Nama", "Kanal")
            sorts.forEachIndexed { idx, label ->
                val isSelected = sortBy == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) CyberTheme.WarningOrange.copy(alpha = 0.16f) else CyberTheme.SignalCardBg)
                        .border(1.dp, if (isSelected) CyberTheme.WarningOrange else CyberTheme.SignalCardBorder, RoundedCornerShape(6.dp))
                        .clickable { sortBy = idx }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSelected) CyberTheme.WarningOrange else CyberTheme.TextSecondary)
                }
            }
        }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Tidak ada jaringan yang sesuai filter.", color = CyberTheme.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(filteredList, key = { it.bssid }) { ap ->
                    ApRowItem(ap)
                }
            }
        }
    }
}

@Composable
fun ApRowItem(ap: WifiApInfo) {
    val signalColor = when {
        ap.rssi >= -50 -> CyberTheme.SuccessGreen
        ap.rssi >= -65 -> CyberTheme.SuccessGreen.copy(alpha = 0.85f)
        ap.rssi >= -75 -> CyberTheme.WarningOrange
        else -> CyberTheme.ErrorRed
    }

    val cardBg = if (ap.isConnected) {
        CyberTheme.PrimaryAccent.copy(alpha = 0.08f)
    } else {
        CyberTheme.SignalCardBg
    }

    val cardBorderColor = if (ap.isConnected) {
        CyberTheme.PrimaryAccent
    } else {
        CyberTheme.SignalCardBorder
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal Strength and Level Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(signalColor.copy(alpha = 0.12f))
                    .border(1.dp, signalColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${ap.rssi}",
                        color = signalColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "dBm",
                        color = signalColor.copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // SSID / BSSID / Channel / Dist details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = ap.ssid,
                        color = CyberTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (ap.isConnected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberTheme.SuccessGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Tersambung",
                                color = CyberTheme.SuccessGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ap.bssid,
                        color = CyberTheme.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "•",
                        color = CyberTheme.TextSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = ap.vendor,
                        color = CyberTheme.PrimaryAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Detail Badges Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Band Badge
                    BadgeItem(
                        text = ap.band,
                        bgColor = CyberTheme.PrimaryAccent.copy(alpha = 0.1f),
                        textColor = CyberTheme.PrimaryAccent
                    )
                    // Channel Badge
                    BadgeItem(
                        text = "Ch ${ap.channel}",
                        bgColor = CyberTheme.WarningOrange.copy(alpha = 0.1f),
                        textColor = CyberTheme.WarningOrange
                    )
                    // Dist Badge
                    val distanceText = if (ap.distance < 1.0) "Sangat Dekat" else String.format(Locale.US, "≈%.1fm", ap.distance)
                    BadgeItem(
                        text = distanceText,
                        bgColor = CyberTheme.TextSecondary.copy(alpha = 0.1f),
                        textColor = CyberTheme.TextSecondary
                    )
                }
            }

            // Right Chevron or Lock Indicator
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val isSecured = ap.capabilities.uppercase().contains("WEP") || 
                                ap.capabilities.uppercase().contains("WPA") ||
                                ap.capabilities.uppercase().contains("EAP")
                Icon(
                    imageVector = if (isSecured) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isSecured) "Sandi" else "Terbuka",
                    tint = if (isSecured) CyberTheme.TextSecondary.copy(alpha = 0.5f) else CyberTheme.SuccessGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BadgeItem(text: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// 2. Channel Rating Screen
@Composable
fun ChannelRatingScreen(ratings24: List<ChannelRating>, ratings50: List<ChannelRating>) {
    var bandSelect by remember { mutableStateOf(0) } // 0: 2.4 GHz, 1: 5 GHz

    Column(modifier = Modifier.fillMaxSize()) {
        // Toggle Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Button(
                onClick = { bandSelect = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (bandSelect == 0) CyberTheme.PrimaryAccent else CyberTheme.SignalCardBg,
                    contentColor = if (bandSelect == 0) Color.White else CyberTheme.TextSecondary
                ),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Text("Pita 2.4 GHz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { bandSelect = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (bandSelect == 1) CyberTheme.PrimaryAccent else CyberTheme.SignalCardBg,
                    contentColor = if (bandSelect == 1) Color.White else CyberTheme.TextSecondary
                ),
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Text("Pita 5.0 GHz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        val activeRatings = if (bandSelect == 0) ratings24 else ratings50

        if (activeRatings.isEmpty()) {
            EmptyWifiState("Tidak ada data saluran. Lakukan pemindaian terlebih dahulu.")
            return
        }

        // List of all channel ratings
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(activeRatings) { item ->
                ChannelRatingRow(item)
            }
        }
    }
}

@Composable
fun ChannelRatingRow(item: ChannelRating) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberTheme.SignalCardBg,
        border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Channel Hexagon/Box Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberTheme.PrimaryAccent.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = "${item.channel}",
                        color = CyberTheme.PrimaryAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        "Saluran ${item.channel}",
                        color = CyberTheme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${item.apCount} Perangkat Overlap",
                        color = CyberTheme.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Star Rating indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val fullStars = item.rating.toInt()
                val halfStar = (item.rating - fullStars) >= 0.5f

                for (i in 1..5) {
                    val iconColor = if (i <= fullStars) {
                        CyberTheme.WarningOrange
                    } else if (i == fullStars + 1 && halfStar) {
                        CyberTheme.WarningOrange.copy(alpha = 0.6f)
                    } else {
                        CyberTheme.TextSecondary.copy(alpha = 0.2f)
                    }
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = String.format(Locale.US, "%.1f", item.rating),
                    color = CyberTheme.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// 3. Channel Graph Screen
@Composable
fun ChannelGraphScreen(scanResults: List<WifiApInfo>) {
    var graphBandSelect by remember { mutableStateOf(0) } // 0: 2.4 GHz, 1: 5 GHz

    Column(modifier = Modifier.fillMaxSize()) {
        // Toggle Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Button(
                onClick = { graphBandSelect = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (graphBandSelect == 0) CyberTheme.PrimaryAccent else CyberTheme.SignalCardBg,
                    contentColor = if (graphBandSelect == 0) Color.White else CyberTheme.TextSecondary
                ),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Text("Pita 2.4 GHz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { graphBandSelect = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (graphBandSelect == 1) CyberTheme.PrimaryAccent else CyberTheme.SignalCardBg,
                    contentColor = if (graphBandSelect == 1) Color.White else CyberTheme.TextSecondary
                ),
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Text("Pita 5.0 GHz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        val filteredAps = scanResults.filter {
            if (graphBandSelect == 0) it.band == "2.4 GHz" else it.band == "5 GHz"
        }

        val gridLineColor = CyberTheme.TextSecondary.copy(alpha = 0.15f)
        val textCol = CyberTheme.TextSecondary

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CyberTheme.SignalCardBg)
                .border(1.dp, CyberTheme.SignalCardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Margin calculations
                val leftMargin = 40.dp.toPx()
                val bottomMargin = 30.dp.toPx()
                val rightMargin = 10.dp.toPx()
                val topMargin = 15.dp.toPx()

                val plotWidth = width - leftMargin - rightMargin
                val plotHeight = height - topMargin - bottomMargin

                // Draw Y-axis grid (RSSI: -100 to -30 dBm)
                val rssiMin = -100f
                val rssiMax = -30f
                val rssiRange = rssiMax - rssiMin

                val yStep = plotHeight / 7f
                for (i in 0..7) {
                    val y = topMargin + (yStep * i)
                    val dbmValue = (rssiMax - (i * (rssiRange / 7f))).toInt()

                    // Horizontal line
                    drawLine(
                        color = gridLineColor,
                        start = Offset(leftMargin, y),
                        end = Offset(width - rightMargin, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Text label
                    drawContext.canvas.nativeCanvas.drawText(
                        "$dbmValue",
                        10.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            setColor(textCol.toArgb())
                            textSize = 8.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }

                // Channel Scale mapping
                val channels = if (graphBandSelect == 0) {
                    (1..14).toList()
                } else {
                    listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
                }

                val chanCount = channels.size
                val xStep = plotWidth / (chanCount + 1f)

                // Draw Vertical Channel grid
                for (idx in channels.indices) {
                    val chan = channels[idx]
                    val x = leftMargin + (xStep * (idx + 1f))

                    drawLine(
                        color = gridLineColor,
                        start = Offset(x, topMargin),
                        end = Offset(x, height - bottomMargin),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw X-axis channel numbers
                    drawContext.canvas.nativeCanvas.drawText(
                        "$chan",
                        x - 4.dp.toPx(),
                        height - 8.dp.toPx(),
                        android.graphics.Paint().apply {
                            setColor(textCol.toArgb())
                            textSize = 8.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }

                // Draw AP curves (Parabolas)
                // Distinct colors for APs
                val apColors = listOf(
                    Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
                    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF4DB6AC),
                    Color(0xFFD4E157), Color(0xFF90A4AE), Color(0xFFFF8A65)
                )

                filteredAps.forEachIndexed { apIdx, ap ->
                    val color = apColors[apIdx % apColors.size]

                    // Find channel index
                    val chanIdx = channels.indexOf(ap.channel)
                    if (chanIdx != -1) {
                        val centerX = leftMargin + (xStep * (chanIdx + 1f))
                        val rssiValue = ap.rssi.coerceIn(-100, -30).toFloat()
                        val peakY = topMargin + (plotHeight * (1f - (rssiValue - rssiMin) / rssiRange))

                        // Parabola span width (e.g., 2.5 times channel step on 2.4G, narrow on 5G)
                        val halfSpanWidth = if (graphBandSelect == 0) xStep * 2.5f else xStep * 1.5f

                        val path = Path().apply {
                            moveTo(centerX - halfSpanWidth, height - bottomMargin)
                            // Draw nice curve peak
                            cubicTo(
                                centerX - (halfSpanWidth / 2f), peakY + (height - bottomMargin - peakY) * 0.1f,
                                centerX - (halfSpanWidth / 4f), peakY,
                                centerX, peakY
                            )
                            cubicTo(
                                centerX + (halfSpanWidth / 4f), peakY,
                                centerX + (halfSpanWidth / 2f), peakY + (height - bottomMargin - peakY) * 0.1f,
                                centerX + halfSpanWidth, height - bottomMargin
                            )
                            close()
                        }

                        // Draw filled transparent area
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.15f),
                            style = Fill
                        )

                        // Draw path outline
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Draw a dot at the peak
                        drawCircle(
                            color = color,
                            radius = 3.dp.toPx(),
                            center = Offset(centerX, peakY)
                        )

                        // Draw SSID name above peak
                        drawContext.canvas.nativeCanvas.drawText(
                            ap.ssid,
                            centerX - (ap.ssid.length * 2f).coerceAtMost(centerX - leftMargin),
                            (peakY - 6.dp.toPx()).coerceAtLeast(topMargin),
                            android.graphics.Paint().apply {
                                setColor(color.toArgb())
                                textSize = 7.sp.toPx()
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                        )
                    }
                }
            }
        }
    }
}

// 4. Time Graph Screen
@Composable
fun TimeGraphScreen(
    scanResults: List<WifiApInfo>,
    signalHistory: Map<String, List<Pair<Long, Int>>>,
    activeBssids: Set<String>
) {
    if (signalHistory.isEmpty()) {
        EmptyWifiState("Tidak ada data histori sinyal.\nPastikan Wi-Fi aktif dan tunggu data masuk.")
        return
    }

    val gridLineColor = CyberTheme.TextSecondary.copy(alpha = 0.15f)
    val textCol = CyberTheme.TextSecondary

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Visualisasi Sinyal Wi-Fi Terhadap Waktu (dbM)",
            color = CyberTheme.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CyberTheme.SignalCardBg)
                .border(1.dp, CyberTheme.SignalCardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val leftMargin = 40.dp.toPx()
                val bottomMargin = 25.dp.toPx()
                val rightMargin = 10.dp.toPx()
                val topMargin = 15.dp.toPx()

                val plotWidth = width - leftMargin - rightMargin
                val plotHeight = height - topMargin - bottomMargin

                // Y Axis (-100 to -30 dBm)
                val rssiMin = -100f
                val rssiMax = -30f
                val rssiRange = rssiMax - rssiMin

                val yStep = plotHeight / 7f
                for (i in 0..7) {
                    val y = topMargin + (yStep * i)
                    val dbmValue = (rssiMax - (i * (rssiRange / 7f))).toInt()

                    drawLine(
                        color = gridLineColor,
                        start = Offset(leftMargin, y),
                        end = Offset(width - rightMargin, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        "$dbmValue",
                        10.dp.toPx(),
                        y + 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            setColor(textCol.toArgb())
                            textSize = 8.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }

                // X Axis - Time tracking ticks (30 seconds window)
                val now = System.currentTimeMillis()
                val windowMs = 90_000L // 90 seconds timeline window
                val startTime = now - windowMs

                // Vertical timeline ticks
                val xStep = plotWidth / 5f
                for (i in 0..5) {
                    val x = leftMargin + (xStep * i)
                    val timeAgoSec = ((5 - i) * 18) // segments of 18 seconds
                    drawLine(
                        color = gridLineColor,
                        start = Offset(x, topMargin),
                        end = Offset(x, height - bottomMargin),
                        strokeWidth = 1.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        if (timeAgoSec == 0) "Sekarang" else "-${timeAgoSec}s",
                        x - 12.dp.toPx(),
                        height - 6.dp.toPx(),
                        android.graphics.Paint().apply {
                            setColor(textCol.toArgb())
                            textSize = 8.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }

                // Draw Timeline Signal lines
                val apColors = listOf(
                    Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
                    Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF4DB6AC)
                )

                var colorIdx = 0
                for ((bssid, historyList) in signalHistory) {
                    if (historyList.isEmpty() || !activeBssids.contains(bssid)) continue

                    val color = apColors[colorIdx % apColors.size]
                    colorIdx++

                    val path = Path()
                    var isFirst = true

                    historyList.forEach { (time, rssi) ->
                        val relativeX = if (time >= startTime) {
                            leftMargin + plotWidth * ((time - startTime).toFloat() / windowMs)
                        } else {
                            leftMargin
                        }

                        val rssiVal = rssi.coerceIn(-100, -30).toFloat()
                        val relativeY = topMargin + plotHeight * (1f - (rssiVal - rssiMin) / rssiRange)

                        if (isFirst) {
                            path.moveTo(relativeX, relativeY)
                            isFirst = false
                        } else {
                            path.lineTo(relativeX, relativeY)
                        }
                    }

                    // Only draw if we actually added coordinates
                    if (!isFirst) {
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend of SSIDs with matching line colors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val apColors = listOf(
                Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
                Color(0xFFE57373), Color(0xFFBA68C8), Color(0xFF4DB6AC)
            )
            var colorIdx = 0
            for ((bssid, _) in signalHistory) {
                if (!activeBssids.contains(bssid)) continue
                val matchAp = scanResults.firstOrNull { it.bssid == bssid } ?: continue
                val color = apColors[colorIdx % apColors.size]
                colorIdx++

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = matchAp.ssid,
                        color = CyberTheme.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// 5. Connected Wifi Detail Screen
@Composable
fun ConnectedWifiDetailScreen(details: Map<String, String>) {
    if (details.isEmpty()) {
        EmptyWifiState("Saat ini tidak tersambung ke jaringan Wi-Fi apapun.\nSambungkan ke Wi-Fi di pengaturan sistem Anda.")
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CyberTheme.PrimaryAccent.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Koneksi",
                        tint = CyberTheme.PrimaryAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Status Jaringan Terhubung",
                            color = CyberTheme.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            details["SSID"] ?: "Unknown",
                            color = CyberTheme.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        items(details.entries.toList()) { entry ->
            DetailItemCard(label = entry.key, value = entry.value)
        }
    }
}

@Composable
fun DetailItemCard(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberTheme.SignalCardBg,
        border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = CyberTheme.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = CyberTheme.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End
            )
        }
    }
}

// Global Empty State
@Composable
fun EmptyWifiState(message: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = CyberTheme.PrimaryAccent.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = CyberTheme.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ConnectedDevicesScreen(viewModel: WifiAnalyzerViewModel) {
    val devices by viewModel.lanDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningLan.collectAsStateWithLifecycle()
    val progress by viewModel.lanScanProgress.collectAsStateWithLifecycle()
    
    // Automatically trigger a scan on entering the tab if devices are empty and we're not already scanning
    LaunchedEffect(Unit) {
        if (devices.isEmpty() && !isScanning) {
            viewModel.startLanScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Top Header card
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberTheme.SignalCardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PEMINDAI NET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberTheme.TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Perangkat LAN",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberTheme.TextPrimary
                        )
                    }

                    Button(
                        onClick = { viewModel.startLanScan() },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberTheme.PrimaryAccent,
                            disabledContainerColor = CyberTheme.PrimaryAccent.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isScanning) "Memindai..." else "Pindai",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }

                if (isScanning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mencari perangkat di subnet...",
                            fontSize = 10.sp,
                            color = CyberTheme.TextSecondary
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTheme.PrimaryAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = CyberTheme.PrimaryAccent,
                        trackColor = CyberTheme.SignalCardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } else if (devices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ditemukan ${devices.size} perangkat aktif di jaringan Wi-Fi ini.",
                        fontSize = 11.sp,
                        color = CyberTheme.SuccessGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (devices.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = CyberTheme.TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ketuk 'Pindai' untuk memetakan semua perangkat yang terhubung ke Wi-Fi ini.",
                        fontSize = 12.sp,
                        color = CyberTheme.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(devices, key = { it.ipAddress }) { device ->
                    DeviceRowItem(device)
                }
            }
        }
    }
}

@Composable
fun DeviceRowItem(device: com.mandala.net.LanDevice) {
    val cardBg = CyberTheme.SignalCardBg
    val cardBorder = if (device.isLocalDevice) CyberTheme.PrimaryAccent else if (device.isGateway) CyberTheme.WarningOrange else CyberTheme.SignalCardBorder
    
    val deviceIcon = when (device.deviceType) {
        "ThisDevice" -> Icons.Default.Smartphone
        "Gateway" -> Icons.Default.Router
        "Phone" -> Icons.Default.Smartphone
        "PC" -> Icons.Default.Computer
        "TV" -> Icons.Default.Tv
        "Printer" -> Icons.Default.Print
        "IoT" -> Icons.Default.Memory
        else -> Icons.Default.Devices
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container with cyber glow
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = when {
                            device.isLocalDevice -> CyberTheme.PrimaryAccent.copy(alpha = 0.1f)
                            device.isGateway -> CyberTheme.WarningOrange.copy(alpha = 0.1f)
                            else -> CyberTheme.TextSecondary.copy(alpha = 0.05f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            device.isLocalDevice -> CyberTheme.PrimaryAccent.copy(alpha = 0.3f)
                            device.isGateway -> CyberTheme.WarningOrange.copy(alpha = 0.3f)
                            else -> CyberTheme.SignalCardBorder
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = deviceIcon,
                    contentDescription = null,
                    tint = when {
                        device.isLocalDevice -> CyberTheme.PrimaryAccent
                        device.isGateway -> CyberTheme.WarningOrange
                        else -> CyberTheme.TextSecondary
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.hostname,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberTheme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Badges
                    if (device.isLocalDevice) {
                        Box(
                            modifier = Modifier
                                .background(CyberTheme.PrimaryAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("SAYA", color = CyberTheme.PrimaryAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (device.isGateway) {
                        Box(
                            modifier = Modifier
                                .background(CyberTheme.WarningOrange.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("ROUTER", color = CyberTheme.WarningOrange, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = device.ipAddress,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberTheme.PrimaryAccent
                    )
                    Text(
                        text = device.macAddress,
                        fontSize = 10.sp,
                        color = CyberTheme.TextSecondary
                    )
                }

                if (device.vendor.isNotEmpty() && device.vendor != "Generic Vendor") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.vendor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = CyberTheme.TextSecondary.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

