package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Add or edit a Google Apps Script relay (MHRV): deployments, auth key, mode, SNI pool, CA certificate and the rest. */
@Composable
fun MhrvEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("رِله Apps Script", existingId, onBack, owns = { it.isMhrvRelay }) { existing -> MhrvBody(existing, onBack) }
}

@Composable
private fun MhrvBody(existing: VpnProfile?, onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var form by remember(existing?.id) { mutableStateOf(MhrvForm.from(existing?.mhrvRelaySettings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    var pasteMessage by remember { mutableStateOf<String?>(null) }
    var pasteFailed by remember { mutableStateOf(false) }
    val check = form.check()
    val draft = form.toProfile()

    HintText("رله‌ای از طریق Google Apps Script خودتان، با دامین‌فرانتینگ روی یک IP گوگل.")

    ProtoSection(title = "پروفایل") {
        ProtoOutlined("چسباندن کانفیگ از کلیپ‌بورد", onClick = {
            val parsed = runCatching { platform.readClipboard() }.getOrNull()?.let(MhrvRelayProfile::fromText)
            if (parsed == null) {
                pasteFailed = true
                pasteMessage = "کلیپ‌بورد کانفیگ MHRV ندارد (JSON، mhrv-rs:// یا نشانی Apps Script)."
            } else {
                form = form.withPasted(parsed)
                pasteFailed = false
                pasteMessage = "کانفیگ چسبانده شد."
            }
        }, tag = "proto_mhrv_paste")
        pasteMessage?.let { HintText(it, error = pasteFailed, modifier = Modifier.testTag("proto_mhrv_paste_result")) }
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        ProtoChoice(
            title = "حالت",
            options = MhrvForm.MODES,
            selected = form.mode,
            onSelect = { form = form.copy(mode = it) },
        )
    }

    if (draft.needsCredentials) {
        ProtoSection(
            title = "شناسه‌های Deployment (چرخش توکن)",
            hint = "اگر سهمیهٔ توکن اول تمام شود، هسته خودکار به بعدی می‌رود. نشانی کامل .../macros/s/.../exec هم پذیرفته می‌شود.",
        ) {
            DeploymentIds(
                ids = form.scriptIds,
                onChange = { touched.mark(MhrvForm.F_IDS); form = form.copy(scriptIds = it) },
                error = check.errors[MhrvForm.F_IDS]?.takeIf { touched.shows(MhrvForm.F_IDS) },
            )
            SecretField("کلید احراز هویت", form.authKey, { form = form.copy(authKey = it) }, MhrvForm.F_AUTH, check, touched, "proto_mhrv_auth")
        }
    }

    ProtoSection(title = "لبهٔ گوگل") {
        EField("IP گوگل", form.googleIp, { form = form.copy(googleIp = FormText.asciiDigits(it).trim()) }, "googleIp", check, touched, "proto_mhrv_ip",
            placeholder = MhrvRelayProfile.DEFAULT_GOOGLE_IP)
        EField("دامنهٔ جلو (SNI)", form.frontDomain, { form = form.copy(frontDomain = it.trim()) }, "frontDomain", check, touched, "proto_mhrv_front",
            placeholder = MhrvRelayProfile.DEFAULT_FRONT_DOMAIN)
    }

    SniPool(
        sniHosts = form.sniHosts,
        googleIp = form.googleIp,
        onChange = { form = form.copy(sniHosts = it) },
    )

    ProtoSection(title = "یوتیوب") {
        ProtoSwitch("یوتیوب هم از رله رد شود", form.youtubeViaRelay, { form = form.copy(youtubeViaRelay = it) },
            subtitle = "خاموش: یوتیوب مستقیم می‌رود و سهمیهٔ Apps Script مصرف نمی‌شود.", tag = "proto_mhrv_youtube")
    }

    if (draft.needsCa) CaSection()

    ProtoSection(title = "پیشرفته") {
        Fold("تنظیمات پیشرفته", "proto_mhrv_advanced") {
            EField("SOCKS5 بالادست برای TCP خام (host:port)", form.upstreamSocks5, { form = form.copy(upstreamSocks5 = it) }, MhrvForm.F_UPSTREAM,
                check, touched, "proto_mhrv_upstream", placeholder = "127.0.0.1:10808")
            StepperRow("رله‌های موازی", form.parallel, 1, 8, { form = form.copy(parallel = it) }, "proto_mhrv_parallel")
            ProtoSwitch("بررسی TLS لبهٔ گوگل", form.verifySsl, { form = form.copy(verifySsl = it) }, tag = "proto_mhrv_verify")
            ProtoSwitch("مسدود کردن QUIC (UDP 443)", form.blockQuic, { form = form.copy(blockQuic = it) }, tag = "proto_mhrv_quic")
            ProtoSwitch("مسدود کردن STUN و TURN (UDP 3478، 5349، 19302)", form.blockStun, { form = form.copy(blockStun = it) }, tag = "proto_mhrv_stun")
            ProtoSwitch("مسدود کردن DoH", form.blockDoh, { form = form.copy(blockDoh = it) }, tag = "proto_mhrv_blockdoh")
            ProtoSwitch("عبور DoH از رله", form.tunnelDoh, { form = form.copy(tunnelDoh = it) }, tag = "proto_mhrv_tunneldoh")
            StepperRow("گام ادغام بسته‌ها (ms)", form.coalesceStep, 5, 500, { form = form.copy(coalesceStep = it) }, "proto_mhrv_cstep", step = 5)
            StepperRow("حداکثر ادغام بسته‌ها (ms)", form.coalesceMax, 100, 5000, { form = form.copy(coalesceMax = it) }, "proto_mhrv_cmax", step = 100)
        }
    }

    SaveSection(
        valid = check.ok,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.mhrv(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}

/** The list of deployment IDs: edit each row, remove it, or paste several at once. */
@Composable
private fun DeploymentIds(ids: List<String>, onChange: (List<String>) -> Unit, error: String?) {
    var input by remember { mutableStateOf("") }
    if (ids.isNotEmpty()) Text("${ids.size.fa()} شناسه", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_mhrv_ids_count"))
    ids.forEachIndexed { index, id ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = id,
                onValueChange = { onChange(MhrvLists.editAt(ids, index, it)) },
                singleLine = true,
                label = { Text("#${(index + 1).fa()}", fontSize = 12.sp) },
                textStyle = TextStyle(fontSize = 13.sp),
                modifier = Modifier.weight(1f).testTag("proto_mhrv_id_$index"),
            )
            IconButton(onClick = { onChange(MhrvLists.removeAt(ids, index)) }, modifier = Modifier.testTag("proto_mhrv_id_remove_$index")) {
                Icon(Icons.Rounded.Close, "حذف", tint = StatusRed)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("یک یا چند شناسه یا نشانی را بچسبانید…", fontSize = 12.sp, color = TextTertiary) },
            maxLines = 3,
            textStyle = TextStyle(fontSize = 13.sp),
            modifier = Modifier.weight(1f).testTag("proto_mhrv_id_input"),
        )
        IconButton(
            onClick = { onChange(MhrvLists.addIds(ids, input)); input = "" },
            enabled = MhrvLists.parseIds(input).isNotEmpty(),
            modifier = Modifier.testTag("proto_mhrv_id_add"),
        ) { Icon(Icons.Rounded.Add, "افزودن", tint = NeonCyan) }
    }
    if (error != null) HintText(error, error = true)
}

/** The SNI rotation pool with a live test per name (and for all of them). */
@Composable
private fun SniPool(sniHosts: String, googleIp: String, onChange: (String) -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var added by remember { mutableStateOf<List<String>>(emptyList()) }
    var custom by remember { mutableStateOf("") }
    val results = remember { mutableStateMapOf<String, SniProbe>() }
    val active = MhrvLists.activePool(sniHosts).toSet()
    val shown = MhrvLists.shownPool(sniHosts, added)

    fun probe(sni: String) {
        results[sni] = SniProbe.Running
        scope.launch {
            val ip = googleIp.trim().ifEmpty { MhrvRelayProfile.DEFAULT_GOOGLE_IP }
            results[sni] = try {
                SniProbe.parse(withContext(Dispatchers.IO) { platform.testSni(ip, sni) })
            } catch (e: Exception) {
                SniProbe.Failed("خطا در اتصال")
            }
        }
    }

    ProtoSection(title = "فهرست SNI چرخشی", hint = "فقط نام‌های تیک‌خورده استفاده می‌شوند. تست، زمان دست‌دهی TLS با آن نام را نشان می‌دهد.") {
        ProtoOutlined("تست همه", onClick = { shown.forEach(::probe) }, enabled = results.values.none { it == SniProbe.Running }, tag = "proto_mhrv_sni_test_all")
        shown.forEach { sni ->
            val result = results[sni] ?: SniProbe.Idle
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sni in active, onCheckedChange = { onChange(MhrvLists.setEnabled(sniHosts, sni, it)) },
                    modifier = Modifier.testTag("proto_mhrv_sni_check_$sni"))
                Text(sni, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                when (result) {
                    SniProbe.Running -> CircularProgressIndicator(Modifier.padding(4.dp).testTag("proto_mhrv_sni_busy_$sni"), strokeWidth = 2.dp, color = NeonCyan)
                    is SniProbe.Ok -> Text("${result.ms.fa()} ms", color = StatusGreen, fontSize = 12.sp, modifier = Modifier.testTag("proto_mhrv_sni_ok_$sni"))
                    is SniProbe.Failed -> Text(result.reason.take(18), color = StatusRed, fontSize = 11.sp, modifier = Modifier.testTag("proto_mhrv_sni_fail_$sni"))
                    SniProbe.Idle -> Unit
                }
                ProtoOutlined("تست", onClick = { probe(sni) }, enabled = result != SniProbe.Running, tag = "proto_mhrv_sni_test_$sni")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("افزودن SNI دلخواه (دامنه)", fontSize = 12.sp) },
                placeholder = { Text("maps.google.com", fontSize = 12.sp, color = TextTertiary) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("proto_mhrv_sni_input"),
            )
            IconButton(
                onClick = {
                    val tokens = custom.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
                    if (tokens.isNotEmpty()) {
                        added = (added + tokens).distinct()
                        onChange(MhrvLists.addCustom(sniHosts, custom))
                        custom = ""
                    }
                },
                enabled = custom.isNotBlank(),
                modifier = Modifier.testTag("proto_mhrv_sni_add"),
            ) { Icon(Icons.Rounded.Add, "افزودن", tint = NeonCyan) }
        }
    }
}

/** Exporting and installing the relay's own CA so the browser trusts the locally decrypted HTTPS. */
@Composable
private fun CaSection() {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    ProtoSection(
        title = "گواهی CA",
        hint = "حالت Apps Script، HTTPS را روی خود گوشی رمزگشایی می‌کند. گواهی رله را نصب کنید تا مرورگر آن را بپذیرد؛ بسیاری از برنامه‌ها گواهی کاربر را نادیده می‌گیرند و برایشان باید حالت «تونل کامل» را بزنید.",
    ) {
        if (platform.supportsMhrvCa) {
            ProtoButton(
                text = "دریافت و نصب گواهی CA (پوشهٔ Downloads)",
                icon = Icons.Rounded.VerifiedUser,
                running = running,
                onClick = {
                    scope.launch {
                        running = true
                        message = null
                        try {
                            val result = withContext(Dispatchers.IO) { runCatching { platform.installMhrvCa() }.getOrDefault("") }
                            message = result.ifBlank { "نصب گواهی انجام نشد؛ دوباره تلاش کنید." }
                            if (result.isNotBlank()) platform.toast(result)
                        } finally {
                            running = false
                        }
                    }
                },
                tag = "proto_mhrv_ca_install",
            )
            message?.let { Text(it, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("proto_mhrv_ca_message")) }
        } else {
            HintText("نصب خودکار گواهی روی این دستگاه پشتیبانی نمی‌شود.")
        }
    }
}
