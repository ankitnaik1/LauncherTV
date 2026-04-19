package com.example.launchtv.ui.theme

import androidx.compose.ui.graphics.Color

// Apple TV OS inspired palette - refined for better visibility and translucency
val AppleDarkBg = Color(0xFF000000) // Pure black for OLED-like backgrounds
val AppleGray = Color(0xFF1C1C1E)
val AppleLightGray = Color(0xFF2C2C2E)
val AppleTextPrimary = Color(0xFFFFFFFF)
val AppleTextSecondary = Color(0xFFEBEBF5).copy(alpha = 0.6f)
val AppleAccent = Color(0xFFFFFFFF) 
val AppleSurface = Color(0xFF252525)

// Translucent colors for glass effect
val AppleGlass = Color(0xFF1C1C1E).copy(alpha = 0.8f)
val AppleGlassLight = Color(0xFFFFFFFF).copy(alpha = 0.15f)
val AppleGlassFocus = Color(0xFFFFFFFF).copy(alpha = 0.95f)

// Keep Gruvbox for reference
val GruvboxBg = Color(0xFF282828)
val GruvboxFg = Color(0xFFEBDBB2)
val GruvboxBg0_H = Color(0xFF1D2021)
val GruvboxBg1 = Color(0xFF3C3836)
val GruvboxBg2 = Color(0xFF504945)
