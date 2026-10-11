package com.helboy.vpnclub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** پالت برنامه — دو نسخه‌ی شب و روز، هماهنگ با رنگ‌های آتشین هلبوی. */
data class ClubPalette(
    val bg: Color, val bgTop: Color, val bgBottom: Color,
    val card: Color, val cardElevated: Color, val border: Color,
    val accent: Color, val accentMuted: Color, val indigo: Color, val purple: Color,
    val green: Color, val amber: Color, val red: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val onAccent: Color, val track: Color, val isDark: Boolean,
)

val NightPalette = ClubPalette(
    bg = Color(0xFF0B0B12), bgTop = Color(0xFF07070C), bgBottom = Color(0xFF1A0B0B),
    card = Color(0xFF15141F), cardElevated = Color(0xFF1E1B2A), border = Color(0xFF2E2A3D),
    accent = Color(0xFFFF6A2B), accentMuted = Color(0xFF9A3412), indigo = Color(0xFF818CF8), purple = Color(0xFFA855F7),
    green = Color(0xFF34D399), amber = Color(0xFFFBBF24), red = Color(0xFFF87171),
    textPrimary = Color(0xFFF8F7FB), textSecondary = Color(0xFFA6A3B5), textTertiary = Color(0xFF6E6A80),
    onAccent = Color(0xFF16090A), track = Color(0xFF2A2536), isDark = true,
)

val DayPalette = ClubPalette(
    bg = Color(0xFFFBF7F4), bgTop = Color(0xFFFFFFFF), bgBottom = Color(0xFFFCE9DF),
    card = Color(0xFFFFFFFF), cardElevated = Color(0xFFF6EFEA), border = Color(0xFFEADCD3),
    accent = Color(0xFFE2521B), accentMuted = Color(0xFFF7C3A8), indigo = Color(0xFF4F46E5), purple = Color(0xFF9333EA),
    green = Color(0xFF059669), amber = Color(0xFFB45309), red = Color(0xFFDC2626),
    textPrimary = Color(0xFF1C1917), textSecondary = Color(0xFF57534E), textTertiary = Color(0xFF8A817A),
    onAccent = Color(0xFFFFFFFF), track = Color(0xFFEADCD3), isDark = false,
)

val LocalPalette = staticCompositionLocalOf { NightPalette }

/** دسترسی کوتاه — نام‌های قبلی حفظ شده تا همه‌ی صفحه‌ها خودکار تم‌پذیر شوند. */
val Palette: ClubPalette @Composable @ReadOnlyComposable get() = LocalPalette.current
val DarkBg: Color @Composable @ReadOnlyComposable get() = Palette.bg
val CardBg: Color @Composable @ReadOnlyComposable get() = Palette.card
val CardBgElevated: Color @Composable @ReadOnlyComposable get() = Palette.cardElevated
val BorderDark: Color @Composable @ReadOnlyComposable get() = Palette.border
val NeonCyan: Color @Composable @ReadOnlyComposable get() = Palette.accent
val NeonCyanMuted: Color @Composable @ReadOnlyComposable get() = Palette.accentMuted
val NeonIndigo: Color @Composable @ReadOnlyComposable get() = Palette.indigo
val NeonPurple: Color @Composable @ReadOnlyComposable get() = Palette.purple
val StatusGreen: Color @Composable @ReadOnlyComposable get() = Palette.green
val StatusAmber: Color @Composable @ReadOnlyComposable get() = Palette.amber
val StatusRed: Color @Composable @ReadOnlyComposable get() = Palette.red
val TextPrimary: Color @Composable @ReadOnlyComposable get() = Palette.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = Palette.textSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = Palette.textTertiary
