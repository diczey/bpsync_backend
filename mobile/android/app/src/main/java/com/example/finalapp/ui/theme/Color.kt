package com.example.finalapp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Base Colors
val PrimaryBlue = Color(0xFF2D59F0)
val PrimaryBlueDark = Color(0xFF1E40AF)
val SuccessGreen = Color(0xFF10B981)
val SuccessGreenDark = Color(0xFF059669)
val ErrorRed = Color(0xFFEF4444)
val ErrorRedDark = Color(0xFFDC2626)
val ActivePink = Color(0xFFEC4899)
val ActivePinkDark = Color(0xFFDB2777)
val PurpleMain = Color(0xFF8B5CF6)
val PurpleDark = Color(0xFF7C3AED)
val CyanMain = Color(0xFF06B6D4)
val CyanDark = Color(0xFF0891B2)
val OrangeMain = Color(0xFFF59E0B)
val OrangeDark = Color(0xFFD97706)

// Background & Foreground
val BackgroundLight = Color(0xFFE9F1FC)
val BackgroundVia = Color(0xFFF5F8FB)
val BackgroundDark = Color(0xFFDDE5EF)
val ForegroundBlack = Color(0xFF000000)
val TextSecondary = Color(0xFF6B7280)
val TextMuted = Color(0xFF9CA3AF)
val BorderColor = Color(0x1A000000)

// Gradients
val PrimaryGradient = Brush.linearGradient(listOf(PrimaryBlue, PrimaryBlueDark))
val SuccessGradient = Brush.linearGradient(listOf(SuccessGreen, SuccessGreenDark))
val ErrorGradient = Brush.linearGradient(listOf(ErrorRed, ErrorRedDark))
val PinkGradient = Brush.linearGradient(listOf(ActivePink, ActivePinkDark))
val BackgroundGradient = Brush.verticalGradient(listOf(BackgroundLight, BackgroundVia, BackgroundDark))
val GlassGradient = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.5f)))