package com.example.bp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.data.model.HealthReadingDto
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingsScreen(
    token: String,
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(token) {
        if (uiState.readings.isEmpty() && !uiState.isLoading) {
            viewModel.loadAll(token)
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Ölçüm Geçmişi", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = BPBlueLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BPBlue)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text(
                    "${uiState.readings.size} kayıt bulundu",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            items(uiState.readings) { reading ->
                DetailedReadingCard(reading = reading)
            }
            if (uiState.readings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = TextHint,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Henüz ölçüm yok", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedReadingCard(reading: HealthReadingDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BPBlueDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💓", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ölçüm #${reading.id}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                val date = java.util.Date(if (reading.timestamp < 10000000000L) reading.timestamp * 1000 else reading.timestamp)
                val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(date)
                Text(
                    text = dateStr,
                    color = TextHint,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DividerDark)
            Spacer(Modifier.height(12.dp))

            // Values grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Sistolik", reading.systolicBp?.toString() ?: "--", "mmHg")
                MiniStat("Diyastolik", reading.diastolicBp?.toString() ?: "--", "mmHg")
                MiniStat("Nabız", reading.heartRate?.toString() ?: "--", "bpm")
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                reading.spo2?.let {
                    MiniStat("SpO2", it.toString(), "%", Modifier.weight(1f))
                }
                reading.temperature?.let {
                    MiniStat("Sıcaklık", "%.1f".format(it), "°C", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(4.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.width(2.dp))
            Text(unit, color = TextHint, fontSize = 11.sp)
        }
    }
}
