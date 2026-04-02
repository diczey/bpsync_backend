package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*

@Composable
fun RegisterScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }

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
            
            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(PrimaryGradient, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Logo", tint = Color.White, modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
            Text("Join BP Sync today", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.height(32.dp))

            // registration Form
            GlassInput(value = name, onValueChange = { name = it }, placeholder = "Full Name", icon = Icons.Default.Person)
            Spacer(modifier = Modifier.height(16.dp))
            
            
            // Input Fields
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Name Field
                GlassInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Full Name",
                    icon = Icons.Default.Person
                )

                // Email Field
                GlassInput(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email Address",
                    icon = Icons.Default.Email
                )
                
                // Password Field
                GlassInput(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    icon = Icons.Default.Lock,
                    isPassword = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body Metrics row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                var weight by remember { mutableStateOf("") }
                var height by remember { mutableStateOf("") }
                Box(modifier = Modifier.weight(1f)) {
                    GlassInput(value = weight, onValueChange = { weight = it }, placeholder = "Weight (kg)", icon = Icons.Default.Scale)
                }
                Box(modifier = Modifier.weight(1f)) {
                    GlassInput(value = height, onValueChange = { height = it }, placeholder = "Height (cm)", icon = Icons.Default.Straighten)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            var gender by remember { mutableStateOf("") }
            GlassInput(value = gender, onValueChange = { gender = it }, placeholder = "Gender (Male/Female)", icon = Icons.Default.Person)

            Spacer(modifier = Modifier.height(16.dp))

            // Date of Birth Field - MOVED TO BOTTOM
            var dob by remember { mutableStateOf("") }
            val context = androidx.compose.ui.platform.LocalContext.current
            val calendar = java.util.Calendar.getInstance()
            val datePickerDialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    dob = "$dayOfMonth/${month + 1}/$year"
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )

            Box(modifier = Modifier.fillMaxWidth().clickable { datePickerDialog.show() }) {
                GlassInput(
                    value = dob,
                    onValueChange = { },
                    placeholder = "Date of Birth (Day/Month/Year)",
                    icon = Icons.Default.CalendarToday,
                    enabled = false
                )
                Box(modifier = Modifier.matchParentSize())
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { navController.navigate(Screen.Dashboard.route) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun GlassInputSmall(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = TextMuted, fontSize = 12.sp) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
