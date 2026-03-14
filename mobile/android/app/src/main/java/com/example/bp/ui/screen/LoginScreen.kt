package com.example.bp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.AuthUiState
import com.example.bp.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess && isLoginMode) {
            onLoginSuccess()
        } else if (uiState.isSuccess && !isLoginMode) {
            // After register, switch to login mode
            isLoginMode = true
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Gradient top accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BPBlueDark.copy(alpha = 0.7f),
                            BackgroundDark
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo area
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "💓",
                        fontSize = 56.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "BPSync",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Kan Basıncı Takip Sistemi",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tab row: Login / Register
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDark),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Giriş Yap" to true, "Kayıt Ol" to false).forEach { (label, isLogin) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isLoginMode == isLogin) BPBlue else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(onClick = {
                                    isLoginMode = isLogin
                                    viewModel.clearError()
                                }) {
                                    Text(
                                        text = label,
                                        color = if (isLoginMode == isLogin) TextPrimary else TextSecondary,
                                        fontWeight = if (isLoginMode == isLogin) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    var name by remember { mutableStateOf("") }
                    var dateOfBirth by remember { mutableStateOf("") } // YYYY-MM-DD
                    var gender by remember { mutableStateOf("") }
                    var weight by remember { mutableStateOf("") }
                    var height by remember { mutableStateOf("") }

                    // Email (always shown)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", color = TextHint) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = BPBlueLight)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = bpTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Name (Register only)
                    if (!isLoginMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Ad Soyad", color = TextHint) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = BPBlueLight)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = bpTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Password (always shown)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Şifre", color = TextHint) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BPBlueLight)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        colors = bpTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Extra fields for Register
                    if (!isLoginMode) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dateOfBirth,
                                onValueChange = { dateOfBirth = it },
                                label = { Text("Doğum Tarihi", color = TextHint, fontSize = 12.sp) },
                                placeholder = { Text("YYYY-MM-DD", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                colors = bpTextFieldColors(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = gender,
                                onValueChange = { gender = it },
                                label = { Text("Cinsiyet", color = TextHint, fontSize = 12.sp) },
                                placeholder = { Text("E/K", fontSize = 12.sp) },
                                modifier = Modifier.weight(0.6f),
                                colors = bpTextFieldColors(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("Kilo (kg)", color = TextHint, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = bpTextFieldColors(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = height,
                                onValueChange = { height = it },
                                label = { Text("Boy (cm)", color = TextHint, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = bpTextFieldColors(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // Error message
                    uiState.error?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BPRed.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = err,
                                color = BPRed,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Submit button
                    Button(
                        onClick = {
                            if (isLoginMode) {
                                viewModel.login(email, password)
                            } else {
                                val request = com.example.bp.data.model.RegisterRequest(
                                    email = email,
                                    password = password,
                                    name = if (name.isEmpty()) email.split("@")[0] else name,
                                    dateOfBirth = dateOfBirth.ifEmpty { null },
                                    gender = gender.ifEmpty { null },
                                    weight = weight.ifEmpty { null },
                                    height = height.ifEmpty { null },
                                    bloodType = null,
                                    emergencyContact = null
                                )
                                viewModel.register(request)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = BPBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isLoginMode) "Giriş Yap" else "Kayıt Ol",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun bpTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BPBlue,
    unfocusedBorderColor = DividerDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = BPBlueLight,
    focusedLabelColor = BPBlueLight,
    unfocusedLabelColor = TextHint
)
