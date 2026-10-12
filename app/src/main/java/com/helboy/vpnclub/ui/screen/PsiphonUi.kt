package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.helboy.vpnclub.corex.PsiphonRunner
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.PsiphonState
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.components.FireIceToggle
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextSecondary

/** کارت سایفون در صفحه‌ی خانه: کلید آتش/یخ + وضعیت. */
@Composable
fun PsiphonCard(ui: VpnController.UiState) {
    var showConfig by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val st = VpnController.settings()
    var mode by remember { mutableStateOf(st.psiphonMode) }
    val direct = mode == "direct"
    val sub = when {
        !ui.psiphonHasConfig -> "کانفیگ سایفون وارد نشده — لمس کنید"
        !ui.psiphonOn -> "خاموش · ترافیک مستقیم از سرور"
        ui.psiphon == PsiphonState.ON && direct -> "روشن · سایفون مستقیم (بدون سرور)"
        ui.psiphon == PsiphonState.ON -> "روشن · ترافیک ← سایفون ← سرور"
        direct && !ui.connected -> "روشن · دکمه‌ی اتصال = سایفون مستقیم"
        ui.psiphon == PsiphonState.CONNECTING -> "در حال اتصال سایفون…"
        ui.psiphon == PsiphonState.FAILED -> "سایفون وصل نشد؛ اتصال بدون سایفون ادامه دارد"
        ui.connected -> "روشن · در اتصال بعدی اعمال می‌شود"
        else -> "روشن · با اتصال بعدی فعال می‌شود"
    }
    ClubCard(Modifier.fillMaxWidth()) {
        Column {
            FireIceToggle(
                text = "PSIPHON",
                on = ui.psiphonOn,
                busy = ui.psiphonOn && ui.psiphon == PsiphonState.CONNECTING,
                enabled = ui.psiphonHasConfig,
                subtitle = err ?: sub,
                onToggle = {
                    if (!ui.psiphonHasConfig) showConfig = true
                    else err = VpnController.setPsiphon(!ui.psiphonOn)
                },
            )
            if (ui.psiphonHasConfig) PsiphonOptions(mode = mode, onMode = { mode = it; st.psiphonMode = it })
        }
    }
    if (showConfig) PsiphonConfigDialog(onClose = { showConfig = false })
}

/** ورود کانفیگ رسمی سایفون (JSON از Psiphon Inc.). */
@Composable
fun PsiphonConfigDialog(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("کانفیگ سایفون") },
        text = {
            Column {
                Text("کانفیگ رسمی JSON که از Psiphon Inc. گرفته‌اید را بچسبانید (باید PropagationChannelId و SponsorId داشته باشد).",
                    color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    placeholder = { Text("{ \"PropagationChannelId\": \"…\", \"SponsorId\": \"…\", … }", fontSize = 11.sp) },
                    isError = error != null,
                )
                error?.let { Text(it, color = StatusRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = VpnController.importPsiphonConfig(text)
                if (e == null) onClose() else error = e
            }) { Text("ذخیره", color = NeonCyan) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
    )
}

/** تنظیمات سایفون (مثل MahsaNG): حالت، پروتکل، کشور خروجی، اتصال تهاجمی. با اتصال بعدی اعمال می‌شود. */
@Composable
private fun PsiphonOptions(mode: String, onMode: (String) -> Unit) {
    val st = VpnController.settings()
    var proto by remember { mutableStateOf(st.psiphonProtocol) }
    var region by remember { mutableStateOf(st.psiphonRegion) }
    var aggressive by remember { mutableStateOf(st.psiphonAggressive) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionRow("حالت", listOf("chain" to "روی سرور", "direct" to "مستقیم (بدون سرور)"), mode, onMode)
        OptionRow("پروتکل", listOf("auto" to "خودکار", "normal" to "معمولی", "conduit" to "Conduit"), proto) {
            proto = it; st.psiphonProtocol = it
        }
        OptionRow("کشور", PsiphonRunner.REGIONS, region) { region = it; st.psiphonRegion = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("اتصال تهاجمی", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("تلاش هم‌زمان بیشتر؛ سریع‌تر وصل می‌شود، باتری بیشتری مصرف می‌کند", color = TextSecondary, fontSize = 11.sp)
            }
            Switch(checked = aggressive, onCheckedChange = { aggressive = it; st.psiphonAggressive = it })
        }
        Text("تغییرات با اتصال بعدی اعمال می‌شود. Conduit از داوطلبان سایفون عبور می‌کند و در فیلترینگ شدید بهتر جواب می‌دهد.",
            color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun OptionRow(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (k, name) ->
                val sel = k == value
                Box(Modifier
                    .background(if (sel) NeonCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
                    .clickable { onPick(k) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(name, fontSize = 12.sp, color = if (sel) NeonCyan else TextSecondary,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
