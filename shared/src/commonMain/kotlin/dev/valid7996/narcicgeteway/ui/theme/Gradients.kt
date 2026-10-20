package dev.valid7996.narcicgeteway.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object NrgGradients {
    val connected: Brush
        get() = Brush.linearGradient(
            colors = listOf(NrgViolet, NrgCyan, NrgLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    private val sessionPalettes: List<List<Color>> = listOf(
        listOf(NrgViolet, NrgCyan, NrgLime),
        listOf(Color(0xFF2E6BE6), NrgCyan, Color(0xFF7DD8FF)),
        listOf(NrgDeepViolet, NrgViolet, NrgCyan),
        listOf(NrgCyan, NrgLime, Color(0xFF4ED9E0)),
        listOf(Color(0xFF1B3A8A), NrgViolet, NrgCyan),
    )

    fun forSession(sessionId: Int): Brush = Brush.linearGradient(
        colors = paletteFor(sessionId),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    private fun paletteFor(sessionId: Int): List<Color> =
        sessionPalettes[((sessionId % sessionPalettes.size) + sessionPalettes.size) % sessionPalettes.size]

    fun connectedStops(sessionId: Int): List<Color> =
        listOf(NrgDeepViolet) + paletteFor(sessionId)

    val connectingStops: List<Color> = listOf(NrgDeepViolet, Color(0xFF1E4A9E), NrgCyan)

    val idle: Brush
        get() = Brush.linearGradient(
            colors = listOf(NrgDeepViolet, Color(0xFF173B7C), NrgViolet.copy(alpha = 0.75f)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )

    val connecting: Brush
        get() = Brush.linearGradient(
            colors = listOf(Color(0xFF1E4A9E), NrgCyan, NrgLime),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
}
