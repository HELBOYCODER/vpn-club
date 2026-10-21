package com.helboy.vpnclub.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import com.helboy.vpnclub.ui.theme.TextTertiary
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.helboy.vpnclub.corex.ConfigEditor
import com.helboy.vpnclub.corex.ServerEntry
import com.helboy.vpnclub.corex.VpnController
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary

/** QR کد سیاه روی سفید (برای اسکن با هر اپ). null = متن برای QR خیلی بلند است. */
fun qrBitmap(text: String, size: Int = 720): Bitmap? = try {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val px = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
} catch (_: Exception) { null }

fun shareText(ctx: Context, text: String, title: String = "اشتراک کانفیگ") {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** منوی یک سرور: کپی لینک · QR · اشتراک · ویرایش · حذف. */
@Composable
fun ServerActionsDialog(s: ServerEntry, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val link = remember(s) { ConfigEditor.shareLink(s) }
    when {
        showQr -> QrDialog(s.config.tag.ifEmpty { s.config.host }, link, onClose = { showQr = false })
        showEdit -> EditConfigDialog(s, onClose = { showEdit = false; onClose() })
        else -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text(s.config.tag.ifEmpty { s.config.host }, maxLines = 1) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (s.injected) "لینک با IP تمیز ${s.config.dialHost} (مستقیم برای دیگران قابل استفاده)"
                         else "لینک اصلی کانفیگ", color = TextSecondary, fontSize = 12.sp)
                    Text(link, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextPrimary, maxLines = 4,
                        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(8.dp))
                    ActionRow(Icons.Rounded.ContentCopy, if (copied) "کپی شد ✓" else "کپی لینک", NeonCyan) {
                        clipboard.setText(AnnotatedString(link)); copied = true
                    }
                    ActionRow(Icons.Rounded.QrCode2, "QR کد برای دیگران", NeonCyan) { showQr = true }
                    ActionRow(Icons.Rounded.Share, "اشتراک (تلگرام، واتساپ…)", NeonCyan) { shareText(ctx, link) }
                    ActionRow(Icons.Rounded.Edit, "ویرایش کانفیگ", StatusAmber) { showEdit = true }
                    ActionRow(Icons.Rounded.Delete, "حذف از لیست", StatusRed) { VpnController.removeServer(s); onClose() }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("بستن") } },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = color)
            Text(text, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun QrDialog(title: String, text: String, onClose: () -> Unit) {
    val bmp = remember(text) { qrBitmap(text) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, maxLines = 1) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bmp != null) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Image(bmp.asImageBitmap(), "QR", modifier = Modifier.size(260.dp))
                    }
                    Text("با v2rayNG، Hiddify، Hellboy VPN یا هر اپ دیگر اسکن کنید", color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                } else Text("لینک برای QR خیلی بلند است؛ از «کپی لینک» استفاده کنید.", color = StatusAmber)
            }
        },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("کپی لینک", color = NeonCyan) } },
        dismissButton = { TextButton(onClick = onClose) { Text("بستن") } },
    )
}

/** ویرایش کانفیگ یک سرور لیست (دستی، اسکن یا ساب). */
@Composable
fun EditConfigDialog(s: ServerEntry, onClose: () -> Unit) =
    FullConfigEditor(s.config, onSave = { VpnController.editServer(s, it) }, onClose = onClose)

/**
 * ویرایشگر کامل کانفیگ با همه‌ی گزینه‌های PattNG:
 *  سرور (نام، آدرس، پورت، UUID/رمز، flow/encryption/method) · ترنسپورت (tcp/ws/httpupgrade/xhttp/grpc و فیلدهایشان)
 *  · امنیت (none/tls/reality: SNI، fingerprint، ALPN، ECH، cipher suites، pbk/sid/spx/pqv) · پیشرفته (FinalMask)
 *  و حالت «لینک خام» برای تغییر کامل.
 */
