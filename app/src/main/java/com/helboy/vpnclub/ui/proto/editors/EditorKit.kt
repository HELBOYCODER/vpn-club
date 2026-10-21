package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoError
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/* Building blocks shared by the protocol editors. Everything is Hellboy's own look: ProtoKit pieces and theme tokens. */

/** Which fields the person has touched, so "required" messages do not shout at an empty, untouched form. */
@Stable
class Touched {
    var keys by mutableStateOf(emptySet<String>())
        private set

    fun mark(key: String) {
        if (key !in keys) keys = keys + key
    }

    fun shows(key: String): Boolean = key in keys
}

@Composable
fun rememberTouched(key: Any? = null): Touched = remember(key) { Touched() }

/** The page every editor sits in: header, then either the form, a "not found" error, or the read-only notice of a locked profile. */
@Composable
internal fun EditorFrame(
    title: String,
    existingId: String?,
    onBack: () -> Unit,
    owns: (VpnProfile) -> Boolean,
    content: @Composable ColumnScope.(VpnProfile?) -> Unit,
) {
    val existing = remember(existingId) { existingId?.let { id -> runCatching { ProtoEnv.repo.profile(id) }.getOrNull() } }
    ProtoPage(title = if (existingId == null) "افزودن $title" else "ویرایش $title", onBack = onBack) {
        when {
            existingId != null && existing == null -> {
                ProtoError("این پروفایل دیگر وجود ندارد. شاید حذف شده است.")
                ProtoOutlined("بازگشت", onBack, tag = "proto_cancel")
            }
            existing != null && !owns(existing) -> {
                ProtoError("این پروفایل از نوع دیگری است و در این صفحه ویرایش نمی‌شود.")
                ProtoOutlined("بازگشت", onBack, tag = "proto_cancel")
            }
            existing != null && existing.isLocked -> LockedNotice(existing, onBack)
            else -> content(this, existing)
        }
    }
}

@Composable
private fun LockedNotice(profile: VpnProfile, onBack: () -> Unit) {
    ProtoSection(
        title = "این پروفایل قفل است",
        hint = "پروفایل قفل‌شده فقط‌خواندنی است. سازنده‌اش جزئیات آن را پنهان و تغییرناپذیر کرده؛ " +
            "برای تغییر، قفلش را با رمز بردارید یا نسخهٔ تازه را از منبع اصلی بگیرید.",
        modifier = Modifier.testTag("proto_locked"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = NeonCyan)
            Text(profile.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Text(profile.transportLabel, color = TextSecondary, fontSize = 12.sp)
    }
    ProtoOutlined("بازگشت", onBack, tag = "proto_cancel")
}

/** A text field wired to a [FormCheck]: error under the field once touched, amber warning always. */
@Composable
internal fun EField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    key: String,
    check: FormCheck,
    touched: Touched,
    tag: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    monospace: Boolean = false,
    enabled: Boolean = true,
    transform: (String) -> String = { it },
) {
    Column(modifier.fillMaxWidth()) {
        ProtoField(
            label = label,
            value = value,
            onValueChange = { touched.mark(key); onChange(transform(it)) },
            placeholder = placeholder,
            error = check.errors[key]?.takeIf { touched.shows(key) },
            keyboard = keyboard,
            singleLine = minLines <= 1,
            minLines = minLines,
            monospace = monospace,
            enabled = enabled,
            tag = tag,
        )
        check.warnings[key]?.let { Text(it, color = StatusAmber, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp)) }
    }
}

/** A digits-only field. */
@Composable
internal fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    key: String,
    check: FormCheck,
    touched: Touched,
    tag: String,
    placeholder: String = "",
    maxDigits: Int = 9,
) = EField(
    label, value, onChange, key, check, touched, tag,
    placeholder = placeholder, keyboard = KeyboardType.Number, transform = { FormText.digitsOnly(it, maxDigits) },
)

/** A decimal-number field (digits and one dot). */
@Composable
internal fun DecimalField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    key: String,
    check: FormCheck,
    touched: Touched,
    tag: String,
    placeholder: String = "",
) = EField(
    label, value, onChange, key, check, touched, tag,
    placeholder = placeholder, keyboard = KeyboardType.Decimal, transform = { FormText.decimalOnly(it) },
)

