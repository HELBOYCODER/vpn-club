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

/** شب: بتن تیره‌ی سبز-خاکستری دیوار عکس + نارنجی گرافیتی «Hell Boy». */
val NightPalette = ClubPalette(
    bg = Color(0xFF222929), bgTop = Color(0xCC0E1212), bgBottom = Color(0xD9100C0A),
    card = Color(0xC71C2222), cardElevated = Color(0xD92A3131), border = Color(0x66727C78),
    accent = Color(0xFFE8742A), accentMuted = Color(0xFF783312), indigo = Color(0xFF9AA7FF), purple = Color(0xFFC08BFF),
    green = Color(0xFF4ADE80), amber = Color(0xFFFBBF24), red = Color(0xFFF87171),
    textPrimary = Color(0xFFF2EFEA), textSecondary = Color(0xFFB4BAB6), textTertiary = Color(0xFF828C88),
    onAccent = Color(0xFF1A0D06), track = Color(0x55727C78), isDark = true,
)

/** روز: بتن روشن + همان نارنجی گرافیتی (کمی تیره‌تر برای خوانایی). */
val DayPalette = ClubPalette(
    bg = Color(0xFFA8AAA5), bgTop = Color(0x99FFFFFF), bgBottom = Color(0x80E9DDD2),
    card = Color(0xD9FFFFFF), cardElevated = Color(0xE6F3F1EC), border = Color(0x80A3A8A2),
    accent = Color(0xFFC2551B), accentMuted = Color(0xFFF2C2A2), indigo = Color(0xFF4F46E5), purple = Color(0xFF9333EA),
    green = Color(0xFF15803D), amber = Color(0xFFB45309), red = Color(0xFFDC2626),
    textPrimary = Color(0xFF1F2422), textSecondary = Color(0xFF4A524E), textTertiary = Color(0xFF6B736F),
    onAccent = Color(0xFFFFFFFF), track = Color(0x80A3A8A2), isDark = false,
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
