package com.example.bp.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bp.data.model.ProfileUpdateRequest
import com.example.bp.data.model.UserDto
import com.example.bp.ui.theme.*
import com.example.bp.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    token: String,
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Form states
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }

    LaunchedEffect(token) {
        viewModel.loadProfile(token)
    }

    // Populate form when user data is loaded
    LaunchedEffect(uiState.user) {
        uiState.user?.let { user ->
            name = user.name
            dob = user.dateOfBirth ?: ""
            gender = user.gender ?: ""
            weight = user.weight ?: ""
            height = user.height ?: ""
            bloodType = user.bloodType ?: ""
            emergencyContact = user.emergencyContact ?: ""
        }
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Profil Bilgileri", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = TextPrimary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.updateProfile(
                                token,
                                ProfileUpdateRequest(
                                    name = name,
                                    dateOfBirth = dob.ifEmpty { null },
                                    gender = gender.ifEmpty { null },
                                    weight = weight.ifEmpty { null },
                                    height = height.ifEmpty { null },
                                    bloodType = bloodType.ifEmpty { null },
                                    emergencyContact = emergencyContact.ifEmpty { null }
                                )
                            )
                        },
                        enabled = !uiState.isLoading
                    ) {
                        Text("KAYDET", color = BPBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Profile Pic (Placeholder)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BPBlue, BPBlueDark))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.user != null && uiState.user!!.name.isNotEmpty()) 
                           uiState.user!!.name.first().uppercase() else "U",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = uiState.user?.email ?: "Yukleniyor...",
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Info Section
            ProfileSectionTitle("Kişisel Bilgiler")
            
            ProfileField(
                label = "Ad Soyad",
                value = name,
                onValueChange = { name = it },
                icon = Icons.Default.Person
            )
            
            ProfileField(
                label = "Doğum Tarihi (YYYY-MM-DD)",
                value = dob,
                onValueChange = { dob = it },
                icon = Icons.Default.CalendarToday,
                placeholder = "1990-01-01"
            )
            
            ProfileField(
                label = "Cinsiyet",
                value = gender,
                onValueChange = { gender = it },
                icon = Icons.Default.Wc,
                placeholder = "Erkek / Kadın"
            )

            Spacer(modifier = Modifier.height(24.dp))
            ProfileSectionTitle("Vücut Bilgileri")
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileField(
                    modifier = Modifier.weight(1f),
                    label = "Ağırlık (kg)",
                    value = weight,
                    onValueChange = { weight = it },
                    icon = Icons.Default.MonitorWeight,
                    keyboardType = KeyboardType.Number
                )
                ProfileField(
                    modifier = Modifier.weight(1f),
                    label = "Boy (cm)",
                    value = height,
                    onValueChange = { height = it },
                    icon = Icons.Default.Height,
                    keyboardType = KeyboardType.Number
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            ProfileSectionTitle("Sağlık & İletişim")
            
            ProfileField(
                label = "Kan Grubu",
                value = bloodType,
                onValueChange = { bloodType = it },
                icon = Icons.Default.Bloodtype,
                placeholder = "A Rh+"
            )
            
            ProfileField(
                label = "Acil Durum Kişisi",
                value = emergencyContact,
                onValueChange = { emergencyContact = it },
                icon = Icons.Default.Phone
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(uiState.error!!, color = BPRed, fontSize = 13.sp)
            }
            
            if (uiState.isUpdated) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Profil Başarıyla Güncellendi", color = BPGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BPBlue)
            }
        }
    }
}

@Composable
fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        color = BPBlueLight,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@Composable
fun ProfileField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp, color = TextHint) },
        leadingIcon = { ProfileIcon(icon, contentDescription = null, size = 20.dp, tint = TextSecondary) },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BPBlue,
            unfocusedBorderColor = DividerDark,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            unfocusedLabelColor = TextSecondary,
            focusedLabelColor = BPBlueLight
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}

@Composable
private fun ProfileIcon(icon: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: Color) {
    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = tint
    )
}
