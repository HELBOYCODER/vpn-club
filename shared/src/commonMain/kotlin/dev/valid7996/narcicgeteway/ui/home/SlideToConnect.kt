@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.valid7996.narcicgeteway.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import dev.valid7996.narcicgeteway.ui.components.CyberRadarIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.theme.NrgCyan
import dev.valid7996.narcicgeteway.ui.theme.NrgViolet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The swipe control: a track with a knob the user drags. Dragging the knob to the far end
 * connects, dragging it back disconnects; when the tunnel is up a tap anywhere lets go.
 * The slide stays left-to-right in every locale.
 */
@Composable
internal fun SlideToConnect(
    state: ConnectionState,
    onToggle: () -> Unit,
    customColor: Color? = null,
    customActiveColor: Color? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        val density = LocalDensity.current

        val height = if (compact) 56.dp else 64.dp
        val knobSize = height - 10.dp
        val knobPx = with(density) { knobSize.toPx() }

        var trackWidth by remember { mutableStateOf(1f) }
        val maxOffset = (trackWidth - knobPx).coerceAtLeast(0f)
        val offset = remember { Animatable(0f) }

        val transition = state.isTransitioning
        val active = state.isActive

        // The knob parks at the far end while connected and returns home when the tunnel drops.
        LaunchedEffect(active, transition, trackWidth) {
            when {
                active -> offset.animateTo(maxOffset, spring(dampingRatio = 0.8f, stiffness = 380f))
                !transition -> offset.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f))
            }
        }

        val progress = if (maxOffset > 0f) (offset.value / maxOffset).coerceIn(0f, 1f) else 0f
        val hintAlpha by animateFloatAsState(
            targetValue = if (progress > 0.35f || active) 0f else 1f,
            animationSpec = tween(180),
            label = "slide-hint",
        )

        val idleColor = customColor ?: MaterialTheme.colorScheme.primary
        val glass = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

        // The shadow adopts the state colour, so the glow of the button always matches it.
        val glowColor by animateColorAsState(
            when {
                state == ConnectionState.Error -> MaterialTheme.colorScheme.error
                active -> customActiveColor ?: NrgViolet
                else -> idleColor
            },
            tween(700),
            label = "slide-glow",
        )
        val elevation by animateDpAsState(if (active) 16.dp else 8.dp, label = "slide-elevation")
        val activeGradient = Brush.horizontalGradient(
            listOf(customActiveColor ?: NrgViolet, (customActiveColor ?: NrgCyan)),
        )
        val errorGradient = Brush.horizontalGradient(
            listOf(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.errorContainer),
        )

        Box(
            modifier
                .fillMaxWidth()
                .height(height)
                .shadow(
                    elevation = elevation,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = glowColor.copy(alpha = 0.55f),
                    spotColor = glowColor.copy(alpha = 0.55f),
                )
                .onSizeChanged { trackWidth = it.width.toFloat() }
                .pointerInput(active, transition) {
                    if (active && !transition) {
                        detectTapGestures { _ ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        }
                    }
                },
        ) {
            // The trail that follows the knob.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .graphicsLayer { alpha = if (active) 0.35f else 0.16f * progress }
                    .background(
                        if (active) {
                            activeGradient
                        } else {
                            Brush.horizontalGradient(
                                listOf(idleColor.copy(alpha = 0.45f), Color.Transparent),
                                startX = 0f,
                                endX = trackWidth,
                            )
                        },
                    ),
            )

            // The track.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        when {
                            state == ConnectionState.Error -> errorGradient
                            active -> activeGradient
                            else -> Brush.horizontalGradient(listOf(glass, glass))
                        },
                    )
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape),
            )

            if (!transition) {
                Text(
                    text = stringResource(if (active) Res.string.slide_to_disconnect else Res.string.slide_to_connect),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = hintAlpha },
                )
            }

            // The chevron marking the far end; it yields as the knob approaches.
            Icon(
                painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .graphicsLayer { alpha = if (active) 0f else (1f - progress * 0.7f).coerceIn(0f, 1f) },
            )

            // The knob.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(5.dp)
                    .size(knobSize)
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.35f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    )
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.88f))))
                    .pointerInput(active, transition, maxOffset) {
                        if (transition) return@pointerInput
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    offset.snapTo((offset.value + dragAmount).coerceIn(0f, maxOffset))
                                }
                            },
                            onDragEnd = {
                                when {
                                    !active && offset.value >= maxOffset * 0.75f -> {
                                        scope.launch { offset.animateTo(maxOffset, spring(dampingRatio = 0.7f)) }
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggle()
                                    }

                                    active && offset.value <= maxOffset * 0.25f -> {
                                        scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f)) }
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggle()
                                    }

                                    else -> scope.launch {
                                        offset.animateTo(if (active) maxOffset else 0f, spring(dampingRatio = 0.75f))
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (transition) {
                    CyberRadarIndicator(
                        modifier = Modifier.size(knobSize * 0.55f),
                    )
                } else {
                    Icon(
                        painterResource(if (active) Res.drawable.ic_lock_open else Res.drawable.ic_bolt),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(knobSize * 0.5f),
                    )
                }
            }
        }
    }
}
