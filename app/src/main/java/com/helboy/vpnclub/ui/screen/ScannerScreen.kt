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
            Text("از هر اسکن فقط سریع‌ترین IP منتخب می‌شود · لمس هر IP = انتخاب آن",
                color = TextSecondary, fontSize = 12.sp)

            // ── IP منتخب ──
            ClubCard(Modifier.fillMaxWidth().padding(top = 12.dp), border = NeonCyan) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("IP منتخب برای اینجکت", color = TextSecondary, fontSize = 12.sp)
                    if (!editing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.selectedIp.ifEmpty { "— هنوز انتخاب نشده —" }, color = TextPrimary,
                                fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                ActionButton("اینجکت به همه", Icons.Rounded.Vaccines,
                    running = ui.task == Task.INJECT,
                    enabled = !ui.testing && ui.selectedIp.isNotEmpty() && ui.servers.isNotEmpty(),
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
            val selected = ip.ip == ui.selectedIp
            ClubCard(Modifier.fillMaxWidth().clickable { VpnController.selectIp(ip.ip) },
                border = if (selected) NeonCyan else Palette.border) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((i + 1).fa(), color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(ip.ip, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (selected) Chip("منتخب", NeonCyan) else if (i == 0) Chip("سریع‌ترین", StatusGreen)
                    Text("  ${ip.tlsMs.fa()} ms", color = StatusGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
