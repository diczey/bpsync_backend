package com.example.finalapp.ui.screen

import com.example.finalapp.ui.component.*


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.finalapp.data.model.UserDto
import com.example.finalapp.ui.navigation.Screen
import com.example.finalapp.ui.theme.*

@Composable
fun ProfileScreen(navController: NavController) {
    var isEditing by remember { mutableStateOf(false) }
    
    // User info states
    var name by remember { mutableStateOf("John Doe") }
    var age by remember { mutableStateOf("28") }
    var weight by remember { mutableStateOf("75") }
    var height by remember { mutableStateOf("178") }
    var email by remember { mutableStateOf("john@example.com") }

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
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                }
                
                IconButton(
                    onClick = { isEditing = !isEditing },
                    modifier = Modifier.size(44.dp).background(if (isEditing) SuccessGreen else Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                ) {
                    Icon(if (isEditing) Icons.Default.Save else Icons.Default.Edit, contentDescription = "Edit", tint = if (isEditing) Color.White else PrimaryBlue)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile Info Card
            PremiumGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(), 
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(80.dp).background(PrimaryGradient, RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isEditing) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                    } else {
                        Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    }
                    Text("Health ID: BP2024", fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableProfileStatItem(modifier = Modifier.weight(1f), label = "Age", value = age, onValueChange = { age = it }, icon = Icons.Default.CalendarToday, color = PrimaryBlue, isEditing = isEditing)
                EditableProfileStatItem(modifier = Modifier.weight(1f), label = "Gender", value = "Male", onValueChange = { /* gender not changeable yet */ }, icon = Icons.Default.Person, color = PurpleMain, isEditing = isEditing)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditableProfileStatItem(modifier = Modifier.weight(1f), label = "Weight (kg)", value = weight, onValueChange = { weight = it }, icon = Icons.Default.Scale, color = SuccessGreen, isEditing = isEditing)
                EditableProfileStatItem(modifier = Modifier.weight(1f), label = "Height (cm)", value = height, onValueChange = { height = it }, icon = Icons.Default.Straighten, color = OrangeMain, isEditing = isEditing)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info rows
            EditableInfoBlock(label = "Email", value = email, onValueChange = { email = it }, icon = Icons.Default.Email, isEditing = isEditing)
            EditableInfoBlock(label = "Phone", value = "+1 234 567 8900", onValueChange = { /* phone not changeable here */ }, icon = Icons.Default.Phone, isEditing = isEditing)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun EditableProfileStatItem(modifier: Modifier, label: String, value: String, onValueChange: (String) -> Unit, icon: ImageVector, color: Color, isEditing: Boolean) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(8.dp)) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditing) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    modifier = Modifier.width(80.dp)
                )
            } else {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun EditableInfoBlock(label: String, value: String, onValueChange: (String) -> Unit, icon: ImageVector, isEditing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                if (isEditing) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = PrimaryBlue),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                    )
                } else {
                    Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ForegroundBlack)
                }
            }
        }
    }
}
