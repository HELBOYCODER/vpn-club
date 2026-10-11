package com.helboy.vpnclub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** حالت تم انتخابی کاربر: system | light | dark (قابل تغییر زنده از تنظیمات). */
object ThemeState {
    var mode by mutableStateOf("system")
    /** ظاهر دکمه‌ی اتصال: hellboy | orb */
    var buttonStyle by mutableStateOf("hellboy")
    /** قطره‌های خون و آتش روی پوسته */
    var effects by mutableStateOf(true)
}

@Composable
fun VPNClubTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeState.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) NightPalette else DayPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentMuted,
        secondary = p.indigo, background = p.bg, surface = p.card, surfaceVariant = p.cardElevated,
        onSurface = p.textPrimary, onBackground = p.textPrimary, outline = p.border,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentMuted,
        secondary = p.indigo, background = p.bg, surface = p.card, surfaceVariant = p.cardElevated,
        onSurface = p.textPrimary, onBackground = p.textPrimary, outline = p.border,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
