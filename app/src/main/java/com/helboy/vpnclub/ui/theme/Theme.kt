package com.helboy.vpnclub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkBg,
    primaryContainer = NeonCyanMuted,
    secondary = NeonIndigo,
    onSecondary = TextPrimary,
    background = DarkBg,
    surface = CardBg,
    surfaceVariant = CardBgElevated,
    onSurface = TextPrimary,
    outline = BorderDark
)

@Composable
fun VPNClubTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
