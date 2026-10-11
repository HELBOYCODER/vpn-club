package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.Phase
import com.helboy.vpnclub.ui.components.ClubPowerButton
import com.helboy.vpnclub.ui.viewmodel.MainViewModel

/**
 * صفحهٔ تکدکمهای — راست‌چین (RTL)، یک دکمهٔ انیمیشنی و وضعیت موتور.
 */
@Composable
fun OneButtonScreen(
    ui: MainViewModel.UiState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0B1020))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ClubPowerButton(
                phase = ui.phase,
                connected = ui.connected,
                onClick = onToggle,
            )

            Spacer(Modifier.height(28.dp))
            Text(
                text = when {
                    ui.connected -> "متصل"
                    ui.phase == Phase.FAILED -> "اتصال ناموفق"
                    ui.phase == Phase.IDLE -> "خاموش"
                    else -> "در حال اتصال"
                },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                ui.message.ifEmpty { "برای اتصال، دکمه را بزنید" },
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp,
            )
            if (ui.configCount > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "کانفیگ: ${ui.configCount} • IP تمیز CF: ${ui.cleanIpCount} • سالم: ${ui.usableCount}",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
