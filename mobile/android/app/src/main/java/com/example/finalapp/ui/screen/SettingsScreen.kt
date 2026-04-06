package com.example.finalapp.ui.screen

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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.CyanMain
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PurpleMain
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTurkish = uiState.settings.language == "tr"

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(t("Settings", "Ayarlar"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(
                        t("Synced with your account", "Hesabınla senkronize"),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            uiState.message?.let { message ->
                Text(message, color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(PurpleMain.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = PurpleMain)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(t("Language", "Dil"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                            Text(
                                t("Stored on device and backend", "Cihazda ve backend'de saklanır"),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        LanguageButton(
                            modifier = Modifier.weight(1f),
                            label = "English",
                            selected = uiState.settings.language == "en",
                            onClick = { viewModel.setLanguage("en") }
                        )
                        LanguageButton(
                            modifier = Modifier.weight(1f),
                            label = "Türkçe",
                            selected = uiState.settings.language == "tr",
                            onClick = { viewModel.setLanguage("tr") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(t("Notifications", "Bildirimler"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ToggleItem(
                        label = t("Push Notifications", "Anlık Bildirimler"),
                        checked = uiState.settings.notificationsEnabled,
                        onCheckedChange = viewModel::setPushNotifications
                    )
                    ToggleItem(
                        label = t("Weekly Reports", "Haftalık Raporlar"),
                        checked = uiState.settings.weeklyReportsEnabled,
                        onCheckedChange = viewModel::setWeeklyReports
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.isSaving) {
                            t("Saving changes...", "Değişiklikler kaydediliyor...")
                        } else {
                            t(
                                "Preferences are synced to your account when you are signed in.",
                                "Tercihler giriş yaptığında hesabınla senkronize edilir."
                            )
                        },
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsLinkItem(
                title = t("Notification Inbox", "Bildirim Kutusu"),
                icon = Icons.Default.Notifications,
                color = PrimaryBlue,
                onClick = { navController.navigate(Screen.Notifications.route) }
            )
            SettingsLinkItem(
                title = t("Health Reports", "Sağlık Raporları"),
                icon = Icons.Default.Description,
                color = OrangeMain,
                onClick = { navController.navigate(Screen.Reports.route) }
            )
            SettingsLinkItem(
                title = t("Privacy Policy", "Gizlilik Politikası"),
                icon = Icons.Default.Security,
                color = SuccessGreen,
                onClick = {}
            )
            SettingsLinkItem(
                title = t("About BP Sync", "BP Sync Hakkında"),
                icon = Icons.Default.Info,
                color = CyanMain,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = if (uiState.settings.language == "tr") "BP Sync Sürüm 1.0.0" else "BP Sync Version 1.0.0",
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LanguageButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) PrimaryBlue else Color.White.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else ForegroundBlack,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ForegroundBlack)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray
            )
        )
    }
}

@Composable
private fun SettingsLinkItem(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = color)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            }
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextMuted)
        }
    }
}
