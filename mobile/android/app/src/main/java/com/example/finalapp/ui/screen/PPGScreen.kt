package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateMessage
import com.example.finalapp.ui.viewmodel.PpgViewModel
import androidx.compose.foundation.Canvas

@Composable
fun PPGScreen(
    navController: NavController,
    viewModel: PpgViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

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
                    Icon(Icons.Default.ArrowBack, contentDescription = t("Back", "Geri"), tint = CyanMain)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(t("PPG Signal", "PPG Sinyali"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(t("Backend signal quality view", "Backend sinyal kalitesi görünümü"), fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PPG Info Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(64.dp).background(CyanMain.copy(alpha = 0.1f), RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanMain, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f).background(CyanMain.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (uiState.avgQuality > 0) "${uiState.avgQuality}" else "--",
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = CyanMain
                                )
                                Text(t("Avg Quality", "Ort. Kalite"), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(modifier = Modifier.weight(1f).background(SuccessGreen.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val stabilityColor = if (uiState.signalStability > 80) SuccessGreen else if (uiState.signalStability > 50) OrangeMain else ErrorRed
                                Text(
                                    if (uiState.signalStability > 0) "${uiState.signalStability}%" else "--",
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = stabilityColor
                                )
                                Text(t("Stability", "Kararlılık"), fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(t("Signal Quality Trend", "Sinyal Kalite Trendi"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(12.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                if (uiState.isLoading && uiState.chartPoints.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanMain)
                    }
                } else if (uiState.chartPoints.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = translateMessage(
                                uiState.backendMessage ?: "Complete a BLE measurement to see PPG signal data.",
                                isTurkish
                            ),
                            color = TextMuted,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else if (uiState.chartPoints.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
                        val width = size.width
                        val height = size.height
                        val points = uiState.chartPoints

                        val maxQ = 100f
                        val minQ = 0f

                        val tMax = points.last().timestamp
                        val tMin = points.first().timestamp
                        val tRange = (tMax - tMin).toFloat().takeIf { it > 0 } ?: 1f

                        val path = androidx.compose.ui.graphics.Path()

                        points.forEachIndexed { i, p ->
                            val normalizedX = (p.timestamp - tMin).toFloat() / tRange
                            val normalizedY = 1f - (p.quality - minQ) / (maxQ - minQ)

                            val x = normalizedX * width
                            val y = normalizedY * height

                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                val prevP = points[i - 1]
                                val prevX = ((prevP.timestamp - tMin).toFloat() / tRange) * width
                                val prevY = (1f - (prevP.quality - minQ) / (maxQ - minQ)) * height

                                val controlX1 = prevX + (x - prevX) / 2f
                                val controlX2 = prevX + (x - prevX) / 2f

                                path.cubicTo(
                                    controlX1, prevY,
                                    controlX2, y,
                                    x, y
                                )
                            }
                        }

                        drawPath(
                            path = path,
                            color = CyanMain,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            translateMessage(uiState.errorMessage ?: "Failed to generate signal.", isTurkish),
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = uiState.backendMessage?.let { translateMessage(it, isTurkish) }
                        ?: t("This screen reflects whatever the backend PPG service currently provides.", "Bu ekran backend PPG servisinin o anda sağladığı veriyi yansıtır."),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // info Section
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyanMain, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(t("What is PPG?", "PPG Nedir?"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        t(
                            "Photoplethysmography (PPG) is an optical technique used to detect blood volume changes in the tissue.",
                            "Fotopletismografi (PPG), dokudaki kan hacmi değişimlerini algılamak için kullanılan optik bir tekniktir."
                        ),
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
