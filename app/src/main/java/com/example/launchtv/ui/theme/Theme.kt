package com.example.launchtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LaunchTVTheme(
    content: @Composable () -> Unit,
) {
    // Apple TV OS inspired Dark Color Scheme
    val appleColorScheme = darkColorScheme(
        primary = Color.White, // Apple's focus is usually pure white/brightest
        onPrimary = Color.Black,
        primaryContainer = AppleGray,
        onPrimaryContainer = Color.White,
        secondary = AppleLightGray,
        onSecondary = Color.White,
        secondaryContainer = AppleSurface,
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFF007AFF), // Apple Blue for accent
        onTertiary = Color.White,
        background = AppleDarkBg,
        onBackground = Color.White,
        surface = AppleGray,
        onSurface = Color.White,
        surfaceVariant = AppleLightGray,
        onSurfaceVariant = Color.White,
        error = Color(0xFFFF453A), // Apple System Red
        onError = Color.White
    )

    MaterialTheme(
        colorScheme = appleColorScheme,
        typography = Typography,
        content = content
    )
}
