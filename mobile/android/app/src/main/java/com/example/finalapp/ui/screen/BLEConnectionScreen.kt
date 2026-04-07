package com.example.finalapp.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.data.repository.BleDevice
import com.example.finalapp.ui.component.PremiumGlassCard
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateMessage
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ErrorRed
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.BleViewModel

@Composable
fun BLEConnectionScreen(
    navController: NavController,
    viewModel: BleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTurkish = rememberIsTurkish()
    val runtimePermissions = remember { requiredBlePermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (runtimePermissions.all { permission -> result[permission] == true }) {
            viewModel.scanDevices()
        } else {
            viewModel.onPermissionsDenied()
        }
    }

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    fun requestScan() {
        if (hasBlePermissions(context)) {
            viewModel.scanDevices()
        } else {
            permissionLauncher.launch(runtimePermissions)
        }
    }

    fun connect(device: BleDevice) {
        if (hasBlePermissions(context)) {
            viewModel.connectToDevice(device)
        } else {
            permissionLauncher.launch(runtimePermissions)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = t("Back", "Geri"), tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(t("Bluetooth Connection", "Bluetooth Bağlantısı"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Real BLE device pairing", "Gerçek BLE cihaz eşleştirmesi"), fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(if (uiState.connected) SuccessGreen else PrimaryBlue, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (uiState.connected) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        uiState.statusTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForegroundBlack
                    )
                    Text(
                        uiState.statusSubtitle,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .padding(14.dp)
                    ) {
                        Text(
                            t("Prediction Backend", "Tahmin Motoru"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${t("Active model", "Aktif model")}: ${uiState.activeModelLabel}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForegroundBlack
                        )
                        uiState.modelMessage?.let { modelMessage ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = translateMessage(modelMessage, isTurkish),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (uiState.connected) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.55f))
                                .padding(14.dp)
                        ) {
                            Text(
                                if (uiState.streaming) t("Live Sync Active", "Canlı Senkron Aktif") else t("Waiting for stream", "Akış bekleniyor"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForegroundBlack
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${t("Frames received", "Alınan kare")}: ${uiState.framesReceived}", fontSize = 12.sp, color = TextSecondary)
                            Text("${t("Frames uploaded", "Gönderilen kare")}: ${uiState.framesUploaded}", fontSize = 12.sp, color = TextSecondary)
                            Text("${t("Window fill", "Pencere doluluğu")}: ${uiState.bufferFill}", fontSize = 12.sp, color = TextSecondary)
                            Text("${t("Measurements ready", "Hazır ölçüm")}: ${uiState.measurementsReady}", fontSize = 12.sp, color = TextSecondary)
                            uiState.lastMeasurement?.let { measurement ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("${t("Latest", "Son")}: $measurement", fontSize = 12.sp, color = ForegroundBlack)
                            }
                        }
                    }

                    uiState.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = translateMessage(error, isTurkish),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (!uiState.connected) {
                        Button(
                            onClick = ::requestScan,
                            enabled = !uiState.scanning && !uiState.connecting,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (uiState.scanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Text(t("Scan for Devices", "Cihazları Tara"), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::disconnect,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ErrorRed)
                        ) {
                            Text(t("Disconnect Device", "Cihaz Bağlantısını Kes"), color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.devices.isNotEmpty() && !uiState.connected) {
                Text(
                    t("Found Devices", "Bulunan Cihazlar"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Start).padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                for (device in uiState.devices) {
                    DeviceItem(
                        device = device,
                        enabled = !uiState.connecting && !uiState.scanning,
                        onClick = { connect(device) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BleDevice,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .border(1.dp, Color.White, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(device.name, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(device.address, fontSize = 12.sp, color = TextMuted)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}

private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun hasBlePermissions(context: Context): Boolean {
    return requiredBlePermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
