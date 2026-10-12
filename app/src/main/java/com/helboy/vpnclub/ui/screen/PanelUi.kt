package com.helboy.vpnclub.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.corex.CloudflareDeployer
import com.helboy.vpnclub.corex.MySub
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.NeonIndigo
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary

fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

/** کارت تنظیمات: نصب آسان پنل شخصی روی کلادفلر خود کاربر. */
@Composable
fun PanelInstallCard() {
    var panel by remember { mutableStateOf<CloudflareDeployer.Panel?>(null) }
    ClubCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("پنل شخصی روی کلادفلر", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text("با توکن کلادفلر خودتان، پنل روی حساب خودتان نصب می‌شود و لینک ساب آن خودکار به «ساب‌های من» در تب سرورها اضافه می‌شود.",
                color = TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { panel = CloudflareDeployer.Panel.BPB }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Cloud, null, tint = NeonCyan); Text("  نصب BPB", color = NeonCyan)
                }
                OutlinedButton(onClick = { panel = CloudflareDeployer.Panel.ZEUS }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Cloud, null, tint = StatusAmber); Text("  نصب Zeus", color = StatusAmber)
                }
            }
        }
    }
    panel?.let { PanelDeployDialog(it, onClose = { panel = null }) }
}

@Composable
fun PanelDeployDialog(panel: CloudflareDeployer.Panel, onClose: () -> Unit) {
    val ui by VpnController.state.collectAsState()
    val ctx = LocalContext.current
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(CloudflareDeployer.randomName(if (panel == CloudflareDeployer.Panel.BPB) "bpb" else "zeus")) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { VpnController.clearDeploy() }
    val running = ui.task == Task.DEPLOY
    val result = ui.deployResult?.takeIf { started && it.panel == panel }
    val error = ui.deployError.takeIf { started }

    AlertDialog(
        onDismissRequest = { if (!running) { token = ""; onClose() } },
        title = { Text("نصب ${panel.label}") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result == null) {
                    Text("۱) توکن بسازید: صفحه‌ی کلادفلر باز می‌شود؛ پایین صفحه «Continue to summary» و بعد «Create Token» را بزنید و توکن را کپی کنید.",
                        color = TextSecondary, fontSize = 12.sp)
                    OutlinedButton(onClick = { openUrl(ctx, CloudflareDeployer.tokenUrl()) }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.OpenInBrowser, null, tint = NeonCyan); Text("  ساخت توکن در کلادفلر", color = NeonCyan)
                    }
                    Text("۲) توکن را اینجا بچسبانید (ذخیره نمی‌شود):", color = TextSecondary, fontSize = 12.sp)
                    OutlinedTextField(token, { token = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, enabled = !running,
                        label = { Text("Cloudflare API Token") },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                            }
                        })
                    OutlinedTextField(name, { name = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(40) },
                        Modifier.fillMaxWidth(), singleLine = true, enabled = !running, label = { Text("نام ورکر (زیردامنه)") },
                        isError = !CloudflareDeployer.isValidName(name))
                    if (ui.testing && !running) Text("کار دیگری در حال اجراست (${ui.taskMsg.take(40)})؛ کمی صبر کنید.", color = StatusAmber, fontSize = 11.sp)
                    if (panel == CloudflareDeployer.Panel.ZEUS)
                        Text("Zeus: پایگاه داده D1 ساخته می‌شود، رمز پنل و یک کاربر نامحدود خودکار تنظیم می‌شود.", color = TextTertiary, fontSize = 11.sp)
                }
                if (ui.deploySteps.isNotEmpty() && started) {
                    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ui.deploySteps.forEachIndexed { i, st ->
                            val last = i == ui.deploySteps.lastIndex && running
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (last) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = NeonCyan)
                                else Text("✓", color = StatusGreen, fontSize = 12.sp)
                                Text("  $st", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                error?.takeIf { it.isNotEmpty() }?.let { Text(it, color = StatusRed, fontSize = 12.sp) }
                result?.let { r ->
                    Text("✓ ${r.panel.label} روی ${r.host} نصب شد", color = StatusGreen, fontWeight = FontWeight.Bold)
                    CopyRow("آدرس پنل", r.panelUrl, open = true)
                    if (r.panelPassword.isNotEmpty()) CopyRow("رمز پنل (یادداشت کنید!)", r.panelPassword)
                    if (r.subUrl.isNotEmpty()) CopyRow("لینک ساب (به «ساب‌های من» اضافه شد)", r.subUrl)
                    if (r.note.isNotEmpty()) Text(r.note, color = TextSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (result == null) TextButton(
                enabled = !ui.testing && token.length >= 20 && CloudflareDeployer.isValidName(name),
                onClick = { started = true; VpnController.deployPanel(panel, token, name) },
            ) { Text(if (running) "در حال نصب…" else if (error.isNullOrEmpty()) "نصب" else "دوباره", color = NeonCyan, fontWeight = FontWeight.Bold) }
            else TextButton(onClick = { token = ""; onClose() }) { Text("تمام", color = NeonCyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { if (!running && result == null) TextButton(onClick = { token = ""; onClose() }) { Text("بستن") } },
    )
}

@Composable
private fun CopyRow(label: String, value: String, open: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 2, modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.ContentCopy, "کپی", tint = NeonCyan)
            }
            if (open) IconButton(onClick = { openUrl(ctx, value) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.OpenInBrowser, "باز کردن", tint = NeonCyan)
            }
        }
    }
}