/** A secret: masked, with an eye button to reveal it. [supporting] explains e.g. "already stored, leave empty to keep". */
@Composable
internal fun SecretField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    key: String,
    check: FormCheck,
    touched: Touched,
    tag: String,
    minLines: Int = 1,
    supporting: String? = null,
    monospace: Boolean = false,
) {
    var reveal by remember { mutableStateOf(false) }
    val error = check.errors[key]?.takeIf { touched.shows(key) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { touched.mark(key); onChange(it) },
            label = { Text(label, fontSize = 12.sp) },
            isError = error != null,
            singleLine = minLines <= 1,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = TextStyle(fontSize = 14.sp, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default),
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }, modifier = Modifier.testTag("${tag}_reveal")) {
                    Icon(
                        if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (reveal) "پنهان کردن" else "نمایش",
                        tint = TextSecondary,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        if (error != null) Text(error, color = StatusRed, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        else if (supporting != null) Text(supporting, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        check.warnings[key]?.let { Text(it, color = StatusAmber, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, start = 4.dp)) }
    }
}

/** Optional profile name. */
@Composable
internal fun NameField(value: String, onChange: (String) -> Unit, check: FormCheck, touched: Touched, hint: String = "اگر خالی بماند، نامی خودکار ساخته می‌شود.") {
    EField("نام پروفایل", value, onChange, "name", check, touched, "proto_name")
    Text(hint, color = TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
}

/** − value + */
@Composable
internal fun StepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    tag: String,
    step: Int = 1,
    display: (Int) -> String = { it.fa() },
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min, modifier = Modifier.testTag("${tag}_minus")) {
            Text("−", color = TextPrimary, fontSize = 20.sp)
        }
        Text(display(value), color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("${tag}_value"))
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max, modifier = Modifier.testTag("${tag}_plus")) {
            Text("+", color = TextPrimary, fontSize = 20.sp)
        }
    }
}

/** A tappable header that shows or hides its body (the "advanced" blocks). */
@Composable
internal fun Fold(title: String, tag: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = NeonCyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = NeonCyan)
        }
        if (open) content()
    }
}

@Composable
internal fun HintText(text: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, color = if (error) StatusRed else TextTertiary, fontSize = 11.sp, modifier = modifier)
}

/** A file-backed field: what is loaded, a pick button, a clear button, a spinner while the file is read. */
@Composable
internal fun FileImportRow(
    title: String,
    status: String?,
    emptyText: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)?,
    busy: Boolean,
    error: String?,
    tag: String,
    pickLabel: String = "انتخاب فایل",
    hint: String? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = TextSecondary, fontSize = 12.sp)
        Text(status?.takeIf { it.isNotBlank() } ?: emptyText, color = if (status.isNullOrBlank()) TextTertiary else TextPrimary, fontSize = 14.sp, maxLines = 2)
        if (busy) ProtoLoading("در حال خواندن فایل…")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProtoOutlined(pickLabel, onPick, enabled = !busy, tag = tag)
            if (onClear != null && !status.isNullOrBlank()) ProtoOutlined("پاک کردن", onClear, enabled = !busy, tag = "${tag}_clear")
        }
        if (error != null) HintText(error, error = true) else if (hint != null) HintText(hint)
    }
}

/** The bottom of every editor: save (disabled while the form is invalid), cancel, and the reason saving failed, if it did. */
@Composable
internal fun SaveSection(valid: Boolean, onSave: () -> Unit, onCancel: () -> Unit, saveError: String?, saving: Boolean = false) {
    if (saveError != null) Text(saveError, color = StatusRed, fontSize = 12.sp, modifier = Modifier.testTag("proto_save_error"))
    ProtoButton("ذخیره", Icons.Rounded.Check, onSave, enabled = valid, running = saving, tag = "proto_save")
    if (!valid) HintText("برای ذخیره، موارد قرمز را کامل کنید.", modifier = Modifier.testTag("proto_save_hint"))
    ProtoOutlined("انصراف", onCancel, tag = "proto_cancel")
}

/** Runs [block]; on an exception answers a message that says what failed, never a stack trace. */
internal inline fun saveGuarded(onFail: (String) -> Unit, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        onFail("ذخیره نشد. دوباره تلاش کنید؛ اگر تکرار شد برنامه را ببندید و باز کنید.")
    }
}

/** Picks a text file (a PEM key or certificate) and hands its text to [onText]; [busy] and [error] feed the screen. */
@Stable
class PemImport internal constructor(
    private val scope: CoroutineScope,
    private val platform: Platform,
    private val needs: String?,
    private val rejected: String,
    private val onText: (String) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        error = null
    }

    fun pick() {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            try {
                val file = try {
                    platform.pickFileBytes()
                } catch (e: Exception) {
                    error = CertFiles.PICK_FAILED
                    null
                }
                if (file != null) {
                    val text = PemFiles.readText(file.bytes, needs)
                    if (text == null) error = rejected else onText(text)
                }
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun rememberPemImport(needs: String?, rejected: String, onText: (String) -> Unit): PemImport {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onText)
    return remember(platform, scope, needs, rejected) { PemImport(scope, platform, needs, rejected) { latest.value(it) } }
}
