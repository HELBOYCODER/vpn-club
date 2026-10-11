package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.IranVariants
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

/** سرورها: ۱۰ سرور سالم + دکمه‌ی «ری‌اسکن» (لیست تازه) و «اینجکت IP» (تزریق IP تمیز). لمس سرور = اتصال. */
@Composable
fun ServersScreen(
    ui: VpnController.UiState,
    onConnect: (ServerEntry) -> Unit,
    onRescan: () -> Unit,
    onInject: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("سرورهای منتخب", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${ui.servers.size.fa()} سرور سالم · سریع‌ترین بالا · لمس = اتصال", color = TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("ری‌اسکن سرورها", Icons.Rounded.Refresh,
                    running = ui.task == Task.RESCAN, enabled = !ui.testing,
                    accent = NeonCyan, onClick = onRescan, modifier = Modifier.weight(1f))
                ActionButton("اینجکت IP تمیز", Icons.Rounded.Vaccines,
                    running = ui.task == Task.INJECT, enabled = !ui.testing && ui.servers.isNotEmpty(),
                    accent = StatusAmber, onClick = onInject, modifier = Modifier.weight(1f))
            }
            TaskLine(ui)
        }

        if (ui.servers.isEmpty()) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Text("لیست خالی است. «ری‌اسکن سرورها» را بزنید تا ۱۰ سرور سالم از ساب‌ها پیدا شود.",
                        color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        itemsIndexed(ui.servers, key = { _, s -> s.serverKey }) { i, s ->
            ServerRow(rank = i + 1, s = s,
                active = ui.connected && ui.current?.serverKey == s.serverKey,
                onClick = { if (!ui.busyConnecting) onConnect(s) })
        }
    }
}

/** خط وضعیت کار در حال اجرا + پیام نتیجه‌ی آخرین کار. */
@Composable
fun TaskLine(ui: VpnController.UiState) {
    if (ui.testing) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = NeonCyan, trackColor = Color(0xFF1A243B))
        Text(ui.taskMsg, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    } else if (ui.message.isNotEmpty()) {
        Text(ui.message, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ServerRow(rank: Int, s: ServerEntry, active: Boolean, onClick: () -> Unit) {
    ClubCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (active) StatusGreen else Color(0xFF26354D),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(30.dp).background(if (active) StatusGreen else Color(0xFF1A243B), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(rank.fa(), color = if (active) Color.Black else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Text(ServerEntry.flagOf(s.country), fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.config.tag.ifEmpty { s.config.host }, color = TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip(s.config.scheme.uppercase(), NeonIndigo)
                    when {
                        s.injected && s.canInject -> Chip("IP تمیز ${s.config.dialHost}", NeonCyan)
                        s.canInject -> Chip("کلادفلر · قابل اینجکت", NeonCyan.copy(alpha = 0.7f))
                        else -> Chip("بدون اینجکت", TextTertiary)
                    }
                    IranVariants.byId(s.variantId)?.takeIf { it.id != "plain" }?.let { Chip(it.label, StatusAmber) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (s.latencyMs > 0) "${s.latencyMs.fa()} ms" else "—",
                    color = StatusGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (active) Text("متصل", color = StatusGreen, fontSize = 11.sp)
            }
        }
    }
}
