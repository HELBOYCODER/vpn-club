package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.IranVariants
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.formatSpeed
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

/** لیست ۱۰ سرور منتخب + IPهای تمیز کلادفلر. */
@Composable
fun ServersScreen(
    ui: VpnController.UiState,
    onConnect: (ServerEntry) -> Unit,
    onRefresh: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("۱۰ سرور منتخب", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("رفرش: ضعیف‌ها حذف ← سرور جدید از ساب‌ها ← اسکن IP تمیز ← تزریق به هر ۱۰ سرور", color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = onRefresh, enabled = !ui.testing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "تست دوباره",
                        tint = if (ui.testing) TextTertiary else NeonCyan)
                }
            }
            if (ui.testing) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeonCyan, trackColor = Color(0xFF1A243B))
                Text(ui.message, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (ui.servers.isEmpty()) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Text("هنوز سروری تست نشده. دکمهٔ اتصال را در صفحهٔ خانه بزنید تا ساب‌ها دریافت و ۱۰ سرور سالم انتخاب شوند.",
                        color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        items(ui.servers, key = { it.id }) { s ->
            ServerRow(rank = ui.servers.indexOf(s) + 1, s = s, active = ui.connected && ui.current?.serverKey == s.serverKey,
                onClick = { onConnect(s) })
        }

        if (ui.cleanIps.isNotEmpty()) {
            item {
                Text("IPهای تمیز کلادفلر", color = TextPrimary, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("لمس = کپی", color = TextTertiary, fontSize = 11.sp)
            }
            items(ui.cleanIps.take(12), key = { "ip-" + it.ip }) { ip ->
                ClubCard(Modifier.fillMaxWidth().clickable {
                    clipboard.setText(AnnotatedString(ip.ip))
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ip.ip, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Chip("${ip.tlsMs.fa()} ms", StatusGreen)
                    }
                }
            }
        }
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
            ) { Text(rank.fa(), color = if (active) Color.Black else TextSecondary, fontSize = 13.sp,
                fontWeight = FontWeight.Bold) }
            Text(ServerEntry.flagOf(s.country), fontSize = 26.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.config.tag.ifEmpty { s.config.host }, color = TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip(s.config.scheme.uppercase(), NeonIndigo)
                    if (s.injected) Chip("IP تمیز", NeonCyan)
                    IranVariants.byId(s.variantId)?.takeIf { it.id != "plain" }?.let { Chip(it.label, StatusAmber) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${s.latencyMs.fa()} ms", color = StatusGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(formatSpeed(s.downBps.toLong()), color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