/** کارت یک «ساب من» در تب سرورها: آمار + رفرش / پینگ / کپی / پنل / حذف. */
@Composable
fun MySubCard(sub: MySub, ui: VpnController.UiState) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val busy = ui.subBusyId == sub.id
    var confirmDelete by remember { mutableStateOf(false) }
    ClubCard(Modifier.fillMaxWidth(), border = if (sub.aliveCount > 0) NeonCyan.copy(alpha = 0.6f) else com.helboy.vpnclub.ui.theme.Palette.border) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sub.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Chip(when (sub.kind) { "bpb" -> "BPB"; "zeus" -> "Zeus"; else -> "ساب" }, NeonIndigo)
                        if (sub.lastFetchAt > 0) Chip("${sub.aliveCount.fa()}/${sub.configCount.fa()} سالم", if (sub.aliveCount > 0) StatusGreen else StatusAmber)
                        if (sub.bestMs > 0) Chip("${sub.bestMs.fa()}ms", StatusGreen)
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonCyan)
            }
            if (sub.lastError.isNotEmpty()) Text(sub.lastError, color = StatusAmber, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                SmallBtn(Icons.Rounded.Refresh, "رفرش", !ui.testing) { VpnController.refreshSub(sub.id) }
                SmallBtn(Icons.Rounded.NetworkPing, "پینگ", !ui.testing) { VpnController.pingSub(sub.id) }
                SmallBtn(Icons.Rounded.ContentCopy, "کپی", true) { clipboard.setText(AnnotatedString(sub.url)); VpnController.notify("لینک ساب کپی شد") }
                if (sub.panelUrl.isNotEmpty()) SmallBtn(Icons.Rounded.OpenInBrowser, "پنل", true) { openUrl(ctx, sub.panelUrl) }
                SmallBtn(Icons.Rounded.Delete, "حذف", !busy, StatusRed) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف «${sub.name}»؟") },
        text = { Text("ساب و سرورهایش از لیست حذف می‌شوند (پنل روی کلادفلر باقی می‌ماند).", fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; VpnController.removeSub(sub.id) }) { Text("حذف", color = StatusRed) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("لغو") } },
    )
}

@Composable
private fun SmallBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, enabled: Boolean,
                     color: Color = NeonCyan, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
        Icon(icon, null, tint = if (enabled) color else TextTertiary, modifier = Modifier.size(16.dp))
        Text(" $text", color = if (enabled) color else TextTertiary, fontSize = 12.sp)
    }
}

/** افزودن ساب دستی (لینک ساب پنل خودتان یا هر ساب دیگر). */
@Composable
fun AddSubDialog(onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(clipboard.getText()?.text?.trim()?.takeIf { it.startsWith("http") && !it.contains("\n") }.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("افزودن ساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(url, { url = it.trim(); error = null }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("لینک ساب (https://…)") }, isError = error != null)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام (اختیاری)") })
                error?.let { Text(it, color = StatusRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { val e = VpnController.addSub(name, url); if (e == null) onClose() else error = e }) {
                Text("افزودن و رفرش", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
    )
}
