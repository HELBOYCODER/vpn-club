package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.formatSpeed
import com.helboy.vpnclub.ui.theme.*

/** The current session's measured shortlist; the main button owns selection and recovery. */
@Composable
fun ServersScreen(ui: VpnController.UiState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("سرورهای منتخب", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("چند سرور تازه با تست واقعی انتخاب می‌شوند؛ اتصال و تعویض سرور خودکار است.", color = TextSecondary, fontSize = 13.sp)
            if (ui.testing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = NeonCyan, trackColor = Palette.track)
                Text(ui.taskMsg, color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (ui.servers.isEmpty()) item {
            ClubCard(Modifier.fillMaxWidth()) {
                Text(if (ui.sessionActive) "در حال یافتن سرور مناسب…" else "دکمهٔ اتصال در صفحهٔ خانه، جست‌وجوی تازه را شروع می‌کند.", color = TextSecondary)
            }
        }
        itemsIndexed(ui.servers, key = { _, server -> server.serverKey }) { index, server ->
            val active = ui.connected && ui.current?.serverKey == server.serverKey
            ClubCard(Modifier.fillMaxWidth(), border = if (active) StatusGreen else Palette.track) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(ServerEntry.flagOf(server.country), fontSize = 28.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${(index + 1).fa()}. ${server.config.tag.ifEmpty { server.config.host }}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(server.config.scheme.uppercase(), color = NeonIndigo, fontSize = 12.sp)
                        Text("${server.latencyMs.fa()} ms · " + if (active) "متصل" else "پشتیبان تست‌شده", color = if (active) StatusGreen else TextSecondary, fontSize = 12.sp)
                        if (server.downBps > 0) Text("سرعت تست: ${formatSpeed(server.downBps.toLong())}", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
