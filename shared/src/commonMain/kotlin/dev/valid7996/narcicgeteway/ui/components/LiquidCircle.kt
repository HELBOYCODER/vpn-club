package dev.valid7996.narcicgeteway.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.ui.theme.LocalMotionBudget
import dev.valid7996.narcicgeteway.ui.theme.MotionBudget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The water circle: a closed loop of points whose radii breathe on two out-of-phase waves, drawn
 * fresh every frame and filled with the given gradient — a resting droplet that ripples as the
 * connection state changes. Shared by the home hero and the About sheet.
 */
@Composable
fun LiquidCircle(colors: List<Color>, modifier: Modifier = Modifier) {
    val budget = LocalMotionBudget.current
    val phase = if (budget == MotionBudget.Full) {
        rememberInfiniteTransition(label = "water").animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "water-phase",
        ).value
    } else {
        0.6f
    }
    Canvas(modifier) {
        val points = 30
        val step = (2.0 * PI).toFloat() / points
        val radius = size.minDimension / 2f * 0.90f
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun wobble(i: Int): Float = radius * (1f +
            0.05f * sin(phase * 1.6f + i * step * 2f) +
            0.03f * sin(2.3f * phase - i * step * 3f))
        val pts = List(points) { i ->
            val angle = step * i
            val r = wobble(i)
            Offset(cx + r * cos(angle), cy + r * sin(angle))
        }
        fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val path = Path()
        val first = mid(pts[points - 1], pts[0])
        path.moveTo(first.x, first.y)
        for (i in 0 until points) {
            val m = mid(pts[i], pts[(i + 1) % points])
            path.quadraticBezierTo(pts[i].x, pts[i].y, m.x, m.y)
        }
        path.close()
        drawPath(
            path,
            brush = Brush.linearGradient(
                colors,
                start = Offset(cx - radius, cy - radius),
                end = Offset(cx + radius, cy + radius),
            ),
        )
        // The light patch that makes the fill read as water rather than a flat disc.
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = radius * 0.55f,
            center = Offset(cx - radius * 0.22f, cy - radius * 0.28f),
        )
    }
}

/** Convenience wrapper: a liquid circle of [size] centered in its parent. */
@Composable
fun LiquidCircleBadge(colors: List<Color>, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier, contentAlignment = Alignment.Center) {
        LiquidCircle(colors = colors, modifier = Modifier.size(size))
        content()
    }
}
