package com.example.bp.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.BLEViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEScreen(
    token: String,
    viewModel: BLEViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(token) {
        viewModel.loadStatus(token)
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Sensör Bağlantısı", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = TextPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // BLE Status Indicator
            BLEStatusHeader(connected = uiState.status?.connected ?: false)

            Spacer(modifier = Modifier.height(32.dp))

            // Action Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.status?.connected == true) "Cihaz Bağlı" else "Cihaz Aranıyor...",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    if (uiState.status?.deviceName != null) {
                        Text(
                            text = uiState.status!!.deviceName,
                            color = BPBlueLight,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Main Connect/Scan Button
                    Button(
                        onClick = { viewModel.scan(token) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.status?.connected == true) BPGreen.copy(alpha = 0.2f) else BPBlue
                        ),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (uiState.status?.connected == true) "Yeniden Tara" else "Sensörü Bul", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Streaming Controls (Only show if connected)
            AnimatedVisibility(
                visible = uiState.status?.connected == true,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Veri Akışı", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startStreaming(token) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BPBlueDark),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !uiState.isStreaming && !uiState.isLoading
                            ) {
                                Text("BAŞLAT", fontWeight = FontWeight.Bold)
                            }
                            
                            Button(
                                onClick = { viewModel.stopStreaming(token) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BPRed.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = uiState.isStreaming && !uiState.isLoading
                            ) {
                                Text("DURDUR", color = BPRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Info Messages
            uiState.message?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = BPBlue.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = msg,
                        color = BPBlueLight,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = err,
                        color = BPRed,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BLEStatusHeader(connected: Boolean) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = if (connected) listOf(BPGreen.copy(alpha = 0.3f), Color.Transparent)
                    else listOf(BPBlue.copy(alpha = 0.2f), Color.Transparent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(if (connected) BPGreen else BPBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
    }
}
