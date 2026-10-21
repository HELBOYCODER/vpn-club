package com.helboy.vpnclub.ui.proto.editors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoNavRow
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.TextPrimary
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/** Add or edit a Psiphon profile: exit country, mode, CDN fronting candidates and beast mode. */
@Composable
fun PsiphonEditor(existingId: String?, onBack: () -> Unit) {
    EditorFrame("سایفون", existingId, onBack, owns = { it.isPsiphon }) { existing -> PsiphonBody(existing, onBack) }
}

@Composable
private fun PsiphonBody(existing: VpnProfile?, onBack: () -> Unit) {
    var form by remember(existing?.id) { mutableStateOf(PsiphonForm.from(existing?.psiphonSettings(), existing?.name.orEmpty())) }
    val touched = rememberTouched(existing?.id)
    var saveError by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val check = form.check()
    val code = form.country.trim().uppercase()

    ProtoSection(title = "سایفون", hint = "با تنظیمات پیش‌فرض بدون هیچ ورودی دیگری کار می‌کند.") {
        NameField(form.name, { form = form.copy(name = it) }, check, touched)
        ProtoNavRow(
            title = "کشور خروج",
            hint = if (code.isEmpty()) "خودکار (سریع‌ترین)" else PsiphonForm.countryName(code),
            onClick = { picking = true },
            tag = "proto_psiphon_country",
        )
        check.warnings[PsiphonForm.F_COUNTRY]?.let { HintText(it, error = true) }
        ProtoChoice(
            title = "حالت",
            options = PsiphonForm.MODES,
            selected = form.mode,
            onSelect = { form = form.copy(mode = it) },
        )
    }

    ProtoSection(
        title = "CDN",
        hint = "اختیاری. آدرس و CIDR با کاما، فاصله یا خط جدید جدا شوند. در حالت‌های خودکار، مستقیم و CDN استفاده می‌شود.",
    ) {
        EField("IPهای CDN (هر خط یکی)", form.cdnIps, { form = form.copy(cdnIps = it) }, PsiphonForm.F_IPS, check, touched, "proto_psiphon_ips",
            minLines = 2, monospace = true)
        EField("نام‌های SNI مربوط به CDN (هر خط یکی)", form.cdnSni, { form = form.copy(cdnSni = it) }, PsiphonForm.F_SNI, check, touched, "proto_psiphon_sni",
            minLines = 2, monospace = true)
    }

    ProtoSection(title = "حالت Beast") {
        ProtoSwitch(
            title = "حالت Beast",
            checked = form.beast,
            onChange = { form = form.copy(beast = it) },
            subtitle = "همهٔ پروتکل‌ها را روی همهٔ سرورها هم‌زمان امتحان می‌کند. جایی وصل می‌شود که روش عادی نمی‌شود، ولی هنگام اتصال باتری و داده‌ی بیشتری مصرف می‌کند.",
            tag = "proto_psiphon_beast",
        )
    }

    SaveSection(
        valid = check.ok,
        onSave = {
            if (check.ok) saveGuarded({ saveError = it }) {
                EditorSave.psiphon(ProtoEnv.repo, existing, form.name, form.toProfile())
                onBack()
            }
        },
        onCancel = onBack,
        saveError = saveError,
    )

    if (picking) {
        CountryDialog(selected = code, onPick = { form = form.copy(country = it); picking = false }, onDismiss = { picking = false })
    }
}

@Composable
private fun CountryDialog(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query) { PsiphonForm.filterCountries(query) }
    ProtoDialog(title = "انتخاب کشور خروج", onDismiss = onDismiss) {
        ProtoField("جست‌وجو", query, { query = it }, tag = "proto_psiphon_country_search")
        CountryRow("", "خودکار (سریع‌ترین)", selected.isEmpty()) { onPick("") }
        shown.forEach { c -> CountryRow(c, PsiphonForm.countryName(c), c == selected) { onPick(c) } }
        if (shown.isEmpty()) Text("کشوری با این نام پیدا نشد.", color = TextPrimary, fontSize = 13.sp)
    }
}

@Composable
private fun CountryRow(code: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp).testTag("proto_psiphon_country_${code.ifEmpty { "auto" }}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 14.sp)
            if (code.isNotEmpty()) Text(code, color = com.helboy.vpnclub.ui.theme.TextTertiary, fontSize = 11.sp)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
