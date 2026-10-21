package com.helboy.vpnclub.ui.proto.importing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.screen.qrBitmap
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Small pieces shared by the import page, the deep-link host and the lock-share page.
 */

/** This installation's device code with a copy button; the person sends it to whoever locks a config for them. */
@Composable
internal fun DeviceCodeRow(modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val code = remember { runCatching { ProtoEnv.repo.nrgDeviceId() }.getOrDefault("") }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("کد این دستگاه", color = TextSecondary, fontSize = 11.sp)
            Text(
                code.ifEmpty { "—" }, color = StatusAmber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, modifier = Modifier.testTag("proto_device_code"),
            )
        }
        TextButton(
            onClick = {
                if (code.isNotEmpty()) {
                    platform.copyToClipboard(code)
                    platform.toast("کد دستگاه کپی شد")
                }
            },
            enabled = code.isNotEmpty(),
        ) { Text("کپی", color = NeonCyan) }
    }
}

/** Asks for the key of a sealed config and opens it. [onUnlocked] runs once it is stored. */
@Composable
internal fun UnlockDialog(bytes: ByteArray, meta: ZsxMetadata, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val title = meta.nameEn.ifBlank { meta.nameFa }.ifBlank { "کانفیگ قفل‌شده" }

    ProtoDialog(
        title = "بازگشایی کانفیگ قفل‌شده",
        onDismiss = { if (!running) onDismiss() },
        confirmLabel = if (running) "در حال بازگشایی…" else "بازگشایی و ذخیره",
        confirmEnabled = !running && key.isNotBlank(),
        onConfirm = {
            running = true
            error = null
            scope.launch {
                val repo = ProtoEnv.repo
                val problem = ImportEngine.unlock(repo, bytes, meta, key)
                running = false
                if (problem == null) {
                    platform.toast("کانفیگ قفل‌شده باز و اضافه شد.")
                    onUnlocked()
                } else {
                    error = problem
                }
            }
        },
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
        if (meta.note.isNotBlank()) Text(meta.note, color = TextSecondary, fontSize = 12.sp)
        meta.expiresAt?.let { at ->
            Text("اعتبار: " + ImportLogic.remainingText(at, System.currentTimeMillis()), color = TextSecondary, fontSize = 12.sp)
        }
        Text(
            "این کانفیگ با کلید اختصاصی قفل شده است. کلیدی را که فرستنده داده وارد کنید.",
            color = TextSecondary, fontSize = 12.sp,
        )
        ProtoField(
            label = "کلید اختصاصی", value = key, onValueChange = { key = it; error = null },
            placeholder = "NG-XXXX-XXXX-XXXX", monospace = true, error = error, tag = "proto_unlock_key",
        )
        ProtoOutlined("جای‌گذاری کلید از کلیپ‌بورد", onClick = {
            val clip = platform.readClipboard()
            if (!clip.isNullOrBlank()) {
                key = LockedConfigExtractor.extractKey(clip) ?: clip.trim()
                error = null
            }
        })
        DeviceCodeRow()
    }
}

/** Asks for the private key of a short link that was pasted without it. */
@Composable
internal fun RelayKeyDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    ProtoDialog(
        title = "کلید اختصاصی",
        onDismiss = onDismiss,
        confirmLabel = "فعال‌سازی",
        confirmEnabled = key.isNotBlank(),
        onConfirm = { onSubmit(key.trim()) },
    ) {
        Text("این لینک کوتاه فقط با کلید اختصاصی باز می‌شود. کلید را وارد کنید.", color = TextSecondary, fontSize = 12.sp)
        ProtoField(
            label = "کلید", value = key, onValueChange = { key = it },
            placeholder = "NG-XXXX-XXXX-XXXX", monospace = true, tag = "proto_relay_key",
        )
    }
}

/** Username and password for an OpenVPN profile that asks for them. */
@Composable
internal fun OvpnCredentialsDialog(name: String, onDismiss: () -> Unit, onSubmit: (user: String, password: String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    ProtoDialog(
        title = "ورود به $name",
        onDismiss = onDismiss,
        confirmLabel = "افزودن",
        confirmEnabled = user.isNotBlank(),
        onConfirm = { onSubmit(user, pass) },
    ) {
        Text("این پروفایل OpenVPN نام کاربری و رمز می‌خواهد و چیزی در خود فایل نیست.", color = TextSecondary, fontSize = 12.sp)
        ProtoField(label = "نام کاربری", value = user, onValueChange = { user = it }, tag = "proto_ovpn_user")
        ProtoField(label = "رمز عبور", value = pass, onValueChange = { pass = it }, password = true, tag = "proto_ovpn_pass")
    }
}

private sealed interface QrState {
    data object Loading : QrState
    data object TooLarge : QrState
    class Ready(val bitmap: Bitmap) : QrState
}

/** A black-on-white QR code for [text] (rendered off the main thread), or a hint when the text is too long. */
@Composable
internal fun ProtoQr(text: String, modifier: Modifier = Modifier) {
    val state by produceState<QrState>(QrState.Loading, text) {
        value = withContext(Dispatchers.Default) { qrBitmap(text)?.let { QrState.Ready(it) } ?: QrState.TooLarge }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (val s = state) {
            QrState.Loading -> Text("در حال ساخت QR…", color = TextTertiary, fontSize = 12.sp)
            QrState.TooLarge -> Text("این متن برای QR خیلی بلند است؛ از کپی یا فایل استفاده کنید.", color = StatusRed, fontSize = 12.sp)
            is QrState.Ready -> Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Image(s.bitmap.asImageBitmap(), "QR", modifier = Modifier.size(240.dp))
            }
        }
    }
}

/** Little coloured line under a field or inside a card: used for results and errors. */
@Composable
internal fun StatusLine(text: String, color: Color, modifier: Modifier = Modifier, tag: String? = null) {
    Text(
        text, color = color, fontSize = 12.sp,
        modifier = modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
    )
}