@Composable
fun FullConfigEditor(config: com.helboy.vpnclub.corex.ProxyConfig, onSave: (com.helboy.vpnclub.corex.ProxyConfig) -> Unit, onClose: () -> Unit) {
    val p0 = remember(config) { ConfigEditor.paramsOf(config) }
    var name by remember { mutableStateOf(config.tag) }
    var address by remember { mutableStateOf(config.host) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var secret by remember { mutableStateOf(config.uuidOrUser) }
    val params = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(p0) } }
    var rawMode by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(config.raw.ifEmpty { config.toShareLink() }) }
    var error by remember { mutableStateOf<String?>(null) }
    val transport = ConfigEditor.hasTransportFields(config)
    val net = params["type"] ?: "tcp"
    val sec = params["security"] ?: "none"

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("ویرایش کانفیگ · ${config.scheme.uppercase()}") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { rawMode = false }) { Text("فرم کامل", color = if (!rawMode) NeonCyan else TextSecondary, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { rawMode = true }) { Text("لینک خام", color = if (rawMode) NeonCyan else TextSecondary, fontWeight = FontWeight.Bold) }
                }
                if (rawMode) {
                    OutlinedTextField(raw, { raw = it; error = null }, Modifier.fillMaxWidth().height(200.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        label = { Text("لینک کانفیگ") })
                } else {
                    Section("سرور")
                    Field("نام (Remarks)", name) { name = it }
                    Field("آدرس (Address)", address) { address = it }
                    Field("پورت (Port)", port, KeyboardType.Number) { port = it.filter(Char::isDigit).take(5) }
                    Field(if (config.scheme == "vless" || config.scheme == "vmess") "UUID (id)" else "رمز (Password)", secret) { secret = it }
                    ConfigEditor.protocolSpecs(config.scheme).forEach { SpecField(it, params) }
                    if (transport) {
                        Section("ترنسپورت (Network)")
                        Choice(ConfigEditor.NETWORKS, net) { params["type"] = it }
                        ConfigEditor.transportSpecs(net).forEach { SpecField(it, params) }
                        Section("امنیت (Security)")
                        Choice(if (config.scheme == "vmess") listOf("none", "tls") else ConfigEditor.SECURITIES, sec) { params["security"] = it }
                        ConfigEditor.securitySpecs(sec).forEach { SpecField(it, params) }
                        Section("پیشرفته")
                        SpecField(ConfigEditor.FINALMASK, params)
                        Text("allowInsecure در هسته‌ی جدید Xray حذف شده و قابل تنظیم نیست. پروفایل‌های فرگمنت ایران (همراه اول/ایرانسل) خودکار روی تست و اتصال اعمال می‌شوند.",
                            color = TextTertiary, fontSize = 10.sp)
                    }
                }
                error?.let { Text(it, color = StatusRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = if (rawMode) ConfigEditor.fromLink(raw)
                        else ConfigEditor.applyFull(config, name, address, port, secret, params.toMap())
                when (r) {
                    is ConfigEditor.Result.Ok -> { onSave(r.config); onClose() }
                    is ConfigEditor.Result.Invalid -> error = r.reason
                }
            }) { Text("ذخیره و تست", color = NeonCyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
    )
}

@Composable
private fun Section(title: String) {
    Text(title, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

/** انتخاب از چند گزینه (چیپ‌های افقی). */
@Composable
private fun Choice(options: List<String>, value: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val sel = o == value
            Text(o.ifEmpty { "—" }, color = if (sel) Color.Black else NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(if (sel) NeonCyan else NeonCyan.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable { onPick(o) }.padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

/** فیلد یک پارامتر لینک: متن آزاد + چیپ‌های پیشنهادی (اگر گزینه دارد). */
@Composable
private fun SpecField(spec: ConfigEditor.Spec, params: MutableMap<String, String>) {
    val v = params[spec.key].orEmpty()
    OutlinedTextField(v, { params[spec.key] = it }, Modifier.fillMaxWidth(), singleLine = spec.key !in setOf("extra", "fm", "ech"),
        label = { Text(spec.label, fontSize = 12.sp) },
        placeholder = { if (spec.hint.isNotEmpty()) Text(spec.hint, fontSize = 11.sp, color = TextTertiary) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp))
    if (spec.options.isNotEmpty()) Choice(spec.options, v) { params[spec.key] = it }
}

@Composable
private fun Field(label: String, value: String, kb: KeyboardType = KeyboardType.Uri, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label, fontSize = 12.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp), keyboardOptions = KeyboardOptions(keyboardType = kb))
}

/** وارد کردن کانفیگ: از کلیپ‌بورد یا چسباندن دستی (یک یا چند لینک، یا ساب base64). */
@Composable
fun ImportConfigDialog(onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(clipboard.getText()?.text?.takeIf { it.contains("://") }.orEmpty()) }
    val count = remember(text) { ConfigEditor.parseMany(text).size }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("افزودن کانفیگ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("لینک vless / vmess / trojan / ss — هر خط یکی. از کلیپ‌بورد پر شد اگر لینکی کپی کرده بودید.",
                    color = TextSecondary, fontSize = 12.sp)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(170.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
                Text("$count کانفیگ شناسایی شد", color = if (count > 0) NeonCyan else TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = count > 0, onClick = { VpnController.importConfigs(text); onClose() }) {
                Text("افزودن و تست", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
    )
}
