package com.helboy.vpnclub.ui.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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

/** ویرایش کانفیگ: فیلدها یا متن خام لینک. پس از ذخیره، تست واقعی و جایگزینی. */
@Composable
fun EditConfigDialog(s: ServerEntry, onClose: () -> Unit) {
    val f0 = remember(s) { ConfigEditor.fieldsOf(s.config) }
    var name by remember { mutableStateOf(f0.name) }
    var address by remember { mutableStateOf(f0.address) }
    var port by remember { mutableStateOf(f0.port) }
    var sni by remember { mutableStateOf(f0.sni) }
    var host by remember { mutableStateOf(f0.host) }
    var path by remember { mutableStateOf(f0.path) }
    var rawMode by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(s.config.raw.ifEmpty { s.config.toShareLink() }) }
    var error by remember { mutableStateOf<String?>(null) }
    val transport = ConfigEditor.hasTransportFields(s.config)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("ویرایش کانفیگ · ${s.config.scheme.uppercase()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { rawMode = false }) { Text("فیلدها", color = if (!rawMode) NeonCyan else TextSecondary, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { rawMode = true }) { Text("لینک خام", color = if (rawMode) NeonCyan else TextSecondary, fontWeight = FontWeight.Bold) }
                }
                if (rawMode) {
                    OutlinedTextField(raw, { raw = it; error = null }, Modifier.fillMaxWidth().height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        label = { Text("لینک کانفیگ") })
                } else {
                    Field("نام", name) { name = it }
                    Field("آدرس سرور (دامنه یا IP)", address) { address = it }
                    Field("پورت", port, KeyboardType.Number) { port = it.filter(Char::isDigit).take(5) }
                    if (transport) {
                        Field("SNI", sni) { sni = it }
                        Field("Host", host) { host = it }
                        Field("Path", path) { path = it }
                    }
                    Text("UUID/رمز و بقیه‌ی تنظیمات دست‌نخورده می‌مانند. برای تغییر کامل از «لینک خام» استفاده کنید.",
                        color = TextSecondary, fontSize = 11.sp)
                }
                error?.let { Text(it, color = StatusRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val r = if (rawMode) ConfigEditor.fromLink(raw)
                        else ConfigEditor.apply(s.config, ConfigEditor.Fields(name, address, port, sni, host, path))
                when (r) {
                    is ConfigEditor.Result.Ok -> { VpnController.editServer(s, r.config); onClose() }
                    is ConfigEditor.Result.Invalid -> error = r.reason
                }
            }) { Text("ذخیره و تست", color = NeonCyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("لغو") } },
    )
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
