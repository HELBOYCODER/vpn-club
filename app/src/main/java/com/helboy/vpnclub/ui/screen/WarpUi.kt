package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary

/** کارت WARP رایگان کلادفلر: اتصال یک‌لمسی، اسکن اندپوینت، نویز، حساب تازه. */
@Composable
fun WarpCard(ui: VpnController.UiState) {
    val warpServers = ui.servers.filter { it.source == "warp" }
    val onWarp = ui.connected && ui.current?.source == "warp"
    ClubCard(Modifier.fillMaxWidth(), border = if (onWarp) StatusGreen else StatusAmber.copy(alpha = 0.6f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WARP رایگان کلادفلر", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(when {
                onWarp -> "متصل به WARP · ${ui.current?.config?.dialHost}:${ui.current?.config?.dialPort}"
                !ui.warpRegistered -> "کلید روی همین گوشی ساخته و فقط کلید عمومی در WARP ثبت می‌شود."
                warpServers.isNotEmpty() -> "${warpServers.size.fa()} اندپوینت سالم · بهترین ${warpServers.minOf { it.latencyMs }.fa()}ms"
                else -> "حساب آماده است · «اسکن WARP» اندپوینت سالم پیدا می‌کند"
            }, color = if (onWarp) StatusGreen else TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("اتصال WARP", Icons.Rounded.Bolt, running = ui.busyConnecting && !onWarp,
                    enabled = !ui.testing, accent = StatusAmber, onClick = { VpnController.connectWarp() }, modifier = Modifier.weight(1f))
                ActionButton("اسکن WARP", Icons.Rounded.Radar, running = ui.task == Task.WARP,
                    enabled = !ui.testing, accent = NeonCyan, onClick = { VpnController.warpRescan() }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("نویز (ضد فیلتر WireGuard)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("بسته‌های تصادفی پیش از هندشیک؛ اگر وصل نشد خاموش/روشن کنید و دوباره اسکن کنید", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = ui.warpNoise, onCheckedChange = { VpnController.setWarpNoise(it) })
            }
            if (ui.warpRegistered) TextButton(onClick = { VpnController.warpReset() }, enabled = !ui.testing) {
                Text("حساب WARP تازه (کلید جدید)", color = StatusRed, fontSize = 12.sp)
            }
        }
    }
}
