package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextPrimary
import dev.valid7996.narcicgeteway.domain.config.Ikev2Auth
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import dev.valid7996.narcicgeteway.ui.servers.CertImport
import dev.valid7996.narcicgeteway.ui.servers.CertInstallResult
import dev.valid7996.narcicgeteway.ui.servers.CertKind
import dev.valid7996.narcicgeteway.ui.servers.Ikev2CertBridge
import kotlinx.coroutines.launch

/** Add or edit an IKEv2/IPsec profile (run by Android's own IKEv2 stack). */
@Composable
fun Ikev2Editor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("IKEv2", existingId, onBack, owns = { it.isIkev2 }) { existing -> Ikev2Body(existing, onBack) }
}

@Composable
private fun Ikev2Body(existing: VpnProfile?, onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var form by remember(existing?.id) { mutableStateOf(Ikev2Form.from(existing?.ikev2Settings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    var certBusy by remember { mutableStateOf(false) }
    var certError by remember { mutableStateOf<String?>(null) }
    var caBusy by remember { mutableStateOf(false) }
    var caError by remember { mutableStateOf<String?>(null) }

    val check = form.check(Ikev2CertBridge.validateProposal)
    val advancedIkeSupported = (Ikev2CertBridge.supportsAdvancedIke?.invoke() ?: true) && form.authType != Ikev2Auth.EAP_MSCHAPV2
    val stored = "از قبل ذخیره شده. خالی بگذارید تا همان بماند، یا مقدار تازه بنویسید تا جایگزین شود."

    ProtoSection(title = "سرور") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        EField("آدرس سرور", form.server, { form = form.copy(server = it) }, Ikev2Form.F_SERVER, check, touched, "proto_ikev2_server",
            placeholder = "vpn.example.com", keyboard = KeyboardType.Uri)
        ProtoChoice(
            title = "نوع VPN",
            options = listOf(
                Ikev2Auth.EAP_MSCHAPV2.name to "نام کاربری و رمز (EAP)",
                Ikev2Auth.CERTIFICATE.name to "گواهی",
                Ikev2Auth.PSK.name to "کلید مشترک",
            ),
            selected = form.authType.name,
            onSelect = { chosen -> Ikev2Auth.entries.firstOrNull { it.name == chosen }?.let { form = form.copy(authType = it) } },
        )
    }

    ProtoSection(title = "احراز هویت") {
        when (form.authType) {
            Ikev2Auth.EAP_MSCHAPV2, Ikev2Auth.EAP_TLS -> {
                EField("نام کاربری", form.username, { form = form.copy(username = it) }, Ikev2Form.F_USER, check, touched, "proto_ikev2_user")
                SecretField("رمز عبور", form.password, { form = form.copy(password = it) }, Ikev2Form.F_PASSWORD, check, touched,
                    "proto_ikev2_password", supporting = if (form.hasStoredPassword) stored else null)
            }
            Ikev2Auth.PSK -> SecretField("کلید از پیش‌مشترک", form.psk, { form = form.copy(psk = it) }, Ikev2Form.F_PSK, check, touched,
                "proto_ikev2_psk", supporting = if (form.hasStoredPsk) stored else null)
            Ikev2Auth.CERTIFICATE -> {
                Text("گواهی کاربر", color = TextSecondary, fontSize = 12.sp)
                Text(form.userCertAlias.ifBlank { "گواهی انتخاب نشده" }, color = TextPrimary, fontSize = 14.sp)
                if (certBusy) ProtoLoading("در حال آماده‌سازی گواهی…")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ikev2CertBridge.pickClientCertAlias?.let { pick ->
                        ProtoOutlined("انتخاب از انبار", onClick = {
                            certError = null
                            scope.launch {
                                certBusy = true
                                try { pick()?.let { form = form.copy(userCertAlias = it) } } finally { certBusy = false }
                            }
                        }, enabled = !certBusy, tag = "proto_ikev2_cert_choose")
                    }
                    CertImport.installToKeyChain?.let { installer ->
                        ProtoOutlined("وارد کردن از فایل", onClick = {
                            certError = null
                            scope.launch {
                                certBusy = true
                                try {
                                    when (val picked = CertFiles.pick(platform, CertKind.Pkcs12, "این فایل بستهٔ PKCS#12 نیست. گواهی کلاینت باید گواهی و کلید خصوصی را با هم داشته باشد (p12. یا pfx.).")) {
                                        CertPick.Cancelled -> Unit
                                        is CertPick.Rejected -> certError = picked.message
                                        is CertPick.Picked -> when (installer(picked.file.bytes, picked.file.name.substringBeforeLast('.'))) {
                                            CertInstallResult.Installed -> Ikev2CertBridge.pickClientCertAlias?.invoke()?.let { form = form.copy(userCertAlias = it) }
                                            CertInstallResult.Cancelled -> Unit
                                            CertInstallResult.Unsupported -> certError = "این دستگاه امکان نصب گواهی را ندارد."
                                        }
                                    }
                                } finally { certBusy = false }
                            }
                        }, enabled = !certBusy, tag = "proto_ikev2_cert_import")
                    }
                    if (form.userCertAlias.isNotBlank()) {
                        ProtoOutlined("پاک کردن", onClick = { form = form.copy(userCertAlias = "") }, enabled = !certBusy, tag = "proto_ikev2_cert_clear")
                    }
                }
                certError?.let { HintText(it, error = true) }
                    ?: check.errors[Ikev2Form.F_CERT]?.takeIf { touched.shows(Ikev2Form.F_CERT) || form.authType == Ikev2Auth.CERTIFICATE }?.let { HintText(it, error = true) }
                    ?: HintText("در انبار گواهی اندروید ذخیره می‌شود. کلید خصوصی هرگز از آن بیرون نمی‌آید و پروفایل فقط نام را نگه می‌دارد.")
            }
        }

        FileImportRow(
            title = "گواهی CA",
            status = if (form.caCertPem.isBlank()) null else form.caLabel.ifBlank { "واردشده" },
            emptyText = "انتخاب خودکار",
            onPick = {
                caError = null
                scope.launch {
                    caBusy = true
                    try {
                        val file = platform.pickFileBytes()
                        if (file != null) {
                            val info = runCatching { CertImport.inspect?.invoke(file.bytes, file.name) }.getOrNull()
                            val pem = info?.pem ?: Ikev2CertBridge.readCaCertPem?.invoke(file.bytes)
                            if (pem == null) caError = CertFiles.NOT_CERT else form = form.copy(caCertPem = pem, caLabel = info?.description.orEmpty())
                        }
                    } catch (e: Exception) {
                        caError = CertFiles.PICK_FAILED
                    } finally { caBusy = false }
                }
            },
            onClear = { form = form.copy(caCertPem = "", caLabel = ""); caError = null },
            busy = caBusy,
            error = caError,
            tag = "proto_ikev2_ca",
            hint = if (CertImport.canInstallCaCert?.invoke() == false) "اندروید ۱۱ به بعد اجازه نمی‌دهد یک برنامه گواهی CA نصب کند، بنابراین همراه همین پروفایل نگهداری می‌شود." else null,
        )
    }

    ProtoSection(title = "پیشرفته") {
        Fold("نمایش تنظیمات پیشرفته", "proto_ikev2_advanced") {
            EField("هویت سرور", form.remoteId, { form = form.copy(remoteId = it) }, "remoteId", check, touched, "proto_ikev2_remote_id",
                placeholder = "vpn.example.com")
            HintText("هویتی که سرور در گواهی‌اش ارائه می‌دهد، وقتی با آدرس بالا فرق دارد. خالی بماند، همان آدرس استفاده می‌شود.")
            EField("هویت کلاینت", form.localId, { form = form.copy(localId = it) }, "localId", check, touched, "proto_ikev2_local_id")
            HintText("هویتی که این دستگاه ارائه می‌دهد. خالی بماند، نام کاربری یا گواهی تعیینش می‌کند.")
            if (!advancedIkeSupported) HintText("شناسهٔ ریموت و پروپوزال سفارشی فقط برای پروفایل‌های گواهی و PSK روی اندروید ۱۳+ اعمال می‌شوند؛ اینجا نادیده گرفته می‌شوند.")
            NumberField("MTU دستگاه تونل", form.mtu, { form = form.copy(mtu = it) }, Ikev2Form.F_MTU, check, touched, "proto_ikev2_mtu", "1400", maxDigits = 4)
            Text("الگوریتم‌ها", color = TextPrimary, fontSize = 14.sp)
            HintText("در صورت نیاز به‌جای پیش‌فرض‌ها الگوریتم مشخص بدهید، جدا شده با خط تیره — aes256-sha256-modp2048.")
            EField("الگوریتم‌های IKEv2", form.ikeProposal, { form = form.copy(ikeProposal = it) }, Ikev2Form.F_IKE, check, touched, "proto_ikev2_ike",
                placeholder = "aes256-sha256-modp2048")
            EField("الگوریتم‌های ESP", form.espProposal, { form = form.copy(espProposal = it) }, Ikev2Form.F_ESP, check, touched, "proto_ikev2_esp",
                placeholder = "aes256-sha256")
            HintText("این تونل را خودِ اندروید اجرا می‌کند، پس مسیریابی، DNS، قواعد هر اپ و کلید قطع برای IKEv2 در دسترس نیستند. رومینگ MOBIKE همیشه روشن است.")
        }
    }

    SaveSection(
        valid = check.ok && !certBusy && !caBusy,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.ikev2(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}
