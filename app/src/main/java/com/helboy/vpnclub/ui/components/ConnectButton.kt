package com.helboy.vpnclub.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.theme.CardBgElevated
import com.helboy.vpnclub.ui.theme.DarkBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.vpn.VpnConnectionState

@Composable
fun ConnectButton(
    state: VpnConnectionState,
    statusMessage: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = state == VpnConnectionState.CONNECTED
    val isConnecting = state == VpnConnectionState.CONNECTING ||
            state == VpnConnectionState.AUTHENTICATING ||
            state == VpnConnectionState.PREPARING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isConnected || isConnecting) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnecting) 900 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeColor = when {
        isConnected -> StatusGreen
        isConnecting -> StatusAmber
        state == VpnConnectionState.ERROR -> StatusRed
        else -> NeonIndigo
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Outer Glowing Pulse Ring
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(activeColor.copy(alpha = if (isConnected || isConnecting) 0.15f else 0.05f))
                    .border(1.dp, activeColor.copy(alpha = 0.3f), CircleShape)
            )

            // Middle Decorative Ring
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(DarkBg)
                    .border(2.dp, activeColor.copy(alpha = 0.6f), CircleShape)
            )

            // Center Interactive Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(126.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                activeColor.copy(alpha = 0.45f),
                                CardBgElevated
                            )
                        )
                    )
                    .border(2.5.dp, activeColor, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                    contentDescription = "اتصال",
                    tint = if (isConnected) NeonCyan else activeColor,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when (state) {
                VpnConnectionState.CONNECTED -> "متصل شد (سراسری)"
                VpnConnectionState.CONNECTING,
                VpnConnectionState.AUTHENTICATING,
                VpnConnectionState.PREPARING -> "در حال برقراری تونل..."
                VpnConnectionState.DISCONNECTING -> "در حال قطع ارتباط..."
                VpnConnectionState.ERROR -> "خطا در اتصال"
                VpnConnectionState.DISCONNECTED -> "آماده برای اتصال"
            },
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = statusMessage,
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}
