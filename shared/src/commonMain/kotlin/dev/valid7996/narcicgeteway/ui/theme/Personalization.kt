@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.valid7996.narcicgeteway.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.CardCornerStyle
import dev.valid7996.narcicgeteway.domain.model.ListDensity
import dev.valid7996.narcicgeteway.domain.model.TrafficCardStyle
import dev.valid7996.narcicgeteway.domain.model.TrafficTileSize

data class AccentPreset(val name: String, val light: Color, val dark: Color)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("Narcis", Color(0xFF0A7B58), Color(0xFF6FDBA8)),
    AccentPreset("Teal", Color(0xFF00897E), Color(0xFF52DDCF)),
    AccentPreset("Ocean", Color(0xFF1E63D0), Color(0xFFAAC7FF)),
    AccentPreset("Forest", Color(0xFF2E7D32), Color(0xFF9BD89B)),
    AccentPreset("Amber", Color(0xFFB4690E), Color(0xFFFFB870)),
    AccentPreset("Rose", Color(0xFFC4326B), Color(0xFFFFB0C8)),
    AccentPreset("Crimson", Color(0xFFC0362C), Color(0xFFFFB4A9)),
    AccentPreset("Cyan", Color(0xFF00838F), Color(0xFF4DD0E1)),
    AccentPreset("Grape", Color(0xFF8E24AA), Color(0xFFE9B3FF)),
    AccentPreset("Slate", Color(0xFF4A5B7A), Color(0xFFB6C6EA)),
)

fun Color.readableOn(): Color =
    if (luminance() > 0.5f) Color(0xFF10131A) else Color.White

private fun onColorFor(color: Color): Color = color.readableOn()

fun ColorScheme.withAccent(index: Int, dark: Boolean): ColorScheme {
    val preset = AccentPresets.getOrNull(index) ?: return this
    val base = if (dark) preset.dark else preset.light
    val container = if (dark) lerp(base, Color.Black, 0.55f) else lerp(base, Color.White, 0.82f)
    val onContainer = if (dark) lerp(base, Color.White, 0.85f) else lerp(base, Color.Black, 0.6f)
    return copy(
        primary = base,
        onPrimary = onColorFor(base),
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = if (dark) preset.light else preset.dark,
        surfaceTint = base,
    )
}

fun ColorScheme.withThemeProfile(
    profileId: String?,
    accentIndex: Int,
    dark: Boolean,
): ColorScheme {
    val profile = themeProfileOf(profileId)
    val baseAccentColor = when (profile?.id) {
        "hellboy" -> Color(0xFFE8742A)
        "azkaban" -> Color(0xFF38BDF8)
        "noir" -> Color(0xFFFFFFFF)
        "blossom" -> Color(0xFFF472B6)
        "narcis" -> Color(0xFF0F9D6C)
        else -> AccentPresets.getOrNull(accentIndex)?.let { if (dark) it.dark else it.light } ?: Color(0xFF0F9D6C)
    }

    val primary = baseAccentColor
    val onPrimary = onColorFor(primary)
    val primaryContainer = if (dark) {
        when (profile?.id) {
            "hellboy" -> Color(0xFF2A3131)
            "azkaban" -> Color(0xFF0A1628)
            "noir" -> Color(0xFF18181B)
            "blossom" -> Color(0xFF2A101D)
            "narcis" -> Color(0xFF0A2E22)
            else -> lerp(primary, Color.Black, 0.55f)
        }
    } else {
        lerp(primary, Color.White, 0.82f)
    }
    val onPrimaryContainer = if (dark) {
        when (profile?.id) {
            "noir" -> Color(0xFFF4F4F5)
            else -> lerp(primary, Color.White, 0.85f)
        }
    } else {
        lerp(primary, Color.Black, 0.6f)
    }

    val themeBackground = if (dark) {
        when (profile?.id) {
            "azkaban" -> Color(0xFF070E1A)
            "noir" -> Color(0xFF000000)
            "blossom" -> Color(0xFF140810)
            "narcis" -> Color(0xFF06140E)
            else -> Color(0xFF0B111E)
        }
    } else {
        Color(0xFFFAFAFC)
    }

    val themeSurface = if (dark) {
        when (profile?.id) {
            "azkaban" -> Color(0xFF091322)
            "noir" -> Color(0xFF121214)
            "blossom" -> Color(0xFF180A12)
            "narcis" -> Color(0xFF0B2A20)
            else -> Color(0xFF0B111E)
        }
    } else {
        Color(0xFFFFFFFF)
    }

    val themeSurfaceContainer = if (dark) {
        when (profile?.id) {
            "azkaban" -> Color(0xFF0A1628)
            "noir" -> Color(0xFF18181B)
            "blossom" -> Color(0xFF210E1A)
            "narcis" -> Color(0xFF0D2F24)
            else -> Color(0xFF171D2B)
        }
    } else {
        Color(0xFFF0F1F5)
    }

    val themeSurfaceContainerHigh = if (dark) {
        when (profile?.id) {
            "azkaban" -> Color(0xFF0E1F36)
            "noir" -> Color(0xFF202024)
            "blossom" -> Color(0xFF2D1424)
            "narcis" -> Color(0xFF123B2D)
            else -> Color(0xFF222836)
        }
    } else {
        Color(0xFFE8E9EF)
    }

    return copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = primary,
        surfaceTint = primary,
        background = themeBackground,
        surface = themeSurface,
        surfaceContainer = themeSurfaceContainer,
        surfaceContainerHigh = themeSurfaceContainerHigh,
        surfaceDim = themeBackground,
    )
}

