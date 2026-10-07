package com.helboy.vpnclub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.data.model.VpnServer
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.CardBgElevated
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

@Composable
fun SelectedServerCard(
    server: VpnServer?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Flag / Globe Icon container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardBgElevated)
                        .border(1.dp, BorderDark, CircleShape)
                ) {
                    if (server != null) {
                        Text(
                            text = server.countryFlag,
                            fontSize = 24.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "سرور",
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = server?.countryLong ?: "انتخاب سرور",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (server != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            if (server.isIranCompatible) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StatusGreen.copy(alpha = 0.15f))
                                        .border(1.dp, StatusGreen.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "🇮🇷 سازگار با ایران",
                                        color = StatusGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (server.isTsukubaSubnet) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StatusRed.copy(alpha = 0.15f))
                                        .border(1.dp, StatusRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⚠️ فیلتر در ایران",
                                        color = StatusRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (server != null) "${server.ip} • ${server.protocolDetected}" else "جهت تغییر سرور ضربه بزنید",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Right side: Badges and arrow
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (server != null) {
                    // Ping badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusGreen.copy(alpha = 0.15f))
                            .border(1.dp, StatusGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = server.pingFormatted,
                            color = StatusGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Speed badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.12f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = server.speedMbpsFormatted,
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "تغییر سرور",
                    tint = TextTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
