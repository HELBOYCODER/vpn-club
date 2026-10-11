package com.helboy.vpnclub.ui.screen

import com.helboy.vpnclub.ui.theme.Palette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.IranVariants
import com.helboy.vpnclub.corex.Phase
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.components.ParticleOrb
import com.helboy.vpnclub.ui.components.HellboyFace
import com.helboy.vpnclub.ui.components.StatTile
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.formatBytes
import com.helboy.vpnclub.ui.formatDuration
import com.helboy.vpnclub.ui.formatSpeed
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** صفحهٔ خانه: دکمه، وضعیت، سرور فعلی، سرعت زنده و مدت نشست. */
@Composable
fun HomeScreen(ui: VpnController.UiState, onToggle: () -> Unit) {
    // چرخش دکمه فقط وقتی واقعاً در حال اتصال هستیم (نه هنگام اسکن/ری‌اسکن جدا)
    val busy = ui.busyConnecting
    val shownPhase = if (!busy && !ui.connected && ui.phase != Phase.FAILED) Phase.IDLE else ui.phase
    var fastest by remember { mutableStateOf(VpnController.settings().fastestMode) }

    // ساعت نشست
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.connected) {
        while (ui.connected) { now = System.currentTimeMillis(); delay(1_000) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("HELLBOY VPN", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp)

        if (com.helboy.vpnclub.ui.theme.ThemeState.buttonStyle == "orb")
            ParticleOrb(phase = shownPhase, connected = ui.connected, busy = busy,
                downBps = ui.downBps, upBps = ui.upBps, onClick = onToggle, size = 280.dp)
        else
            HellboyFace(phase = shownPhase, connected = ui.connected, busy = busy,
                downBps = ui.downBps, upBps = ui.upBps, onClick = onToggle, size = 300.dp)

        val (title, tColor) = when {
            ui.connected -> "متصل" to StatusGreen
            ui.phase == Phase.FAILED && !busy -> "اتصال ناموفق" to StatusRed
            busy -> "در حال اتصال…" to StatusAmber
            else -> "قطع" to TextSecondary
        }
        Text(title, color = tColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            (if (busy) ui.taskMsg.ifEmpty { ui.message } else ui.message).ifEmpty {
                if (ui.connected) "اینترنت شما امن است" else "برای اتصال هوشمند، دکمه را بزنید"
            },
            color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        // سایفون روی سرور — کلید آتش/یخ با فونت هلبوی
        PsiphonCard(ui)

        // گزینه‌ی سریع‌ترین اتصال
        ClubCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سریع‌ترین اتصال", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (fastest) "همه‌ی سرورها هم‌زمان تست و کم‌تاخیرترین وصل می‌شود"
                         else "به بهترین سرور لیست وصل می‌شود (یا از تب سرورها انتخاب کنید)",
                        color = TextTertiary, fontSize = 11.sp)
                }
                Switch(checked = fastest, onCheckedChange = { fastest = it; VpnController.settings().fastestMode = it })
            }
        }

        if (busy || ui.testing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = NeonCyan, trackColor = Palette.track,
            )
        }

        if (ui.connected && ui.current != null) {
            ServerCard(ui.current, ui.latencyMs, formatDuration(now - ui.connectedSince))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("دانلود", formatSpeed(ui.downBps), "کل: ${formatBytes(ui.downTotal)}",
                    StatusGreen, Modifier.weight(1f))
                StatTile("آپلود", formatSpeed(ui.upBps), "کل: ${formatBytes(ui.upTotal)}",
                    NeonIndigo, Modifier.weight(1f))
            }
        }

        // خلاصهٔ خط لولهٔ موتور
        if (ui.configCount > 0 || ui.servers.isNotEmpty()) {
            ClubCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Metric(ui.configCount.fa(), "کانفیگ ساب")
                    Metric(ui.cleanIpCount.fa(), "IP تمیز CF")
                    Metric("${ui.servers.size.fa()}/${10.fa()}", "سرور منتخب")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun ServerCard(e: ServerEntry, latencyMs: Long, duration: String) {
    ClubCard(Modifier.fillMaxWidth(), border = StatusGreen.copy(alpha = 0.5f)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ServerEntry.flagOf(e.country), fontSize = 34.sp)
                Column(Modifier.weight(1f)) {
                    Text(e.config.tag.ifEmpty { e.config.host }, color = TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${e.config.dialHost}", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
                }
                Text(duration, color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(e.config.scheme.uppercase(), NeonIndigo)
                if (latencyMs > 0) Chip("${latencyMs.fa()} ms", StatusGreen)
                if (e.injected) Chip("IP تمیز CF", NeonCyan)
                IranVariants.byId(e.variantId)?.let { Chip(it.label, StatusAmber) }
            }
        }
    }
}
