package com.example.finalapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.component.PremiumGlassCard
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateMessage
import com.example.finalapp.ui.localization.translateStatus
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ErrorRed
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PrimaryGradient
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.HealthStatusViewModel
import kotlinx.coroutines.delay

@Composable
fun HealthStatusScreen(
    navController: NavController,
    viewModel: HealthStatusViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTurkish = rememberIsTurkish()

    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    val countdownTarget = when (uiState.countdownPhase) {
        "calibration" -> uiState.calibrationReadyAt
        "personalized" -> uiState.weeklyStatusReadyAt
        else -> null
    }

    var remainingMillis by remember(countdownTarget) { mutableLongStateOf(0L) }

    LaunchedEffect(countdownTarget) {
        if (countdownTarget == null) {
            remainingMillis = 0L
            return@LaunchedEffect
        }

        while (true) {
            remainingMillis = (countdownTarget - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remainingMillis == 0L) break
            delay(1000)
        }
    }

    val countdownDays = remainingMillis / 86_400_000L
    val countdownHours = (remainingMillis % 86_400_000L) / 3_600_000L
    val countdownMinutes = (remainingMillis % 3_600_000L) / 60_000L
    val countdownSeconds = (remainingMillis % 60_000L) / 1000L

    val cardTitle = when (uiState.countdownPhase) {
        "awaiting_device" -> t("3-Day Calibration", "3 Günlük Kalibrasyon")
        "calibration" -> t("Calibration Countdown", "Kalibrasyon Geri Sayımı")
        "personalized" -> t("Calibration Complete", "Kalibrasyon Tamamlandı")
        "weekly_pending" -> t("Weekly Data Pending", "Haftalık Veri Bekleniyor")
        else -> t("7-Day Health Status", "7 Günlük Sağlık Durumu")
    }
    val cardSubtitle = when (uiState.countdownPhase) {
        "awaiting_device" -> t(
            "Connect your BLE device to start the countdown",
            "Geri sayımı başlatmak için BLE cihazını bağla"
        )
        "calibration" -> t(
            "Personalized calibration becomes active after 3 full days",
            "Kişiselleştirilmiş kalibrasyon 3 tam gün sonra aktif olur"
        )
        "personalized" -> t(
            "Daily labels are now personalized until day 7",
            "Günlük etiketler artık 7. güne kadar kişiselleştirildi"
        )
        "weekly_pending" -> t(
            "Collect readings on 7 distinct days to unlock weekly status",
            "Haftalık durumu açmak için 7 farklı günde ölçüm topla"
        )
        else -> t(
            "Your weekly health status is now available",
            "Haftalık sağlık durumu artık hazır"
        )
    }

    val modeLabel = if (uiState.statusMode == "personalized") {
        t("Personalized mode", "Kişiselleştirilmiş mod")
    } else {
        t("Standard mode", "Standart mod")
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
                    Text(t("Health Status", "Sağlık Durumu"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(
                        when (uiState.countdownPhase) {
                            "awaiting_device" -> t("Waiting for BLE connection", "BLE bağlantısı bekleniyor")
                            "calibration" -> t("3-day calibration is running", "3 günlük kalibrasyon çalışıyor")
                            "personalized" -> t("Personalized tracking is active", "Kişiselleştirilmiş takip aktif")
                            "weekly_pending" -> t("Waiting for more weekly data", "Daha fazla haftalık veri bekleniyor")
                            else -> t("7-day status is active", "7 günlük durum aktif")
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(PrimaryGradient, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(cardTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(cardSubtitle, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.isLoading && countdownTarget == null && uiState.message == null) {
                        CircularProgressIndicator(color = PrimaryBlue)
                    } else if (uiState.countdownPhase == "awaiting_device") {
                        Text(
                            translateMessage(
                                uiState.message ?: "Connect your wristband to start the 3-day calibration countdown.",
                                isTurkish
                            ),
                            fontSize = 14.sp,
                            color = ForegroundBlack
                        )
                    } else if (uiState.countdownPhase == "weekly_pending") {
                        Text(
                            translateMessage(
                                uiState.message ?: "Collect readings across 7 distinct days to unlock weekly status.",
                                isTurkish
                            ),
                            fontSize = 14.sp,
                            color = ForegroundBlack
                        )
                    } else if (uiState.countdownPhase == "weekly_ready") {
                        Text(
                            t("Calibration finished and 7-day status is available.", "Kalibrasyon tamamlandı ve 7 günlük durum hazır."),
                            fontSize = 14.sp,
                            color = ForegroundBlack
                        )
                        if (uiState.healthScore > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "${t("Health Score", "Sağlık Skoru")}: ${uiState.healthScore}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CountdownItem(modifier = Modifier.weight(1f), value = countdownDays.toString(), label = t("Days", "Gün"))
                            CountdownItem(modifier = Modifier.weight(1f), value = countdownHours.toString().padStart(2, '0'), label = t("Hours", "Saat"))
                            CountdownItem(modifier = Modifier.weight(1f), value = countdownMinutes.toString().padStart(2, '0'), label = t("Mins", "Dak"))
                            CountdownItem(modifier = Modifier.weight(1f), value = countdownSeconds.toString().padStart(2, '0'), label = t("Secs", "Sn"))
                        }
                    }

                    if (uiState.trackingDay > 0) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            "${t("Tracking day", "Takip günü")} ${uiState.trackingDay}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = SuccessGreen)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(t("Current Status", "Güncel Durum"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                            Text(modeLabel, fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (uiState.message != null && uiState.countdownPhase != "awaiting_device") {
                        Text(
                            translateMessage(uiState.message!!, isTurkish),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    StatusRow(
                        label = t("Overall", "Genel"),
                        status = translateStatus(uiState.overallStatus, isTurkish),
                        color = statusColor(uiState.overallStatus)
                    )
                    StatusRow(
                        label = t("Blood Pressure", "Tansiyon"),
                        status = translateStatus(uiState.bloodPressureStatus, isTurkish),
                        color = statusColor(uiState.bloodPressureStatus)
                    )
                    StatusRow(
                        label = t("Heart Rate", "Kalp Atışı"),
                        status = translateStatus(uiState.heartRateStatus, isTurkish),
                        color = statusColor(uiState.heartRateStatus)
                    )
                    StatusRow(
                        label = t("Oxygen Level", "Oksijen Seviyesi"),
                        status = translateStatus(uiState.oxygenStatus, isTurkish),
                        color = statusColor(uiState.oxygenStatus)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(t("How this works", "Bu nasıl çalışır?"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        when (uiState.countdownPhase) {
                            "awaiting_device" -> t(
                                "When BLE connects successfully, a 3-day calibration countdown starts.",
                                "BLE bağlantısı başarılı olduğunda 3 günlük kalibrasyon geri sayımı başlar."
                            )
                            "calibration" -> t(
                                "The first 3 days are used to learn your baseline blood pressure values.",
                                "İlk 3 gün, temel tansiyon değerlerini öğrenmek için kullanılır."
                            )
                            "personalized" -> t(
                                "Days 4 to 6 now label your readings according to your own 3-day average.",
                                "4 ile 6. günler arasında ölçümlerin kendi 3 günlük ortalamana göre etiketlenir."
                            )
                            else -> t(
                                "After day 7, the app combines your personalized baseline and weekly readings into a full health status.",
                                "7. günden sonra uygulama kişisel temel değerinle haftalık ölçümleri birleştirip tam sağlık durumunu çıkarır."
                            )
                        },
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CountdownItem(modifier: Modifier, value: String, label: String) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(color.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ForegroundBlack)
            Text(status, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun statusColor(status: String): Color {
    return when (status.lowercase()) {
        "excellent", "good", "normal", "optimal" -> SuccessGreen
        "fair", "elevated", "personalized tracking", "calibrating" -> OrangeMain
        "low" -> OrangeMain
        "high", "critical", "needs attention", "attention required" -> ErrorRed
        else -> TextSecondary
    }
}