fun ColorScheme.amoled(dark: Boolean): ColorScheme {
    if (!dark) return this
    val black = Color(0xFF000000)
    val bright = Color(0xFF1A1A1A)
    val low = Color(0xFF0B0B0B)
    val container = Color(0xFF0F0F0F)
    val high = Color(0xFF161616)
    val highest = Color(0xFF1E1E1E)
    return copy(
        background = black,
        surface = black,
        surfaceBright = bright,
        surfaceDim = black,
        surfaceContainer = container,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
        surfaceContainerLow = low,
        surfaceContainerLowest = black,
    )
}

data class Personalization(
    val downloadColor: Color? = null,
    val uploadColor: Color? = null,

    val downloadTileColor: Color? = null,
    val uploadTileColor: Color? = null,
    val connectColor: Color? = null,
    val connectedColor: Color? = null,
    val tapHintColor: Color? = null,

    val configCardColor: Color? = null,
    val configCardTextColor: Color? = null,
    val serverCardColor: Color? = null,
    val serverActiveColor: Color? = null,
    val cornerStyle: CardCornerStyle = CardCornerStyle.Rounded,
    val density: ListDensity = ListDensity.Comfortable,
    val showServerPing: Boolean = true,
    val showServerUsage: Boolean = true,
    val monospaceAddress: Boolean = false,

    val showTrafficTiles: Boolean = true,
    val trafficTilesAboveHero: Boolean = false,
    val trafficTileSize: TrafficTileSize = TrafficTileSize.Normal,
    val trafficCardStyle: TrafficCardStyle = TrafficCardStyle.Cards,
    val themeProfileId: String? = null,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = false,
) {
    companion object {
        val Default = Personalization()
    }
}

fun AppSettings.toPersonalization(): Personalization = Personalization(
    downloadColor = customDownloadColor?.argbLongToColor(),
    uploadColor = customUploadColor?.argbLongToColor(),
    downloadTileColor = customDownloadTileColor?.argbLongToColor(),
    uploadTileColor = customUploadTileColor?.argbLongToColor(),
    connectColor = customConnectColor?.argbLongToColor(),
    connectedColor = customConnectedColor?.argbLongToColor(),
    tapHintColor = customTapHintColor?.argbLongToColor(),
    configCardColor = customConfigCardColor?.argbLongToColor(),
    configCardTextColor = customConfigCardTextColor?.argbLongToColor(),
    serverCardColor = customServerCardColor?.argbLongToColor(),
    serverActiveColor = customServerActiveColor?.argbLongToColor(),
    cornerStyle = cardCornerStyle,
    density = listDensity,
    showServerPing = showServerPing,
    showServerUsage = showServerUsage,
    monospaceAddress = monospaceAddress,
    showTrafficTiles = showTrafficTiles,
    trafficTilesAboveHero = trafficTilesAboveHero,
    trafficTileSize = trafficTileSize,
    trafficCardStyle = trafficCardStyle,
    themeProfileId = themeProfileId,
    amoledBlack = amoledBlack,
    dynamicColor = dynamicColor,
)

fun AppSettings.resetPersonalization(): AppSettings = copy(
    customDownloadColor = null,
    customUploadColor = null,
    customDownloadTileColor = null,
    customUploadTileColor = null,
    customConnectColor = null,
    customConnectedColor = null,
    customTapHintColor = null,
    customConfigCardColor = null,
    customConfigCardTextColor = null,
    customServerCardColor = null,
    customServerActiveColor = null,

    themeProfileId = null,
    cardCornerStyle = CardCornerStyle.Rounded,
    listDensity = ListDensity.Comfortable,
    showServerPing = true,
    showServerUsage = true,
    monospaceAddress = false,
)

val AppSettings.hasPersonalization: Boolean
    get() = customDownloadColor != null || customUploadColor != null ||
        customDownloadTileColor != null || customUploadTileColor != null || customConnectColor != null ||
        customConnectedColor != null || customTapHintColor != null ||
        customConfigCardColor != null || customConfigCardTextColor != null ||
        customServerCardColor != null || customServerActiveColor != null ||
        cardCornerStyle != CardCornerStyle.Rounded || listDensity != ListDensity.Comfortable ||
        !showServerPing || !showServerUsage || monospaceAddress

fun goodPingColor(container: Color): Color =
    if (container.luminance() > 0.5f) Color(0xFF1B7A3C) else NrgLime

fun fairPingColor(container: Color): Color =
    if (container.luminance() > 0.5f) Color(0xFF8A5A00) else Color(0xFFFFC46B)

fun pingColor(ping: Int, container: Color, error: Color = Color(0xFFD1453B)): Color = when {
    ping < 0 -> error
    ping < 200 -> goodPingColor(container)
    ping < 500 -> fairPingColor(container)
    else -> error
}
