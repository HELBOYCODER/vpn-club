package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Add or edit a MasterDNS tunnel. */
@Composable
fun MasterDnsEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("MasterDNS", existingId, onBack, owns = { it.isMasterDns }) { existing -> MasterDnsBody(existing, onBack) }
}

@Composable
private fun MasterDnsBody(existing: VpnProfile?, onBack: () -> Unit) {
    var form by remember(existing?.id) { mutableStateOf(MasterDnsForm.from(existing?.masterDnsSettings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    val check = form.check()

    ProtoSection(title = "تونل", hint = "دامنه(ها)ی سرور MasterDNS، کلید رمزنگاری و رزالورهایی که با آن‌ها کار می‌کند.") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        EField("دامنه‌های تونل", form.domains, { form = form.copy(domains = it) }, MasterDnsForm.F_DOMAINS, check, touched, "proto_mdns_domains",
            placeholder = "v.example.com", keyboard = KeyboardType.Uri, minLines = 2)
        SecretField("کلید رمزنگاری", form.key, { form = form.copy(key = it) }, MasterDnsForm.F_KEY, check, touched, "proto_mdns_key")
        ProtoChoice(
            title = "روش رمزنگاری",
            options = MasterDnsForm.METHODS.map { (v, label) -> v.toString() to label },
            selected = form.method.toString(),
            onSelect = { form = form.copy(method = it.toIntOrNull() ?: form.method) },
        )
        EField("رزالورها (هر خط یک آدرس IP)", form.resolvers, { form = form.copy(resolvers = it) }, MasterDnsForm.F_RESOLVERS, check, touched,
            "proto_mdns_resolvers", placeholder = "8.8.8.8\n1.1.1.1:53", minLines = 3)
    }

    ProtoSection(title = "پیشرفته") {
        Fold("تنظیمات پیشرفته", "proto_mdns_advanced") {
            ProtoChoice(
                title = "توزیع بار رزالورها",
                options = MasterDnsForm.BALANCING.map { (v, label) -> v.toString() to label },
                selected = form.balancing.toString(),
                onSelect = { form = form.copy(balancing = it.toIntOrNull() ?: form.balancing) },
            )
            StepperRow("تکرار بسته‌ها", form.duplication, 1, 10, { form = form.copy(duplication = it) }, "proto_mdns_dup")
            ProtoChoice(
                title = "فشرده‌سازی",
                options = MasterDnsForm.COMPRESSION.map { (v, label) -> v.toString() to label },
                selected = form.compression.toString(),
                onSelect = { form = form.copy(compression = it.toIntOrNull() ?: form.compression) },
            )
            ProtoSwitch("غیرفعال‌سازی خودکار رزالورهای مرده", form.autoDisable, { form = form.copy(autoDisable = it) }, tag = "proto_mdns_autodisable")
        }
    }

    SaveSection(
        valid = check.ok,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.masterDns(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )
}
