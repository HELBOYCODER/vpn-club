@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color

// Night-Ice palette: deep blue-slate surfaces with an electric ice accent.
private val IcePrimary = Color(0xFF2E6BE6)

val LightColors: ColorScheme = expressiveLightColorScheme().copy(
    primary = IcePrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001945),
    inversePrimary = Color(0xFFAAC7FF),

    secondary = Color(0xFF00696E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4EBEF),
    onSecondaryContainer = Color(0xFF002022),

    tertiary = Color(0xFFB4551B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF361000),
)

val NrgLime = Color(0xFF7DD8FF)
val NrgOnLime = Color(0xFF00213A)
val NrgHotPink = Color(0xFFFF8A5C)

/** The connected green of the brand — the dashboard wash, the active server, the name. */
val NrgGreen = Color(0xFF27A468)
val NrgViolet = Color(0xFF3D7BFF)
val NrgDeepViolet = Color(0xFF0A1830)
val NrgCyan = Color(0xFF4ED9E0)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF1E4A9E),
    onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = IcePrimary,

    secondary = Color(0xFF87D5DB),
    onSecondary = Color(0xFF003739),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFFB4EBEF),

    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF5A2000),
    tertiaryContainer = Color(0xFF7E3A0C),
    onTertiaryContainer = Color(0xFFFFDBCB),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0B111E),
    onBackground = Color(0xFFE1E6F0),
    surface = Color(0xFF0B111E),
    onSurface = Color(0xFFE1E6F0),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceTint = Color(0xFFAAC7FF),

    inverseSurface = Color(0xFFE1E6F0),
    inverseOnSurface = Color(0xFF2C303A),

    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF383D48),
    surfaceDim = Color(0xFF0B111E),
    surfaceContainerLowest = Color(0xFF060B14),
    surfaceContainerLow = Color(0xFF131926),
    surfaceContainer = Color(0xFF171D2B),
    surfaceContainerHigh = Color(0xFF222836),
    surfaceContainerHighest = Color(0xFF2D3342),
)
