package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.corex.XrayCore
import com.helboy.vpnclub.ui.components.ActionButton
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary

private val DNS = listOf("1.1.1.1" to "کلادفلر", "8.8.8.8" to "گوگل", "9.9.9.9" to "Quad9", "94.140.14.14" to "ضد تبلیغ", "https://1.1.1.1/dns-query" to "DoH رمزدار")

/** سبک اتصال + DNS + قوانین مسیریابی (مثل ZeroNet). */
@Composable
fun ConnectionCard(ui: VpnController.UiState) {
    var showRules by remember { mutableStateOf(false) }
    var customDns by remember { mutableStateOf(if (DNS.none { it.first == ui.dnsServer }) ui.dnsServer else "") }
    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DNS", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DNS.forEach { (ip, label) ->
                    FilterChip(selected = ui.dnsServer == ip, onClick = { customDns = ""; VpnController.setDnsServer(ip) },
                        label = { Text(label, fontSize = 12.sp) })
                }
            }
            OutlinedTextField(value = customDns, onValueChange = { customDns = it.trim() }, singleLine = true,
                label = { Text("DNS دلخواه (IP یا DoH با IP)", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { if (customDns.isNotEmpty()) VpnController.setDnsServer(customDns) },
                        enabled = com.helboy.vpnclub.corex.DnsEndpoint.isValid(customDns)) { Text("ثبت", color = NeonCyan) }
                })
            Text("با اتصال بعدی اعمال می‌شود", color = TextSecondary, fontSize = 11.sp)

            val n = listOf(ui.directRules, ui.blockRules, ui.proxyRules).sumOf { XrayCore.splitRules(it).let { p -> p.first.size + p.second.size } }
            OutlinedButton(onClick = { showRules = true }) {
                Text(if (n > 0) "قوانین مسیریابی (${n.fa()} قانون)" else "قوانین مسیریابی (سایت‌ها / IP ها)")
            }
        }
    }
    if (showRules) RoutingRulesDialog(ui, onClose = { showRules = false })
}

/** پروفایل مسیریابی: هر خط یک دامنه، IP، CIDR، geosite:… یا geoip:… */
@Composable
private fun RoutingRulesDialog(ui: VpnController.UiState, onClose: () -> Unit) {
    var direct by remember { mutableStateOf(ui.directRules) }
    var block by remember { mutableStateOf(ui.blockRules) }
    var proxy by remember { mutableStateOf(ui.proxyRules) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = { VpnController.setRoutingRules(direct, block, proxy); onClose() }) { Text("ذخیره", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
        title = { Text("قوانین مسیریابی") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هر خط: دامنه (example.com)، IP یا CIDR، geosite:… یا geoip:…  ترتیب: مسدود ← مستقیم ← ایران ← از تونل.",
                    color = TextSecondary, fontSize = 11.sp)
                OutlinedTextField(value = direct, onValueChange = { direct = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    label = { Text("مستقیم (بدون VPN)", fontSize = 12.sp) }, placeholder = { Text("digikala.com\nbank.ir", fontSize = 11.sp) })
                OutlinedTextField(value = block, onValueChange = { block = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    label = { Text("مسدود", fontSize = 12.sp) }, placeholder = { Text("geosite:category-ads-all", fontSize = 11.sp) })
                OutlinedTextField(value = proxy, onValueChange = { proxy = it }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    label = { Text("همیشه از تونل", fontSize = 12.sp) }, placeholder = { Text("google.com", fontSize = 11.sp) })
            }
        },
    )
}
