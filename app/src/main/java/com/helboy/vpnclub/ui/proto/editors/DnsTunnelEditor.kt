package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile
import dev.valid7996.narcicgeteway.domain.config.DohPresets
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Add or edit a DNS tunnel (DNSTT or VayDNS). */
@Composable
fun DnsTunnelEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("تونل DNS", existingId, onBack, owns = { it.isDnsTunnel }) { existing -> DnsTunnelBody(existing, onBack) }
}

@Composable
private fun DnsTunnelBody(existing: VpnProfile?, onBack: () -> Unit) {
    var form by remember(existing?.id) { mutableStateOf(DnsTunnelForm.from(existing?.dnsTunnelSettings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    val sshKeyImport = rememberPemImport("PRIVATE KEY", "این فایل کلید خصوصی PEM نیست.") { form = form.copy(sshKey = it) }
    val check = form.check()

    ProtoSection(title = "تونل", hint = "دامنه‌ای که سرور تونل به آن پاسخ می‌دهد و کلید عمومی سرور.") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        ProtoChoice(
            title = "موتور",
            options = listOf(DnsTunnelProfile.ENGINE_DNSTT to "DNSTT", DnsTunnelProfile.ENGINE_VAYDNS to "VayDNS"),
            selected = form.engine,
            onSelect = { form = form.copy(engine = it) },
        )
        EField("دامنهٔ تونل", form.domain, { form = form.copy(domain = it) }, DnsTunnelForm.F_DOMAIN, check, touched, "proto_dns_domain",
            placeholder = "t.example.com", keyboard = KeyboardType.Uri)
        EField("کلید عمومی سرور (hex)", form.publicKey, { form = form.copy(publicKey = it) }, DnsTunnelForm.F_KEY, check, touched,
            "proto_dns_pubkey", monospace = true)
    }

    ProtoSection(title = "رزالور") {
        ProtoChoice(
            title = "ترنسپورت DNS",
            options = DnsTunnelProfile.TRANSPORTS,
            selected = form.transport,
            onSelect = { form = form.copy(transport = it) },
        )
        if (form.isDoh) {
            ProtoChoice(
                title = "سرور DoH آماده",
                options = DohPresets.SERVERS.map { it.url to it.name },
                selected = DohPresets.SERVERS.firstOrNull { it.url == form.dohUrl }?.url ?: "",
                onSelect = { form = form.copy(dohUrl = it) },
            )
            EField("آدرس DoH", form.dohUrl, { form = form.copy(dohUrl = it) }, DnsTunnelForm.F_DOH, check, touched, "proto_dns_doh",
                placeholder = "https://dns.google/dns-query", keyboard = KeyboardType.Uri)
        } else {
            EField("رزالور(های) DNS", form.resolvers, { form = form.copy(resolvers = it) }, "resolvers", check, touched, "proto_dns_resolvers",
                placeholder = "8.8.8.8\n1.1.1.1", minLines = 2)
            if (form.multiResolver) {
                ProtoChoice(
                    title = "حالت رزالور",
                    options = DnsTunnelProfile.RESOLVER_MODES.map { (v, _) -> v to if (v == DnsTunnelProfile.MODE_FANOUT) "مطمئن" else "سریع" },
                    selected = form.resolverMode,
                    onSelect = { form = form.copy(resolverMode = it) },
                )
                if (form.resolverMode == DnsTunnelProfile.MODE_ROUND_ROBIN) {
                    StepperRow("تعداد پخش", form.spread, 1, 5, { form = form.copy(spread = it) }, "proto_dns_spread")
                }
            }
        }
    }

    ProtoSection(title = if (form.isVaydns) "تنظیمات VayDNS" else "تنظیمات DNSTT") {
        if (!form.isVaydns) {
            ProtoSwitch("سرور معتبر (authoritative)", form.authoritative, { form = form.copy(authoritative = it) },
                subtitle = "فقط وقتی روشن کنید که رزالور سرور خودتان باشد.", tag = "proto_dns_authoritative")
            check.warnings[DnsTunnelForm.F_AUTHORITATIVE]?.let {
                HintText("رزالورهای عمومی (گوگل، کلادفلر) نرخ را محدود می‌کنند و اتصال را می‌بندند.", error = true)
            }
            ProtoChoice(
                title = "اندازهٔ کوئری DNS (بایت)",
                options = DnsTunnelProfile.PAYLOAD_PRESETS.map { it.toString() to if (it == 0) "کامل" else it.fa() },
                selected = form.payloadSize.toString(),
                onSelect = { form = form.copy(payloadSize = it.toIntOrNull() ?: 0) },
            )
        } else {
            ProtoChoice(
                title = "نوع رکورد",
                options = DnsTunnelProfile.RECORD_TYPES.map { it to it.uppercase() },
                selected = form.recordType,
                onSelect = { form = form.copy(recordType = it) },
            )
            Text("طول کوئری: ${form.qnameLen.fa()}", color = TextSecondary, fontSize = 12.sp)
            Slider(
                value = form.qnameLen.toFloat(),
                onValueChange = { form = form.copy(qnameLen = it.toInt()) },
                valueRange = 60f..253f,
                modifier = Modifier.testTag("proto_dns_qname"),
            )
            DecimalField("محدودیت نرخ کوئری در ثانیه (۰ = نامحدود)", form.rps, { form = form.copy(rps = it) }, DnsTunnelForm.F_RPS, check, touched,
                "proto_dns_rps", placeholder = "0")
            Fold("تنظیمات پیشرفته", "proto_dns_advanced") {
                NumberField("مهلت بی‌کاری (ثانیه)", form.idleTimeout, { form = form.copy(idleTimeout = it) }, DnsTunnelForm.F_IDLE, check, touched, "proto_dns_idle", "10")
                NumberField("کیپ‌الایو (ثانیه)", form.keepalive, { form = form.copy(keepalive = it) }, DnsTunnelForm.F_KEEPALIVE, check, touched, "proto_dns_keepalive", "2")
                NumberField("مهلت UDP (میلی‌ثانیه)", form.udpTimeout, { form = form.copy(udpTimeout = it) }, DnsTunnelForm.F_UDP, check, touched, "proto_dns_udp", "500")
                NumberField("حداکثر لیبل", form.maxLabels, { form = form.copy(maxLabels = it) }, DnsTunnelForm.F_LABELS, check, touched, "proto_dns_labels", "0")
                NumberField("اندازهٔ شناسهٔ کلاینت (بایت)", form.clientIdSize, { form = form.copy(clientIdSize = it) }, DnsTunnelForm.F_CLIENT_ID, check, touched, "proto_dns_clientid", "2")
            }
        }
    }

    ProtoSection(title = "احراز هویت SOCKS", hint = "اختیاری؛ فقط اگر سرور خروجی نام کاربری و رمز می‌خواهد.") {
        EField("نام کاربری SOCKS", form.socksUser, { form = form.copy(socksUser = it) }, "socksUser", check, touched, "proto_dns_socks_user")
        SecretField("رمز SOCKS", form.socksPass, { form = form.copy(socksPass = it) }, "socksPass", check, touched, "proto_dns_socks_pass")
    }

    ProtoSection(title = "SSH روی DNS", hint = "ترافیک را از یک سرور SSH هم عبور می‌دهد.") {
        ProtoSwitch("فعال‌سازی SSH", form.sshEnabled, { form = form.copy(sshEnabled = it) }, tag = "proto_dns_ssh_enable")
        if (form.sshEnabled) {
            EField("آدرس SSH", form.sshHost, { form = form.copy(sshHost = it) }, DnsTunnelForm.F_SSH_HOST, check, touched, "proto_dns_ssh_host",
                placeholder = "example.com", keyboard = KeyboardType.Uri)
            NumberField("پورت", form.sshPort, { form = form.copy(sshPort = it) }, DnsTunnelForm.F_SSH_PORT, check, touched, "proto_dns_ssh_port", "22", maxDigits = 5)
            EField("نام کاربری", form.sshUser, { form = form.copy(sshUser = it) }, "sshUser", check, touched, "proto_dns_ssh_user")
            ProtoChoice(
                title = "احراز هویت",
                options = listOf(DnsTunnelProfile.AUTH_PASSWORD to "رمز عبور", DnsTunnelProfile.AUTH_KEY to "کلید خصوصی"),
                selected = form.sshAuth,
                onSelect = { form = form.copy(sshAuth = it) },
            )
            if (form.sshAuth == DnsTunnelProfile.AUTH_KEY) {
                SecretField("کلید خصوصی (PEM)", form.sshKey, { form = form.copy(sshKey = it) }, DnsTunnelForm.F_SSH_KEY, check, touched,
                    "proto_dns_ssh_key", minLines = 4, monospace = true)
                FileImportRow(
                    title = "کلید از فایل",
                    status = if (form.sshKey.isBlank()) null else "کلید بارگذاری شد",
                    emptyText = "فایلی انتخاب نشده",
                    onPick = sshKeyImport::pick,
                    onClear = { form = form.copy(sshKey = "") },
                    busy = sshKeyImport.busy,
                    error = sshKeyImport.error,
                    tag = "proto_dns_ssh_key_pick",
                )
                SecretField("عبارت عبور کلید (اختیاری)", form.sshKeyPass, { form = form.copy(sshKeyPass = it) }, "sshKeyPass", check, touched,
                    "proto_dns_ssh_passphrase")
            } else {
                SecretField("رمز عبور SSH", form.sshPass, { form = form.copy(sshPass = it) }, "sshPass", check, touched, "proto_dns_ssh_password")
            }
            ProtoSwitch("عبور DNS از SSH", form.forwardDns, { form = form.copy(forwardDns = it) }, tag = "proto_dns_ssh_forward")
        }
    }

    SaveSection(
        valid = check.ok,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.dnsTunnel(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}
