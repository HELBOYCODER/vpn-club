package com.helboy.vpnclub.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.Phase
import com.helboy.vpnclub.ui.viewmodel.MainViewModel

/**
 * صفحه‌ی تک‌دکمه‌ای — کل رابط کاربری:
 * یک دایره‌ی بزرگ وسط صفحه؛ روشن/خاموش. زیرش فقط وضعیت موتور.
 */
@Composable
fun OneButtonScreen(
    ui: MainViewModel.UiState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = ui.phase in listOf(Phase.FETCHING, Phase.SCANNING_CF, Phase.TESTING, Phase.CONNECTING)
    val on = ui.connected

    val glow = if (on) Color(0xFF34D399) else Color(0xFF3B82F6)
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "angle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .drawBehind {
                    if (busy) {
                        drawArc(
                            color = glow,
                            startAngle = angle,
                            sweepAngle = 90f,
                            useCenter = false,
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )
                    } else {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(glow.copy(alpha = 0.25f), Color.Transparent),
                                center = Offset(size.width / 2, size.height / 2)
                            )
                        )
                    }
                }
                .clip(CircleShape)
                .background(Color(0xFF141B33))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    busy -> "…"
                    on -> "روشن"
                    else -> "خاموش"
                },
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(32.dp))
        Text(ui.message.ifEmpty { "برای اتصال، دکمه را بزنید" }, color = Color(0xFF9CA3AF), fontSize = 14.sp)

        if (ui.configCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "کانفیگ: ${ui.configCount} • IP تمیز CF: ${ui.cleanIpCount} • سالم: ${ui.usableCount}",
                color = Color(0xFF6B7280), fontSize = 12.sp
            )
        }
    }
}
