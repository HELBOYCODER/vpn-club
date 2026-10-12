package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Switch
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.Palette
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

/**
 * اسکنر IP تمیز کلادفلر. هر اسکن فقط «یک IP منتخب» (سریع‌ترین) را برای اینجکت برمی‌گزیند؛
 * کاربر می‌تواند با لمس یک IP دیگر یا ورود دستی آن را عوض کند.
 */
@Composable
fun ScannerScreen(
    ui: VpnController.UiState,
    onScan: () -> Unit,
    onInject: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("اسکنر IP تمیز", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("اسکن ← پینگ مستقیم با اینترنت گوشی ← یک IP منتخب ← اینجکت خودکار روی ۱۰ سرور برتر",
                color = TextSecondary, fontSize = 12.sp)

            // ── IP منتخب ──
            ClubCard(Modifier.fillMaxWidth().padding(top = 12.dp), border = NeonCyan) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("IP منتخب برای اینجکت", color = TextSecondary, fontSize = 12.sp)
                    if (!editing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ui.selectedIp.ifEmpty { "— هنوز انتخاب نشده —" }, color = TextPrimary,
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                if (ui.selectedIp.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (ui.selectedIpMs > 0) Chip("پینگ مستقیم ${ui.selectedIpMs.fa()}ms", StatusGreen)
                                    else Chip(if (ui.task == Task.SCAN_IP) "در حال پینگ…" else "پینگ مستقیم نداد", StatusAmber)
                                    if (ui.selectedIpColo.isNotEmpty()) Chip(ui.selectedIpColo, NeonCyan)
                                }
                            }
                            if (ui.selectedIp.isNotEmpty()) IconButton(onClick = { clipboard.setText(AnnotatedString(ui.selectedIp)) }) {
                                Icon(Icons.Rounded.ContentCopy, "کپی", tint = TextSecondary)
                            }
                            IconButton(onClick = { draft = ui.selectedIp; error = false; editing = true }) {
                                Icon(Icons.Rounded.Edit, "ویرایش دستی", tint = NeonCyan)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = it.filter { c -> c.isDigit() || c == '.' }.take(15); error = false },
                            singleLine = true, isError = error, label = { Text("IP دلخواه (مثلاً 104.16.1.1)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (error) Text("IP نامعتبر است", color = StatusRed, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { if (VpnController.selectIp(draft)) editing = false else error = true }) {
                                Text("ذخیره", color = NeonCyan, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { editing = false }) { Text("لغو", color = TextSecondary) }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("اسکن IP", Icons.Rounded.Radar,
                    running = ui.task == Task.SCAN_IP, enabled = !ui.testing,
                    accent = NeonCyan, onClick = onScan, modifier = Modifier.weight(1f))
                ActionButton("منتخب", Icons.Rounded.Star,
                    running = false, enabled = !ui.testing,
                    accent = StatusGreen, onClick = { VpnController.pickSelectedIp() }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("اینجکت به ۱۰ سرور برتر", Icons.Rounded.Vaccines,
                    running = ui.task == Task.INJECT,
                    enabled = !ui.testing && ui.selectedIp.isNotEmpty() && ui.servers.isNotEmpty(),
                    accent = StatusAmber, onClick = onInject, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("اینجکت خودکار", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("بعد از هر اسکن/انتخاب، IP منتخب روی ۱۰ سرور برتر اینجکت و پینگ می‌شود", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(checked = ui.autoInject, onCheckedChange = { VpnController.setAutoInject(it) })
            }
            TaskLine(ui)
        }
        if (ui.injectReport.isNotEmpty()) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("گزارش آخرین اینجکت · ${ui.injectReport.count { it.ok }.fa()} موفق از ${ui.injectReport.size.fa()}",
                            color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        ui.injectReport.forEach { r ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(if (r.ok) "✓" else "✗", color = if (r.ok) StatusGreen else StatusRed,
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(r.reason, color = TextSecondary, fontSize = 11.sp)
                                }
                                if (r.ok) Text("${r.ms.fa()}ms", color = StatusGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (ui.cleanIps.isEmpty()) {
            item {
                ClubCard(Modifier.fillMaxWidth()) {
                    Text("هنوز اسکن نشده. «اسکن IP» را بزنید.", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
        itemsIndexed(ui.cleanIps, key = { _, ip -> "ip-" + ip.ip }) { i, ip ->
            val selected = ip.ip == ui.selectedIp
            ClubCard(Modifier.fillMaxWidth().clickable { VpnController.selectIp(ip.ip) },
                border = if (selected) NeonCyan else Palette.border) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((i + 1).fa(), color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(ip.ip, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (ip.colo.isNotEmpty()) Chip(ip.colo, TextTertiary)
                    if (selected) Chip("منتخب", NeonCyan) else if (i == 0) Chip("سریع‌ترین", StatusGreen)
                    Text("  ${ip.tlsMs.fa()} ms", color = StatusGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
