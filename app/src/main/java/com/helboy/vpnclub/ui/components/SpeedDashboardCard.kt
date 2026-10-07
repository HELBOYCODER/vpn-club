package com.helboy.vpnclub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.CardBg
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

@Composable
fun SpeedDashboardCard(
    downloadSpeed: String,
    uploadSpeed: String,
    pingFormatted: String,
    durationFormatted: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download Speed
            MetricItem(
                label = "دانلود",
                value = downloadSpeed,
                icon = "⬇️",
                accentColor = NeonCyan
            )

            // Upload Speed
            MetricItem(
                label = "آپلود",
                value = uploadSpeed,
                icon = "⬆️",
                accentColor = NeonIndigo
            )

            // Ping Latency
            MetricItem(
                label = "تاخیر (پینگ)",
                value = pingFormatted,
                icon = "⚡",
                accentColor = StatusGreen
            )

            // Duration
            MetricItem(
                label = "مدت زمان",
                value = durationFormatted,
                icon = "⏱",
                accentColor = TextPrimary
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    icon: String,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.padding(horizontal = 2.dp))
            Text(
                text = label,
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = value,
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
