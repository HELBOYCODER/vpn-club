package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository
import dev.valid7996.narcicgeteway.domain.config.SniSpoofConfig
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add or edit an SNI spoof profile (the no-root engine), plus the app-wide engine settings, a connectivity test and
 * the engine's live status and log.
 */
@Composable
fun SniSpoofEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("SNI Spoof", existingId, onBack, owns = { it.isSniSpoof }) { existing -> SniSpoofBody(existing, onBack) }
}

@Composable
private fun SniSpoofBody(existing: VpnProfile?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val defaults = remember { runCatching { ProtoEnv.repo.getSniSpoofDefaults() }.getOrNull() }
    var form by remember(existing?.id) {
        mutableStateOf(SniSpoofForm.from(existing?.sniSpoofSettings(), existing?.name.orEmpty(), defaults))
    }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    var test by remember { mutableStateOf<ProbeResult?>(null) }
    var testing by remember { mutableStateOf(false) }
    val check = form.check()

    HintText("با تکه‌تکه کردن بسته‌های TLS و ارسال SNI جعلی، فیلترینگ را دور می‌زند. کافی است IP تمیز، پورت و SNI جعلی را مشخص کنید.")

    ProtoSection(title = "پروفایل") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        EField("IP تمیز / سرور واقعی", form.ip, { form = form.copy(ip = FormText.asciiDigits(it).trim()) }, SniSpoofForm.F_IP, check, touched,
            "proto_sni_ip", placeholder = SniSpoofConfig.DEFAULT_CONNECT_IP, keyboard = KeyboardType.Ascii)
        NumberField("پورت سرور", form.port, { form = form.copy(port = it) }, SniSpoofForm.F_PORT, check, touched, "proto_sni_port",
            SniSpoofConfig.DEFAULT_CONNECT_PORT.toString(), maxDigits = 5)
        EField("SNI جعلی", form.fakeSni, { form = form.copy(fakeSni = it.trim()) }, SniSpoofForm.F_SNI, check, touched, "proto_sni_fake",
            placeholder = SniSpoofConfig.DEFAULT_FAKE_SNI, keyboard = KeyboardType.Ascii)
        ProtoChoice(
            title = "روش عبور",
            options = SniSpoofConfig.METHODS.map { it to it },
            selected = form.method,
            onSelect = { form = form.copy(method = it) },
        )
    }

    ProtoSection(title = "تست اتصال", hint = "بررسی می‌کند IP تمیز روی این پورت از شبکهٔ فعلی جواب می‌دهد. خودِ SNI جعلی را آزمایش نمی‌کند.") {
        ProtoButton(
            text = "تست اتصال",
            icon = Icons.Rounded.NetworkPing,
            running = testing,
            enabled = form.ip.isNotBlank() && FormText.port(form.port) != null,
            onClick = {
                val port = FormText.port(form.port) ?: return@ProtoButton
                scope.launch {
                    testing = true
                    test = null
                    try { test = TcpProbe.connect(form.ip, port) } finally { testing = false }
                }
            },
            tag = "proto_sni_test",
        )
        when (val r = test) {
            is ProbeResult.Reachable -> Text("پاسخ داد · ${r.ms.fa()} میلی‌ثانیه", color = StatusGreen, fontSize = 13.sp, modifier = Modifier.testTag("proto_sni_test_result"))
            is ProbeResult.Unreachable -> Text("پاسخ نداد: ${r.reason}", color = StatusRed, fontSize = 13.sp, modifier = Modifier.testTag("proto_sni_test_result"))
            null -> Unit
        }
    }

    val settingsRepo = remember { runCatching { ProtoEnv.settings }.getOrNull() }
    EngineSection(settingsRepo)

    SaveSection(
        valid = check.ok,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.sniSpoof(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}

/** The app-wide no-root engine settings (applied at once, to every spoof profile) with the engine's status and log. */
@Composable
private fun EngineSection(settingsRepo: SettingsRepository?) {
    val platform = LocalPlatform.current
    var status by remember { mutableStateOf("") }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLog by remember { mutableStateOf(false) }

    // The engine runs in another process: what it says about itself is read from the files it writes.
    LaunchedEffect(platform) {
        while (true) {
            withContext(Dispatchers.IO) {
                status = runCatching { platform.rstaSpoofStatus() }.getOrDefault("")
                log = runCatching { platform.rstaSpoofLog() }.getOrDefault(emptyList())
            }
            delay(1_500)
        }
    }

    ProtoSection(
        title = "موتور بدون روت (برای همهٔ پروفایل‌ها)",
        hint = "مقدارهای زیر برای همهٔ پروفایل‌های SNI Spoof مشترک است و با «ذخیرهٔ موتور» همان لحظه اعمال می‌شود؛ ذخیرهٔ پروفایل لازم نیست.",
    ) {
        if (settingsRepo == null) {
            HintText("تنظیمات برنامه در دسترس نیست؛ فقط وضعیت موتور نمایش داده می‌شود.", error = true)
        } else {
            EngineForm(settingsRepo)
        }

        val tone = SpoofStatusText.tone(status)
        Text("وضعیت موتور", color = TextSecondary, fontSize = 12.sp)
        Text(
            SpoofStatusText.describe(status),
            color = when (tone) {
                SpoofStatusText.Tone.Active -> StatusGreen
                SpoofStatusText.Tone.Failed -> StatusRed
                SpoofStatusText.Tone.Idle -> TextPrimary
            },
            fontSize = 13.sp,
            modifier = Modifier.testTag("proto_sni_status"),
        )
        ProtoOutlined(if (showLog) "پنهان کردن لاگ" else "مشاهدهٔ لاگ", { showLog = !showLog }, tag = "proto_sni_log_toggle")
        if (showLog) {
            Text(
                log.takeLast(60).joinToString("\n").ifEmpty { "هنوز چیزی ثبت نشده." },
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().testTag("proto_sni_log"),
            )
        }
    }
}

@Composable
private fun EngineForm(settingsRepo: SettingsRepository) {
    val current by settingsRepo.settings.collectAsState()
    var engine by remember { mutableStateOf(SpoofEngineForm.from(settingsRepo.settings.value)) }
    val touched = rememberTouched()
    val check = engine.check()
    val dirty = !engine.sameAs(current)

    EField("IP اتصال", engine.ip, { engine = engine.copy(ip = FormText.asciiDigits(it).trim()) }, SpoofEngineForm.F_IP, check, touched,
        "proto_engine_ip", keyboard = KeyboardType.Ascii)
    NumberField("پورت اتصال", engine.port, { engine = engine.copy(port = it) }, SpoofEngineForm.F_PORT, check, touched, "proto_engine_port", maxDigits = 5)
    EField("SNI جعلی", engine.fakeSni, { engine = engine.copy(fakeSni = it.trim()) }, SpoofEngineForm.F_SNI, check, touched,
        "proto_engine_sni", keyboard = KeyboardType.Ascii)
    ProtoChoice(
        title = "روش عبور",
        options = SniSpoofConfig.METHODS.map { it to it },
        selected = engine.method,
        onSelect = { engine = engine.copy(method = it) },
    )
    ProtoButton(
        text = "ذخیرهٔ موتور",
        icon = Icons.Rounded.Check,
        enabled = check.ok && dirty,
        onClick = { settingsRepo.update { engine.applyTo(it) } },
        accent = StatusAmber,
        tag = "proto_engine_save",
    )
}
