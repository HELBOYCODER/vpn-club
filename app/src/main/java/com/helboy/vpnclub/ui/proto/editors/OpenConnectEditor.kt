package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.servers.CertKind
import kotlinx.coroutines.launch

/**
 * Add or edit an OpenConnect profile. The login dialogs (password, two-factor, gateway forms) are not here:
 * the activity shows them when the tunnel is connecting.
 */
@Composable
fun OpenConnectEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("OpenConnect", existingId, onBack, owns = { it.isOpenConnect }) { existing -> OpenConnectBody(existing, onBack) }
}

@Composable
private fun OpenConnectBody(existing: VpnProfile?, onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var form by remember(existing?.id) { mutableStateOf(OpenConnectForm.from(existing?.openConnectSettings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    var p12Busy by remember { mutableStateOf(false) }
    var p12Error by remember { mutableStateOf<String?>(null) }
    val caImport = rememberPemImport("-----BEGIN", CertFiles.NOT_PEM) { form = form.copy(caCert = it) }
    val certImport = rememberPemImport("CERTIFICATE", CertFiles.NOT_CERT) { form = form.copy(clientCert = it) }
    val keyImport = rememberPemImport("PRIVATE KEY", "این فایل کلید خصوصی PEM نیست.") { form = form.copy(clientKey = it) }
    val check = form.check()

    ProtoSection(title = "سرور") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        EField("سرور (میزبان یا نشانی)", form.server, { form = form.copy(server = it) }, OpenConnectForm.F_SERVER, check, touched, "proto_oc_server",
            placeholder = "vpn.example.com", keyboard = KeyboardType.Uri)
        NumberField("پورت", form.port, { form = form.copy(port = it) }, OpenConnectForm.F_PORT, check, touched, "proto_oc_port", "443", maxDigits = 5)
        ProtoChoice(
            title = "پروتکل",
            options = OpenConnectProfile.PROTOCOLS,
            selected = form.protocol,
            onSelect = { form = form.copy(protocol = it) },
        )
    }

    ProtoSection(title = "ورود", hint = "اگر خالی بگذارید، هنگام اتصال از شما پرسیده می‌شود.") {
        EField("نام کاربری", form.username, { form = form.copy(username = it) }, "username", check, touched, "proto_oc_user")
        SecretField("گذرواژه", form.password, { form = form.copy(password = it) }, "password", check, touched, "proto_oc_password")
        EField("گروه احراز / پورتال", form.authgroup, { form = form.copy(authgroup = it) }, "authgroup", check, touched, "proto_oc_authgroup")
        EField("پین گواهی سرور (sha256:…)", form.certPin, { form = form.copy(certPin = it) }, "certPin", check, touched, "proto_oc_pin",
            placeholder = "sha256:…", monospace = true)
        HintText("قوانین مسیریابی، مسدودسازی جغرافیایی، گروه‌های سیاست، زنجیره پروکسی و عبور از شبکه محلی روی OpenConnect اثر ندارند — مسیرها را خودِ گیت‌وی تعیین می‌کند.")
    }

    ProtoSection(title = "پیشرفته") {
        Fold("تنظیمات پیشرفته", "proto_oc_advanced") {
            EField("SNI مربوط به TLS (اختیاری)", form.sni, { form = form.copy(sni = it) }, "sni", check, touched, "proto_oc_sni", placeholder = "vpn.example.com")
            EField("پروکسی (اختیاری)", form.proxy, { form = form.copy(proxy = it) }, "proxy", check, touched, "proto_oc_proxy", placeholder = "socks5://127.0.0.1:1080")
            NumberField("MTU (۰ = خودکار)", form.mtu, { form = form.copy(mtu = it) }, OpenConnectForm.F_MTU, check, touched, "proto_oc_mtu", "1400", maxDigits = 5)
            NumberField("مهلت اتصال مجدد (ثانیه)", form.reconnect, { form = form.copy(reconnect = it) }, OpenConnectForm.F_RECONNECT, check, touched, "proto_oc_reconnect", "300")
            EField("عامل کاربر (User-Agent)", form.userAgent, { form = form.copy(userAgent = it) }, "userAgent", check, touched, "proto_oc_ua",
                placeholder = OpenConnectProfile.defaultUserAgentFor(form.protocol))
            ProtoChoice(
                title = "سیستم‌عامل گزارش‌شده",
                options = OpenConnectProfile.REPORTED_OS_VALUES,
                selected = form.reportedOs,
                onSelect = { form = form.copy(reportedOs = it) },
            )

            FileImportRow(
                title = "گواهی کلاینت (بستهٔ PKCS#12)",
                status = if (form.certP12Base64.isBlank()) null else form.certP12Name.ifBlank { "بسته بارگذاری شد" },
                emptyText = "بستهٔ PKCS#12 انتخاب نشده",
                onPick = {
                    p12Error = null
                    scope.launch {
                        p12Busy = true
                        try {
                            when (val picked = CertFiles.pick(platform, CertKind.Pkcs12, CertFiles.NOT_PKCS12)) {
                                CertPick.Cancelled -> Unit
                                is CertPick.Rejected -> p12Error = picked.message
                                is CertPick.Picked -> form = form.copy(certP12Base64 = PemFiles.base64(picked.file.bytes), certP12Name = picked.file.name)
                            }
                        } finally { p12Busy = false }
                    }
                },
                onClear = { form = form.copy(certP12Base64 = "", certP12Name = ""); p12Error = null },
                busy = p12Busy,
                error = p12Error,
                tag = "proto_oc_p12",
                hint = "از یک فایل خوانده می‌شود. OpenConnect خودِ گواهی را لازم دارد، پس گواهی نصب‌شده در اندروید اینجا قابل استفاده نیست.",
            )
            if (form.certP12Base64.isNotBlank()) HintText("این بسته به‌جای فیلدهای PEM استفاده می‌شود و رمزش همان رمز کلید است.")

            PemBlock("گواهی CA (PEM)", form.caCert, { form = form.copy(caCert = it) }, caImport, "proto_oc_ca", check, touched)
            PemBlock("گواهی کلاینت (PEM)", form.clientCert, { form = form.copy(clientCert = it) }, certImport, "proto_oc_cert", check, touched,
                key = OpenConnectForm.F_CLIENT_CERT)
            SecretField("کلید کلاینت (PEM)", form.clientKey, { form = form.copy(clientKey = it) }, "clientKey", check, touched, "proto_oc_key",
                minLines = 3, monospace = true)
            FileImportRow(
                title = "کلید کلاینت از فایل",
                status = if (form.clientKey.isBlank()) null else "کلید بارگذاری شد",
                emptyText = "فایلی انتخاب نشده",
                onPick = keyImport::pick,
                onClear = { form = form.copy(clientKey = "") },
                busy = keyImport.busy,
                error = keyImport.error,
                tag = "proto_oc_key_pick",
                pickLabel = "بارگذاری از فایل",
            )
            SecretField("گذرواژه کلید", form.keyPassword, { form = form.copy(keyPassword = it) }, "keyPassword", check, touched, "proto_oc_key_password")

            ProtoChoice(
                title = "دو مرحله‌ای / توکن",
                options = OpenConnectProfile.TOKEN_MODES,
                selected = form.tokenMode,
                onSelect = { form = form.copy(tokenMode = it) },
            )
            if (form.tokenMode != OpenConnectProfile.TOKEN_NONE) {
                SecretField("راز توکن", form.tokenSecret, { form = form.copy(tokenSecret = it) }, OpenConnectForm.F_TOKEN, check, touched,
                    "proto_oc_token", minLines = 2)
            }
            ProtoSwitch("غیرفعال‌کردن DTLS", form.disableDtls, { form = form.copy(disableDtls = it) }, tag = "proto_oc_dtls")
            ProtoSwitch("غیرفعال کردن IPv6", form.disableIpv6, { form = form.copy(disableIpv6 = it) }, tag = "proto_oc_ipv6")
        }
    }

    SaveSection(
        valid = check.ok && !p12Busy,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.openConnect(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}

/** A multi-line PEM box with a "load from file" button under it. */
@Composable
private fun PemBlock(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    loader: PemImport,
    tag: String,
    check: FormCheck,
    touched: Touched,
    key: String = tag,
) {
    EField(label, value, onChange, key, check, touched, tag, minLines = 3, monospace = true)
    FileImportRow(
        title = "$label از فایل",
        status = if (value.isBlank()) null else "بارگذاری شد",
        emptyText = "فایلی انتخاب نشده",
        onPick = loader::pick,
        onClear = { onChange("") },
        busy = loader.busy,
        error = loader.error,
        tag = "${tag}_pick",
        pickLabel = "بارگذاری از فایل",
    )
}
