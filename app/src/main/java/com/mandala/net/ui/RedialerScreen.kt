package com.mandala.net.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandala.net.CyberTheme
import com.mandala.net.RedialerState
import com.mandala.net.RedialerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedialerScreen(viewModel: RedialerViewModel) {
    val context = LocalContext.current
    val delaySeconds by viewModel.delaySeconds.collectAsStateWithLifecycle()
    val ringDurationSeconds by viewModel.ringDurationSeconds.collectAsStateWithLifecycle()
    val maxRetries by viewModel.maxRetries.collectAsStateWithLifecycle()
    val targetNumber by viewModel.targetNumber.collectAsStateWithLifecycle()
    val currentState by viewModel.currentState.collectAsStateWithLifecycle()
    val callCount by viewModel.callCount.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    
    val scrollState = rememberScrollState()

    var manualNumberInput by remember { mutableStateOf(targetNumber) }
    var tempDelay by remember { mutableStateOf(delaySeconds.toString()) }
    var tempRingDuration by remember { mutableStateOf(ringDurationSeconds.toString()) }
    var tempMaxRetries by remember { mutableStateOf(maxRetries.toString()) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    val activity = context as? Activity
    DisposableEffect(currentState) {
        if (currentState == RedialerState.CALLING || currentState == RedialerState.WAITING_TO_REDIAL) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(targetNumber) {
        manualNumberInput = targetNumber
    }
    
    LaunchedEffect(delaySeconds) { tempDelay = delaySeconds.toString() }
    LaunchedEffect(ringDurationSeconds) { tempRingDuration = ringDurationSeconds.toString() }
    LaunchedEffect(maxRetries) { tempMaxRetries = maxRetries.toString() }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { contactUri ->
                val cursor = context.contentResolver.query(
                    contactUri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        viewModel.setTargetNumber(number)
                        manualNumberInput = number
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val canCall = permissions[Manifest.permission.CALL_PHONE] == true
        val canReadState = permissions[Manifest.permission.READ_PHONE_STATE] == true
        val canAnswer = permissions[Manifest.permission.ANSWER_PHONE_CALLS] == true
        
        if (canCall && canReadState && canAnswer) {
            val hasOverlay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
            if (hasOverlay) {
                viewModel.startRedialing()
            } else {
                showOverlayPermissionDialog = true
            }
        } else {
            Toast.makeText(context, "Izin Call, Read State, & Answer harus diberikan", Toast.LENGTH_LONG).show()
        }
    }
    
    val readContactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Izin membaca kontak ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Auto Redialer Pintar",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CyberTheme.TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Panggil ulang otomatis saat nomor sibuk.",
            fontSize = 13.sp,
            color = CyberTheme.TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Target Configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Target Panggilan",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualNumberInput,
                        onValueChange = { 
                            manualNumberInput = it
                            viewModel.setTargetNumber(it)
                        },
                        placeholder = { Text("Masukkan nomor tujuan") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberTheme.TextPrimary,
                            unfocusedTextColor = CyberTheme.TextPrimary,
                            focusedBorderColor = CyberTheme.PrimaryAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        singleLine = true,
                        enabled = currentState == RedialerState.IDLE || currentState == RedialerState.STOPPED || currentState == RedialerState.CALL_SUCCESS
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                contactPickerLauncher.launch(intent)
                            } else {
                                readContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(CyberTheme.SignalCardBg, RoundedCornerShape(16.dp))
                            .border(1.dp, CyberTheme.SignalCardBorder, RoundedCornerShape(16.dp)),
                        enabled = currentState == RedialerState.IDLE || currentState == RedialerState.STOPPED || currentState == RedialerState.CALL_SUCCESS
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = "Pilih Kontak", tint = CyberTheme.PrimaryAccent)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Konfigurasi Redial",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Delay (Detik)", fontSize = 11.sp, color = CyberTheme.TextSecondary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempDelay,
                            onValueChange = { 
                                tempDelay = it
                                it.toIntOrNull()?.let { value -> viewModel.setDelay(value) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CyberTheme.TextPrimary,
                                unfocusedTextColor = CyberTheme.TextPrimary,
                                focusedBorderColor = CyberTheme.PrimaryAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            singleLine = true,
                            enabled = currentState == RedialerState.IDLE || currentState == RedialerState.STOPPED || currentState == RedialerState.CALL_SUCCESS
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ring (Detik)", fontSize = 11.sp, color = CyberTheme.TextSecondary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempRingDuration,
                            onValueChange = { 
                                tempRingDuration = it
                                it.toIntOrNull()?.let { value -> viewModel.setRingDuration(value) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CyberTheme.TextPrimary,
                                unfocusedTextColor = CyberTheme.TextPrimary,
                                focusedBorderColor = CyberTheme.PrimaryAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            singleLine = true,
                            enabled = currentState == RedialerState.IDLE || currentState == RedialerState.STOPPED || currentState == RedialerState.CALL_SUCCESS
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Retries", fontSize = 11.sp, color = CyberTheme.TextSecondary, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempMaxRetries,
                            onValueChange = { 
                                tempMaxRetries = it
                                it.toIntOrNull()?.let { value -> viewModel.setMaxRetries(value) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CyberTheme.TextPrimary,
                                unfocusedTextColor = CyberTheme.TextPrimary,
                                focusedBorderColor = CyberTheme.PrimaryAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            singleLine = true,
                            enabled = currentState == RedialerState.IDLE || currentState == RedialerState.STOPPED || currentState == RedialerState.CALL_SUCCESS
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Panggilan akan diputus otomatis dan diulang setelah $ringDurationSeconds detik jika belum terjawab. Ketuk JEDA pada tombol panel melayang jika panggilan telah dijawab agar pembicaraan tidak terputus.",
                    fontSize = 11.sp,
                    color = CyberTheme.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (currentState != RedialerState.IDLE) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberTheme.SignalCardBg),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, CyberTheme.SignalCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STATUS REDIALER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberTheme.TextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val statusColor = when (currentState) {
                        RedialerState.IDLE, RedialerState.STOPPED -> CyberTheme.TextSecondary
                        RedialerState.CALLING -> CyberTheme.PrimaryAccent
                        RedialerState.WAITING_TO_REDIAL -> CyberTheme.WarningOrange
                        RedialerState.CALL_SUCCESS -> CyberTheme.SuccessGreen
                        RedialerState.PAUSED -> CyberTheme.WarningOrange
                    }
                    
                    val statusText = when (currentState) {
                        RedialerState.IDLE -> "MENUNGGU INSTRUKSI"
                        RedialerState.STOPPED -> "DIHENTIKAN"
                        RedialerState.CALLING -> "MENGHUBUNGI..."
                        RedialerState.WAITING_TO_REDIAL -> "MENUNGGU REDIAL: $countdown DETIK"
                        RedialerState.CALL_SUCCESS -> "PANGGILAN BERHASIL TERHUBUNG"
                        RedialerState.PAUSED -> "DITANGGUHKAN (PAUSED)"
                    }

                    Text(
                        text = statusText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                    
                    if (callCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Percobaan Panggilan: $callCount",
                            fontSize = 14.sp,
                            color = CyberTheme.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Action Buttons
        if (currentState == RedialerState.IDLE || currentState == RedialerState.STOPPED || currentState == RedialerState.CALL_SUCCESS) {
            Button(
                onClick = {
                    if (targetNumber.isNotBlank()) {
                        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                        val hasReadState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                        val hasAnswer = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
                        if (hasCall && hasReadState && hasAnswer) {
                            val hasOverlay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                android.provider.Settings.canDrawOverlays(context)
                            } else {
                                true
                            }
                            if (hasOverlay) {
                                viewModel.startRedialing()
                            } else {
                                showOverlayPermissionDialog = true
                            }
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS))
                        }
                    } else {
                        Toast.makeText(context, "Masukkan nomor terlebih dahulu", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.SuccessGreen)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text("MULAI REDIAL", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
        } else {
            Button(
                onClick = { viewModel.stopRedialing() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.ErrorRed)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text("HENTIKAN REDIAL", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }
        }
        
        if (showOverlayPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showOverlayPermissionDialog = false },
                title = { Text("Izin Jendela Apung") },
                text = { Text("Aplikasi membutuhkan izin 'Tampilkan di atas aplikasi lain' agar panel info/status redialer tetap aktif melayang saat Anda sedang dalam panggilan.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showOverlayPermissionDialog = false
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                context.startActivity(intent)
                            }
                        }
                    ) {
                        Text("Buka Pengaturan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOverlayPermissionDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
