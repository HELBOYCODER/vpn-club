package com.helboy.vpnclub.ui.screen

import androidx.compose.foundation.layout.Column
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
    val sub = when {
        !ui.psiphonHasConfig -> "کانفیگ سایفون وارد نشده — لمس کنید"
        !ui.psiphonOn -> "خاموش · ترافیک مستقیم از سرور"
        ui.psiphon == PsiphonState.ON -> "روشن · ترافیک ← سایفون ← سرور"
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
