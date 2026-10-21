package com.helboy.vpnclub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helboy.vpnclub.corex.Phase

/**
 * دکمهٔ روشن/خاموش «کلاب» — بدون کتابخانهٔ جدید، فقط Compose.
 *
 * حالتها (از موتور می‌آیند):
 *  - خاموش: دایرهٔ تیره، حلقهٔ آبی کم‌نور، آرام نفس می‌کشد.
 *  - در حال کار (واکشی/اسکن/تست/اتصال): حلقهٔ گرد چرخان + نقطهٔ درخشان روی مدار.
 *  - روشن: حلقهٔ سبز کامل، هاله پخش می‌شود و قفل‌شدن با یک جهش نرم (spring).
 *
 * روشن/خاموش شدن = انیمیشن شعاع هاله + جهش مقیاس، نه تغییر ناگهانی.
 */
@Composable
fun ClubPowerButton(
    phase: Phase,
    connected: Boolean,
    onClick: () -> Unit,
    size: Dp = 240.dp,
    modifier: Modifier = Modifier,
) {
    val busy = phase == Phase.FETCHING || phase == Phase.SCANNING_CF ||
        phase == Phase.TESTING || phase == Phase.CONNECTING

    val accent = if (connected) Color(0xFF34D399) else Color(0xFF3B82F6)
    val failed = phase == Phase.FAILED
    val tint = if (failed) Color(0xFFF87171) else accent

    // ۱) انیمیشن روشن/خاموش: شعاع هاله بین ۰ و ۱ و جهش کوتاه مقیاس.
    val power = remember { Animatable(0f) }
    LaunchedEffect(connected) {
        power.animateTo(if (connected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 180f))
    }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(connected) {
        pop.snapTo(0.92f)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
    }

    // ۲) تنفس در حالت خاموش، چرخش در حالت کار.
    val inf = rememberInfiniteTransition(label = "club")
    val breathe by inf.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "spin",
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(pop.value)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2

            // هاله: با روشن شدن بزرگ و پررنگ می‌شود.
            val haloR = r * (0.78f + 0.22f * power.value) * (if (busy) 1f else breathe)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.35f * (0.3f + 0.7f * power.value)), Color.Transparent),
                    center = c, radius = haloR * 1.4f,
                ),
                radius = haloR * 1.4f, center = c,
            )

            // حلقهٔ بیرونی: آرام در حالت خاموش، کامل در روشن، گرد در کار.
            val ringStroke = Stroke(width = 8f, cap = StrokeCap.Round)
            drawCircle(color = tint.copy(alpha = 0.18f), radius = r * 0.9f, center = c, style = ringStroke)
            if (busy) {
                drawArc(
                    color = tint, startAngle = spin, sweepAngle = 110f, useCenter = false,
                    topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f),
                    size = Size(r * 1.8f, r * 1.8f), style = ringStroke,
                )
            } else if (power.value > 0.01f) {
                drawArc(
                    color = tint, startAngle = -90f, sweepAngle = 360f * power.value, useCenter = false,
                    topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f),
                    size = Size(r * 1.8f, r * 1.8f), style = ringStroke,
                )
            }

            // تکهٔ داخلی: دایرهٔ تیره با گرادیان ملایم.
            drawCircle(
                brush = Brush.verticalGradient(listOf(Color(0xFF1B2446), Color(0xFF0F1530))),
                radius = r * 0.7f, center = c,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.3f),
        )
    }
}
