package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.domain.model.RulesetItem

/** Add / edit one routing rule. Save stays disabled until [checkDraft] is happy. */
@Composable
internal fun RuleEditorDialog(
    initial: RulesetItem,
    isNew: Boolean,
    targets: List<Pair<String, String>>,
    sniffingEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (RulesetItem) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial.toDraft()) }
    var touched by remember(initial.id) { mutableStateOf(!isNew) }
    fun edit(change: (RuleDraft) -> RuleDraft) {
        draft = change(draft)
        touched = true
    }

    val errors = checkDraft(draft)
    val outbounds = buildList {
        add(RulesetItem.OUTBOUND_PROXY to "پروکسی")
        add(RulesetItem.OUTBOUND_DIRECT to "مستقیم")
        add(RulesetItem.OUTBOUND_BLOCK to "مسدود")
        targets.forEach { (id, name) -> add(RulesetItem.profileTag(id) to name) }
        // keep a rule that points at a server that no longer exists selectable, so saving never rewrites it silently
        if (none { it.first == draft.outbound }) add(draft.outbound to outboundLabel(draft.outbound, targets))
    }

    ProtoDialog(
        title = if (isNew) "قاعدهٔ جدید" else "ویرایش قاعده",
        onDismiss = onDismiss,
        onConfirm = { if (errors.ok) onSave(draft.applyTo(initial)) },
        confirmEnabled = errors.ok,
    ) {
        ProtoField(
            label = "نام", value = draft.remarks, onValueChange = { edit { d -> d.copy(remarks = it) } },
            placeholder = "مثلاً: سایت‌های ایرانی", error = if (touched) errors.remarks else null, tag = "proto_rule_name",
        )
        ProtoChoice("خروجی", outbounds, draft.outbound, { edit { d -> d.copy(outbound = it) } })
        ProtoField(
            label = "دامنه‌ها", value = draft.domain, onValueChange = { edit { d -> d.copy(domain = it) } },
            placeholder = "domain:example.com, geosite:google, keyword:ads, full:a.b.com, regexp:^ad\\d+\\.",
            error = errors.domain, singleLine = false, minLines = 2, monospace = true, tag = "proto_rule_domain",
        )
        ProtoField(
            label = "IP / CIDR", value = draft.ip, onValueChange = { edit { d -> d.copy(ip = it) } },
            placeholder = "geoip:ir, 10.0.0.0/8, 2001:db8::/32, !geoip:cn",
            error = errors.ip, singleLine = false, minLines = 2, monospace = true, tag = "proto_rule_ip",
        )
        ProtoField(
            label = "پورت", value = draft.port, onValueChange = { edit { d -> d.copy(port = it) } },
            placeholder = "443 یا 8000-9000 یا 80,443", error = errors.port, monospace = true, tag = "proto_rule_port",
        )
        ProtoChoice(
            "شبکه",
            listOf("" to "هر کدام", "tcp" to "tcp", "udp" to "udp", "tcp,udp" to "tcp,udp"),
            draft.network,
            { edit { d -> d.copy(network = it) } },
        )
        ProtoField(
            label = "پروتکل (نیازمند شنود ترافیک)", value = draft.protocol, onValueChange = { edit { d -> d.copy(protocol = it) } },
            placeholder = KNOWN_PROTOCOLS.joinToString(", "), error = errors.protocol, tag = "proto_rule_protocol",
        )
        if (needsSniffingWarning(draft, sniffingEnabled)) {
            Text(MSG_PROTOCOL_SNIFFING, color = StatusAmber, fontSize = 11.sp, modifier = Modifier.testTag("proto_rule_sniff_warning"))
        }
        if (touched && errors.condition != null) {
            Text(errors.condition, color = StatusRed, fontSize = 11.sp, modifier = Modifier.testTag("proto_rule_no_condition"))
        }
        ProtoSwitch(
            title = "قفل",
            checked = draft.locked,
            onChange = { edit { d -> d.copy(locked = it) } },
            subtitle = "هنگام جایگزینی با پیش‌فرض یا وارد کردن، این قاعده حفظ می‌شود و حذف نمی‌شود.",
        )
        Column {
            Text(
                "ورودی‌ها را با ویرگول یا خط جدید جدا کنید. دامنهٔ ساده به‌صورت زیررشته تطبیق می‌خورد " +
                    "(example.com با notexample.community هم می‌خورد)؛ برای تطبیق دقیق از domain: یا full: استفاده کنید.",
                color = TextTertiary, fontSize = 11.sp,
            )
        }
    }
}
