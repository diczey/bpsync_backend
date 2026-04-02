package com.example.finalapp.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.finalapp.ui.component.GlassInput
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.BackgroundGradient
import com.example.finalapp.ui.theme.ForegroundBlack
import com.example.finalapp.ui.theme.PrimaryBlue
import com.example.finalapp.ui.theme.PrimaryGradient
import com.example.finalapp.ui.theme.TextSecondary
import com.example.finalapp.ui.viewmodel.RegisterViewModel
import java.util.Calendar

@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: RegisterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            viewModel.updateDateOfBirth("$dayOfMonth/${month + 1}/$year")
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(uiState.isRegistered) {
        if (uiState.isRegistered) {
            viewModel.onNavigationHandled()
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(PrimaryGradient, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            Text("Join BP Sync today", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassInput(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    placeholder = "Full Name",
                    icon = Icons.Default.Person
                )
                GlassInput(
                    value = uiState.email,
                    onValueChange = viewModel::updateEmail,
                    placeholder = "Email Address",
                    icon = Icons.Default.Email
                )
                GlassInput(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    placeholder = "Password",
                    icon = Icons.Default.Lock,
                    isPassword = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    GlassInput(
                        value = uiState.weight,
                        onValueChange = viewModel::updateWeight,
                        placeholder = "Weight (kg)",
                        icon = Icons.Default.Scale
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassInput(
                        value = uiState.height,
                        onValueChange = viewModel::updateHeight,
                        placeholder = "Height (cm)",
                        icon = Icons.Default.Straighten
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassInput(
                value = uiState.gender,
                onValueChange = viewModel::updateGender,
                placeholder = "Gender (Male/Female)",
                icon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() }
            ) {
                GlassInput(
                    value = uiState.dateOfBirth,
                    onValueChange = {},
                    placeholder = "Date of Birth (Day/Month/Year)",
                    icon = Icons.Default.CalendarToday,
                    enabled = false
                )
                Box(modifier = Modifier.matchParentSize())
            }

            Spacer(modifier = Modifier.height(20.dp))

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = viewModel::register,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account? ", color = TextSecondary, fontSize = 14.sp)
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Sign In", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
