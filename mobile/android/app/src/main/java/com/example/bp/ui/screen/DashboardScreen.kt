package com.example.bp.ui.screen

import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.data.model.DashboardSummaryDto
import com.example.bp.data.model.HealthReadingDto
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    token: String,
    viewModel: DashboardViewModel,
    onNavigateToReadings: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToBle: () -> Unit,
    onNavigateToReports: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        viewModel.loadAll(token)
    }

    Scaffold(
        containerColor = BackgroundDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💓 ", fontSize = 22.sp)
                        Text(
                            "BPSync",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                ),
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profil", tint = BPBlueLight)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Çıkış", tint = TextSecondary)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Ana Sayfa") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BPBlue,
                        selectedTextColor = BPBlue,
                        indicatorColor = BPBlueDark
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToReadings,
                    icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text("Ölçümler") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToTrends,
                    icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    label = { Text("Trendler") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BPBlue)
                    }
                }
                return@LazyColumn
            }

            uiState.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = BPRed)
                            Spacer(Modifier.width(8.dp))
                            Text(text = err, color = BPRed)
                        }
                    }
                }
            }

            uiState.summary?.let { summary ->
                item { BPSummaryCard(summary = summary) }
                item { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) { BLEConnectCard(onNavigateToBle) }
                        Box(Modifier.weight(1f)) { ReportLinkCard(onNavigateToReports) }
                    }
                }
                item { StatsRow(summary = summary) }

                item {
                    Button(
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Sağlıklı kalın!")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BPGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sağlıklı Kalın Mesajı", color = TextPrimary)
                    }
                }
            }

            if (uiState.readings.isNotEmpty()) {
                item {
                    Text(
                        "Son Ölçümler",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                }
                items(uiState.readings.take(5)) { reading ->
                    ReadingCard(reading = reading)
                }
            }
        }
    }
}

@Composable
private fun BPSummaryCard(summary: DashboardSummaryDto) {
    val bpStatus = summary.healthStatus
    val statusColor = when {
        bpStatus.contains("Normal", ignoreCase = true) -> BPGreen
        bpStatus.contains("Yüksek", ignoreCase = true) || bpStatus.contains("High", ignoreCase = true) -> BPRed
        bpStatus.contains("Düşük", ignoreCase = true) || bpStatus.contains("Low", ignoreCase = true) -> BPOrange
        else -> BPBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(BPBlueDark, SurfaceDark)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = bpStatus,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                val sys = summary.latestSystolic.toString()
                val dia = summary.latestDiastolic.toString()
                Text(
                    text = "$sys / $dia",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "mmHg  (Ortalama)",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Son Güncelleme",
                    color = TextHint,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StatsRow(summary: DashboardSummaryDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(
            modifier = Modifier.weight(1f),
            icon = "❤️",
            label = "Nabız",
            value = "${summary.latestHeartRate}"
        )
        StatChip(
            modifier = Modifier.weight(1f),
            icon = "💧",
            label = "SpO2",
            value = "${summary.latestSpo2}%"
        )
        StatChip(
            modifier = Modifier.weight(1f),
            icon = "🌡️",
            label = "Sıcaklık",
            value = "%.1f°C".format(summary.latestTemperature)
        )
    }
}

@Composable
private fun StatChip(modifier: Modifier, icon: String, label: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ReadingCard(reading: HealthReadingDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BPBlueDark),
                contentAlignment = Alignment.Center
            ) {
                Text("💉", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val sys = reading.systolicBp?.toString() ?: "--"
                val dia = reading.diastolicBp?.toString() ?: "--"
                Text(
                    "$sys / $dia mmHg",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                val hr = reading.heartRate?.let { "Nabız: $it bpm" } ?: ""
                if (hr.isNotEmpty()) Text(hr, color = TextSecondary, fontSize = 13.sp)
            }
            val date = java.util.Date(if (reading.timestamp < 10000000000L) reading.timestamp * 1000 else reading.timestamp)
            val dateStr = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(date)
            Text(
                text = dateStr,
                color = TextHint,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun BLEConnectCard(onConnect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BPBlue.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BPBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Sensöre Bağlan", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Bilek modülünden veri almayı başlat", color = BPBlueLight, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BPBlueLight)
        }
    }
}

@Composable
private fun ReportLinkCard(onNavigate: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BPOrange.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BPOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Sağlık Raporu", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
