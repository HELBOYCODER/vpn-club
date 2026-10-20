package dev.valid7996.narcicgeteway.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.montaa_sheet
import org.jetbrains.compose.resources.painterResource

/**
 * The Montaa Hero Element for the Noir (Monochrome) Template:
 * Renders the Montaa character animation in pristine high definition.
 * Strictly free-floating and borderless with NO added overlays or spinning badges.
 * Moves gently and calmly when connecting or connected; rests peacefully when idle.
 */
@Composable
fun MontaaHero(
    state: ConnectionState,
    size: Dp = 260.dp,
    onTapHero: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isConnected = state.isActive
    val isConnecting = state.isTransitioning
    val isMoving = isConnected || isConnecting

    val transition = rememberInfiniteTransition(label = "montaa-hero")

    val frameProgress by if (isMoving) {
        val animDuration = if (isConnecting) 4500 else 6000
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 29.99f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = animDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "montaa-frame",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val breatheScale by if (isMoving) {
        transition.animateFloat(
            initialValue = 0.98f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "montaa-breathe",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val auraPulse by if (isMoving) {
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "montaa-aura-pulse",
        )
    } else {
        remember { mutableFloatStateOf(0.18f) }
    }

    val painter = painterResource(Res.drawable.montaa_sheet)
    val currentFrame by remember(isMoving) {
        derivedStateOf {
            if (isMoving) frameProgress.toInt().coerceIn(0, 29) else 0
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = breatheScale
                scaleY = breatheScale
            }
            .then(
                if (onTapHero != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapHero,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft ambient back glow (monochrome, soft and gentle) rendered in draw phase
        Box(
            modifier = Modifier
                .size(size * 0.78f)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = if (isMoving) 0.14f * auraPulse else 0.04f),
                                Color(0xFF27272A).copy(alpha = if (isMoving) 0.08f else 0.02f),
                                Color.Transparent,
                            ),
                        ),
                    )
                },
        )

        // Montaa 30-frame sprite renderer (6 columns x 5 rows at 512x512)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = 6
            val rows = 5
            val frameX = currentFrame % cols
            val frameY = currentFrame / cols

            clipRect(0f, 0f, this.size.width, this.size.height) {
                withTransform({
                    translate(
                        left = -frameX * this@Canvas.size.width,
                        top = -frameY * this@Canvas.size.height,
                    )
                }) {
                    with(painter) {
                        draw(
                            size = Size(this@Canvas.size.width * cols, this@Canvas.size.height * rows),
                        )
                    }
                }
            }
        }
    }
}
