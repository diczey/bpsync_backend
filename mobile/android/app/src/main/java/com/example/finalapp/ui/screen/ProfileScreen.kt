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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.localization.rememberIsTurkish
import com.example.finalapp.ui.localization.translateGender
import com.example.finalapp.ui.localization.translateMessage
import com.example.finalapp.ui.component.PremiumGlassCard
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.OrangeMain
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PrimaryGradient
import com.example.finalapp.ui.theme.PurpleMain
import com.example.finalapp.ui.theme.SuccessGreen
import com.example.finalapp.ui.theme.TextMuted
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = viewModel()
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = t("Back", "Geri"), tint = PrimaryBlue)
                }

                IconButton(
                    onClick = viewModel::toggleEditing,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (uiState.isEditing) SuccessGreen else Color.White.copy(alpha = 0.8f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        if (uiState.isEditing) Icons.Default.Save else Icons.Default.Edit,
                        contentDescription = t("Edit", "Düzenle"),
                        tint = if (uiState.isEditing) Color.White else PrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            uiState.message?.let { message ->
                Text(
                    text = translateMessage(message, isTurkish),
                    color = if (uiState.isLoading) TextSecondary else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(PrimaryGradient, RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.isEditing) {
                        TextField(
                            value = uiState.name,
                            onValueChange = viewModel::updateName,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForegroundBlack,
                                textAlign = TextAlign.Center
                            )
                        )
                    } else {
                        Text(uiState.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }

                    Text(
                        "${t("Health ID", "Sağlık ID")}: ${uiState.healthId}",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableProfileStatItem(
                    modifier = Modifier.weight(1f),
                    label = t("Birth Date", "Doğum Tarihi"),
                    value = uiState.dateOfBirth,
                    onValueChange = viewModel::updateDateOfBirth,
                    icon = Icons.Default.CalendarToday,
                    color = PrimaryBlue,
                    isEditing = uiState.isEditing,
                    fieldWidth = 132.dp,
                    placeholder = "YYYY-MM-DD"
                )
                EditableProfileStatItem(
                    modifier = Modifier.weight(1f),
                    label = t("Age", "Yaş"),
                    value = uiState.age,
                    onValueChange = {},
                    icon = Icons.Default.CalendarToday,
                    color = PurpleMain,
                    isEditing = false
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableProfileStatItem(
                    modifier = Modifier.weight(1f),
                    label = t("Gender", "Cinsiyet"),
                    value = if (uiState.isEditing) uiState.gender else translateGender(uiState.gender, isTurkish),
                    onValueChange = viewModel::updateGender,
                    icon = Icons.Default.Person,
                    color = PurpleMain,
                    isEditing = uiState.isEditing
                )
                EditableProfileStatItem(
                    modifier = Modifier.weight(1f),
                    label = t("Weight (kg)", "Kilo (kg)"),
                    value = uiState.weight,
                    onValueChange = viewModel::updateWeight,
                    icon = Icons.Default.Scale,
                    color = SuccessGreen,
                    isEditing = uiState.isEditing
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            EditableProfileStatItem(
                modifier = Modifier.fillMaxWidth(),
                label = t("Height (cm)", "Boy (cm)"),
                value = uiState.height,
                onValueChange = viewModel::updateHeight,
                icon = Icons.Default.Straighten,
                color = OrangeMain,
                isEditing = uiState.isEditing,
                fieldWidth = 120.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            EditableInfoBlock(
                label = t("Email", "E-posta"),
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                icon = Icons.Default.Email,
                isEditing = uiState.isEditing
            )
            EditableInfoBlock(
                label = t("Phone", "Telefon"),
                value = "+1 234 567 8900",
                onValueChange = {},
                icon = Icons.Default.Phone,
                isEditing = false
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quick links
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileLinkRow(
                    icon = Icons.Default.Settings,
                    label = t("Settings", "Ayarlar"),
                    color = PrimaryBlue,
                    onClick = { navController.navigate(Screen.Settings.route) }
                )
                ProfileLinkRow(
                    icon = Icons.Default.Notifications,
                    label = t("Notifications", "Bildirimler"),
                    color = OrangeMain,
                    onClick = { navController.navigate(Screen.Notifications.route) }
                )
                ProfileLinkRow(
                    icon = Icons.Default.ExitToApp,
                    label = t("Logout", "Çıkış Yap"),
                    color = ErrorRed,
                    onClick = {
                        viewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProfileLinkRow(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(9.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = color)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ForegroundBlack)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun EditableProfileStatItem(
    modifier: Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    color: Color,
    isEditing: Boolean,
    fieldWidth: Dp = 80.dp,
    placeholder: String? = null
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditing) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder?.let { hint ->
                        {
                            Text(
                                text = hint,
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.width(fieldWidth),
                    singleLine = true
                )
            } else {
                Text(
                    value.ifBlank { "--" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun EditableInfoBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    isEditing: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                if (isEditing) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = PrimaryBlue
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForegroundBlack
                        )
                    )
                } else {
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                }
            }
        }
    }
}
