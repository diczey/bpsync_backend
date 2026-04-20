package com.example.finalapp.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.data.model.NotificationDto
import com.example.finalapp.ui.component.GlassCard
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateNotificationType
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.NotificationsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = viewModel()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(t("Notifications", "Bildirimler"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    Text(
                        if (isTurkish) "${uiState.unreadCount} okunmadı" else "${uiState.unreadCount} unread",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                TextButton(onClick = viewModel::markAllAsRead) {
                    Text(t("Mark all read", "Tümünü okundu yap"), fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilterChip(
                    selected = !uiState.unreadOnly,
                    onClick = { viewModel.toggleUnreadOnly(false) },
                    label = { Text(t("All", "Tümü")) }
                )
                FilterChip(
                    selected = uiState.unreadOnly,
                    onClick = { viewModel.toggleUnreadOnly(true) },
                    label = { Text(t("Unread", "Okunmamış")) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            uiState.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            uiState.message?.let { message ->
                Text(
                    text = when {
                        isTurkish && message == "No notifications yet." -> "Henüz bildirim yok."
                        else -> message
                    },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.notifications.isEmpty() && !uiState.isLoading) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t("Your notification inbox is empty.", "Bildirim kutunuz şu anda boş."),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else {
                uiState.notifications.forEach { notification ->
                    NotificationCard(
                        notification = notification,
                        isTurkish = isTurkish,
                        onMarkRead = { viewModel.markAsRead(notification.id) },
                        onDelete = { viewModel.deleteNotification(notification.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationDto,
    isTurkish: Boolean,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    fun t(english: String, turkish: String): String = if (isTurkish) turkish else english

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (notification.isRead) SuccessGreen.copy(alpha = 0.12f) else PrimaryBlue.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (notification.isRead) SuccessGreen else PrimaryBlue
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(notification.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                        Text(translateNotificationType(notification.type, isTurkish), fontSize = 11.sp, color = TextMuted)
                    }
                }

                Text(
                    text = formatNotificationTimestamp(notification.timestamp),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(notification.message, fontSize = 13.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!notification.isRead) {
                    TextButton(onClick = onMarkRead) {
                        Icon(Icons.Default.Done, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(t("Read", "Oku"), color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(t("Delete", "Sil"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatNotificationTimestamp(timestamp: Long): String {
    val normalized = if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp
    return SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(normalized))
}
