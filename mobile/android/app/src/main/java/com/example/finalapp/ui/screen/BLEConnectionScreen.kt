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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.finalapp.data.repository.BleDeviceRole
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

private enum class BlePermissionAction {
    CONNECT_WRIST,
    CONNECT_CHEST
}

@Composable
fun BLEConnectionScreen(
    navController: NavController,
    viewModel: BleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isTurkish = rememberIsTurkish()
    val runtimePermissions = remember { requiredBlePermissions() }
    var pendingPermissionAction by remember { mutableStateOf<BlePermissionAction?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (runtimePermissions.all { permission -> result[permission] == true }) {
            when (pendingPermissionAction) {
                BlePermissionAction.CONNECT_WRIST -> viewModel.connectWrist()
                BlePermissionAction.CONNECT_CHEST -> viewModel.connectChest()
                null -> Unit
            }
        } else {
            viewModel.onPermissionsDenied()
        }
        pendingPermissionAction = null
    }

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    fun requestConnection(action: BlePermissionAction, onGranted: () -> Unit) {
        if (hasBlePermissions(context)) {
            onGranted()
        } else {
            pendingPermissionAction = action
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
                    Text(t("Bluetooth Connection", "Bluetooth Baglantisi"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Dual-device wrist and chest pairing", "Iki cihazli wrist ve chest eslestirme"), fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                if (uiState.readyToStart || uiState.streaming) SuccessGreen else PrimaryBlue,
                                RoundedCornerShape(24.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (uiState.readyToStart || uiState.streaming) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(uiState.statusTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(uiState.statusSubtitle, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))

                    Spacer(modifier = Modifier.height(16.dp))
                    DeviceStatusCard(
                        title = t("Wrist Module", "Wrist Modulu"),
                        connected = uiState.wristConnected,
                        device = uiState.wristDevice,
                        accent = PrimaryBlue,
                        connectedLabel = t("Connected", "Bagli"),
                        disconnectedLabel = t("Not connected", "Bagli degil")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    DeviceStatusCard(
                        title = t("Chest Module", "Chest Modulu"),
                        connected = uiState.chestConnected,
                        device = uiState.chestDevice,
                        accent = SuccessGreen,
                        connectedLabel = t("Connected", "Bagli"),
                        disconnectedLabel = t("Not connected", "Bagli degil")
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .padding(14.dp)
                    ) {
                        Text(t("Prediction Backend", "Tahmin Motoru"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${t("Active model", "Aktif model")}: ${uiState.activeModelLabel}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        uiState.modelMessage?.let { modelMessage ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = translateMessage(modelMessage, isTurkish), fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    DebugStatsCard(
                        isTurkish = isTurkish,
                        bufferFill = uiState.bufferFill,
                        framesUploaded = uiState.framesUploaded,
                        uploadFailures = uiState.uploadFailures,
                        framesReceived = uiState.framesReceived,
                        wristFramesReceived = uiState.wristFramesReceived,
                        chestFramesReceived = uiState.chestFramesReceived,
                        measurementsReady = uiState.measurementsReady,
                        lastSeq = uiState.lastSeq,
                        lastError = uiState.lastBleError
                    )

                    uiState.lastMeasurement?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SuccessGreen.copy(alpha = 0.12f))
                                .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                t("Latest BP Result", "Son BP Sonucu"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                result,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForegroundBlack
                            )
                        }
                    }

                    uiState.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = translateMessage(error, isTurkish), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                requestConnection(BlePermissionAction.CONNECT_WRIST, viewModel::connectWrist)
                            },
                            enabled = !uiState.connecting && !uiState.wristConnected,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text(
                                if (uiState.wristConnected) t("Wrist Ready", "Wrist Hazir") else t("Connect Wrist", "Wrist Bagla"),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                requestConnection(BlePermissionAction.CONNECT_CHEST, viewModel::connectChest)
                            },
                            enabled = !uiState.connecting && !uiState.chestConnected,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                        ) {
                            Text(
                                if (uiState.chestConnected) t("Chest Ready", "Chest Hazir") else t("Connect Chest", "Chest Bagla"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        if (uiState.readyToStart && !uiState.streaming) {
                            t(
                                "Both devices are ready. Press start measurement to begin.",
                                "Iki cihaz hazir. Olcume baslamak icin baslat tusuna bas."
                            )
                        } else {
                            t(
                                "Connect Wrist and Chest separately. Start becomes active when both are connected.",
                                "Wrist ve Chest'i ayri ayri bagla. Ikisi de baglaninca baslat aktif olur."
                            )
                        },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.readyToStart && !uiState.streaming) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::startMeasurement,
                            enabled = !uiState.startingMeasurement && !uiState.stoppingMeasurement,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (uiState.startingMeasurement) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    t("Starting...", "Baslatiliyor..."),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    t("Start Measurement", "Olcume Basla"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (uiState.streaming) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::stopMeasurement,
                            enabled = !uiState.startingMeasurement && !uiState.stoppingMeasurement,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ErrorRed)
                        ) {
                            if (uiState.stoppingMeasurement) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = ErrorRed
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    t("Stopping...", "Durduruluyor..."),
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    t("Stop Measurement", "Olcumu Durdur"),
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (uiState.connected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::disconnect,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, TextMuted)
                        ) {
                            Text(t("Disconnect Devices", "Cihazlari Ayir"), color = ForegroundBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusCard(
    title: String,
    connected: Boolean,
    device: BleDevice?,
    accent: Color,
    connectedLabel: String,
    disconnectedLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .padding(14.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(if (connected) connectedLabel else disconnectedLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (connected) accent else ForegroundBlack)
        device?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it.name, fontSize = 12.sp, color = ForegroundBlack)
            Text(it.address, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun DebugStatsCard(
    isTurkish: Boolean,
    bufferFill: String,
    framesUploaded: Int,
    uploadFailures: Int,
    framesReceived: Int,
    wristFramesReceived: Int,
    chestFramesReceived: Int,
    measurementsReady: Int,
    lastSeq: Int,
    lastError: String?
) {
    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .padding(14.dp)
    ) {
        Text(
            text = t("BLE Debug", "BLE Debug"),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))
        DebugStatRow(t("Buffer fill", "Buffer dolulugu"), bufferFill)
        DebugStatRow(t("Frames uploaded", "Gonderilen frame"), framesUploaded.toString())
        DebugStatRow(t("Upload failures", "Upload hatasi"), uploadFailures.toString())
        DebugStatRow(t("Total frames", "Toplam frame"), framesReceived.toString())
        DebugStatRow(t("Wrist frames", "Wrist frame"), wristFramesReceived.toString())
        DebugStatRow(t("Chest frames", "Chest frame"), chestFramesReceived.toString())
        DebugStatRow(t("Ready readings", "Hazir olcum"), measurementsReady.toString())
        DebugStatRow(t("Last seq", "Son seq"), lastSeq.toString())

        if (!lastError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = t("Last error", "Son hata"),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = translateMessage(lastError, isTurkish),
                fontSize = 12.sp,
                color = ErrorRed
            )
        }
    }
}

@Composable
private fun DebugStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun DeviceSection(
    title: String,
    devices: List<BleDevice>,
    emptyLabel: String,
    enabled: Boolean,
    onClick: (BleDevice) -> Unit
) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.fillMaxWidth().padding(start = 4.dp))
    Spacer(modifier = Modifier.height(12.dp))

    if (devices.isEmpty()) {
        if (emptyLabel.isNotBlank()) {
            Text(emptyLabel, fontSize = 12.sp, color = TextMuted, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
        }
        return
    }

    devices.forEachIndexed { index, device ->
        DeviceItem(device = device, enabled = enabled && device.role != BleDeviceRole.UNKNOWN, onClick = { onClick(device) })
        if (index != devices.lastIndex) {
            Spacer(modifier = Modifier.height(8.dp))
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
                    Text(
                        when (device.role) {
                            BleDeviceRole.WRIST -> "Wrist"
                            BleDeviceRole.CHEST -> "Chest"
                            BleDeviceRole.UNKNOWN -> "Unknown"
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
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
