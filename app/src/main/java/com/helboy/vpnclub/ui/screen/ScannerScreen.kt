package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

/** اسکنر IP تمیز کلادفلر (جدا از سرورها). لمس IP = کپی. */
@Composable
fun ScannerScreen(
    ui: VpnController.UiState,
    onScan: () -> Unit,
    onInject: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("اسکنر IP تمیز", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("هندشیک TLS + پاسخ واقعی کلادفلر از روی اینترنت خودتان · تمیزترین بالا · لمس = کپی",
                color = TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("اسکن IP", Icons.Rounded.Radar,
                    running = ui.task == Task.SCAN_IP, enabled = !ui.testing,
                    accent = NeonCyan, onClick = onScan, modifier = Modifier.weight(1f))
                ActionButton("اینجکت به سرورها", Icons.Rounded.Vaccines,
                    running = ui.task == Task.INJECT,
                    enabled = !ui.testing && ui.cleanIps.isNotEmpty() && ui.servers.isNotEmpty(),
                    accent = StatusAmber, onClick = onInject, modifier = Modifier.weight(1f))
            }
            TaskLine(ui)
        }
        if (ui.cleanIps.isEmpty()) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Text("هنوز اسکن نشده. «اسکن IP» را بزنید.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
        itemsIndexed(ui.cleanIps, key = { _, ip -> "ip-" + ip.ip }) { i, ip ->
            ClubCard(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(ip.ip)) },
                border = if (i == 0) StatusGreen else androidx.compose.ui.graphics.Color(0xFF26354D)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((i + 1).fa(), color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(ip.ip, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (i == 0) Chip("تمیزترین", StatusGreen)
                    Text("  ${ip.tlsMs.fa()} ms", color = StatusGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
