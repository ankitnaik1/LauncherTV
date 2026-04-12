package com.example.launchtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LaunchTVTheme(
    content: @Composable () -> Unit,
) {
    // Gruvbox Dark Color Scheme optimized for Fire TV Stick (non-4K)
    // Using high contrast colors for better visibility on lower resolution screens
    val gruvboxColorScheme = darkColorScheme(
        primary = GruvboxOrange, // Primary action color
        onPrimary = GruvboxBg0_H,
        primaryContainer = GruvboxBg1,
        onPrimaryContainer = GruvboxOrange,
        secondary = GruvboxGreen,
        onSecondary = GruvboxBg0_H,
        secondaryContainer = GruvboxBg2,
        onSecondaryContainer = GruvboxGreen,
        tertiary = GruvboxYellow,
        onTertiary = GruvboxBg0_H,
        background = GruvboxBg0_H,
        onBackground = GruvboxFg,
        surface = GruvboxBg,
        onSurface = GruvboxFg,
        surfaceVariant = GruvboxBg1,
        onSurfaceVariant = GruvboxFg,
        error = GruvboxRed,
        onError = GruvboxBg0_H
        // Removed outline as it's not in TV M3 darkColorScheme
    )

    MaterialTheme(
        colorScheme = gruvboxColorScheme,
        typography = Typography,
        content = content
    )
}
