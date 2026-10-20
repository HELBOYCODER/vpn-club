package dev.valid7996.narcicgeteway.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Dual counter-rotating Cyber Radar indicator with a golden core node,
 * matching the Narcic Getway Emerald & Gold brand identity.
 */
@Composable
fun CyberRadarIndicator(
    modifier: Modifier = Modifier.size(32.dp),
    accentColor: Color = Color(0xFFFFE066),
    secondaryColor: Color = LocalContentColor.current,
) {
    val transition = rememberInfiniteTransition(label = "cyber-radar")
    val outerAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outer-angle",
    )
    val innerAngle by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inner-angle",
    )

    Canvas(modifier = modifier) {
        val d = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)

        val outerStroke = (d * 0.095f).coerceAtLeast(1.8f)
        val innerStroke = (d * 0.08f).coerceAtLeast(1.5f)
        val outerRadius = d * 0.42f
        val innerRadius = d * 0.25f

        // Subtle track ring
        drawCircle(
            color = secondaryColor.copy(alpha = 0.16f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = outerStroke * 0.75f),
        )

        // Outer clockwise radar arcs (Golden)
        rotate(degrees = outerAngle, pivot = center) {
            val topLeft = Offset(cx - outerRadius, cy - outerRadius)
            val arcSize = Size(outerRadius * 2f, outerRadius * 2f)
            drawArc(
                color = accentColor,
                startAngle = -45f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accentColor.copy(alpha = 0.85f),
                startAngle = 135f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round),
            )
        }

        // Inner counter-clockwise radar arcs
        rotate(degrees = innerAngle, pivot = center) {
            val topLeft = Offset(cx - innerRadius, cy - innerRadius)
            val arcSize = Size(innerRadius * 2f, innerRadius * 2f)
            drawArc(
                color = secondaryColor.copy(alpha = 0.92f),
                startAngle = 20f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = innerStroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = secondaryColor.copy(alpha = 0.75f),
                startAngle = 200f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = innerStroke, cap = StrokeCap.Round),
            )
        }

        // Central glowing node
        drawCircle(
            color = accentColor,
            radius = (d * 0.08f).coerceAtLeast(1.5f),
            center = center,
        )
    }
}
