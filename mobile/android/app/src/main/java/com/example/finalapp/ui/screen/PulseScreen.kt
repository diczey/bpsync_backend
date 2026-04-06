package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalapp.ui.viewmodel.PulseViewModel

@Composable
fun PulseScreen(
    navController: NavController,
    viewModel: PulseViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ActivePink)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Heart Rate / ECG", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text("Latest backend pulse analysis", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pulse Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(44.dp).background(ActivePink.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(10.dp)) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = ActivePink)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Current BPM", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        }
                        IconButton(
                            onClick = { viewModel.toggleMonitoring() },
                            modifier = Modifier.background(if (uiState.isMonitoring) ErrorRed else SuccessGreen, RoundedCornerShape(16.dp))
                        ) {
                            Icon(if (uiState.isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Box(contentAlignment = Alignment.Center) {
                         Text(
                             if (uiState.isLoading && uiState.currentBpm == 0) "--" else "${uiState.currentBpm}", 
                             fontSize = 72.sp, fontWeight = FontWeight.Bold, color = ActivePink
                         )
                         if (uiState.isMonitoring) {
                             Box(modifier = Modifier.size(12.dp).offset(x = 60.dp).background(SuccessGreen, RoundedCornerShape(6.dp)))
                         }
                    }
                    Text("BPM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = Color.Black.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Status", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            val statusColor = if (uiState.statusLabel == "Normal") SuccessGreen else if (uiState.statusLabel == "Waiting") TextMuted else ErrorRed
                            Text(uiState.statusLabel, fontSize = 14.sp, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Resting HR", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(if (uiState.isLoading && uiState.restingHr == 0) "-- BPM" else "${uiState.restingHr} BPM", fontSize = 14.sp, color = ForegroundBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ECG Waveform Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(28.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           Icon(Icons.Default.Wifi, contentDescription = null, tint = ActivePink, modifier = Modifier.size(16.dp))
                           Spacer(modifier = Modifier.width(8.dp))
                           Text("Waveform Preview", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(if (uiState.isMonitoring) "Updating..." else "Paused", color = if (uiState.isMonitoring) SuccessGreen else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (uiState.isMonitoring && uiState.ecgWaveformPoints.isNotEmpty()) {
                         ECGWaveform(points = uiState.ecgWaveformPoints)
                    } else if (!uiState.isMonitoring) {
                         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             Text("Monitoring Paused", color = TextMuted, fontSize = 14.sp)
                         }
                    } else {
                         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             CircularProgressIndicator(color = ActivePink)
                         }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = uiState.backendMessage
                        ?: "This screen shows whatever the backend pulse service currently provides.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            // Health Tip
             GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                   Box(modifier = Modifier.size(44.dp).background(ActivePink.copy(alpha = 0.1f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                       Icon(Icons.Default.Warning, contentDescription = null, tint = ActivePink)
                   }
                   Spacer(modifier = Modifier.width(16.dp))
                   Column(modifier = Modifier.weight(1f)) {
                       Text("Heart Health Tip", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                       Text("Regular cardio maintaining a healthy rate.", fontSize = 12.sp, color = TextSecondary)
                   }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ECGWaveform(points: List<Float>) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val path = Path()
        
        path.moveTo(0f, centerY)
        
        val pointsCount = points.size
        if (pointsCount > 0) {
            val segmentWidth = width / pointsCount
            val scaleY = height / 5f 
            
            for (i in 0 until pointsCount) {
                val x = i * segmentWidth
                val dataIndex = ((i + phase * pointsCount).toInt()) % pointsCount
                val rawY = points[dataIndex]
                
                path.lineTo(x, centerY - (rawY * scaleY))
            }
        } else {
            path.lineTo(width, centerY)
        }
        
        drawPath(
            path = path,
            color = ActivePink,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
