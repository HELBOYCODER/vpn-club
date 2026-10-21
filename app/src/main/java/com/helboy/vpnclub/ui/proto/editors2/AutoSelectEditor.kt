package com.helboy.vpnclub.ui.proto.editors2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoLoading
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import dev.valid7996.narcicgeteway.core.AutoSelect
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform

private const val TITLE = "انتخاب خودکار"

/**
 * Edits an auto-select group. There is no stored profile behind it: the group is one of "all servers", "manual
 * servers" or one subscription ([existingId] is its auto id, e.g. "auto:all"; null starts from "all servers"), and the
 * way it behaves (test URL, check and sweep interval, switch margin, retry) is the app-wide auto-select setting.
 * Saving writes that setting, renames the group when it is a subscription, and can make the group the active profile.
 */
@Composable
fun AutoSelectEditor(existingId: String?, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val profiles by repo.profiles.collectAsState()
    val subscriptions by repo.subscriptions.collectAsState()
    val gone = remember(existingId, profiles, subscriptions) {
        existingId != null && (!AutoSelectLogic.isValidId(existingId) || repo.autoSelectProfile(existingId) == null)
    }
    if (gone) {
        EditorUnavailable(TITLE, "این گروه خودکار دیگر وجود ندارد؛ شاید اشتراکش حذف شده باشد.", onBack)
    } else {
        AutoSelectForm(existingId, onBack)
    }
}

@Composable
private fun AutoSelectForm(existingId: String?, onBack: () -> Unit) {
    val repo = ProtoEnv.repo
    val platform = LocalPlatform.current
    val settingsRepo = remember { runCatching { ProtoEnv.settings }.getOrNull() }

    val subscriptions by repo.subscriptions.collectAsState()
    val profiles by repo.profiles.collectAsState()
    val activeId by repo.activeId.collectAsState()
    val session by AutoSelect.session.collectAsState()

    var groupId by remember { mutableStateOf(existingId ?: AutoSelectLogic.idOf(null)) }
    val scope = AutoSelectLogic.scopeOf(groupId)
    val members = remember(profiles, groupId) { repo.autoSelectMembers(scope) }
    val currentLabel = AutoSelectLogic.scopeLabel(scope, subscriptions)

    var groupName by remember(groupId) { mutableStateOf(currentLabel) }
    var tuning by remember {
        mutableStateOf(AutoTuningForm.from(settingsRepo?.settings?.value ?: AppSettings()))
    }
    var activate by remember { mutableStateOf(existingId == null) }
    var error by remember { mutableStateOf<String?>(null) }

    val live = session?.takeIf { it.profileId == groupId }
    val canSave = tuning.valid && (!activate || members.isNotEmpty())

    fun save() {
        error = null
        runCatching {
            settingsRepo?.update { tuning.applyTo(it) }
            scope?.takeIf { it.isNotEmpty() }?.let { id ->
                if (groupName.isNotBlank() && groupName.trim() != currentLabel) repo.renameSubscription(id, groupName)
            }
            if (activate) repo.setActive(groupId)
        }.onSuccess {
            platform.toast(SAVED_TEXT)
            onBack()
        }.onFailure {
            error = "ذخیره نشد: ${it.message?.take(120) ?: "خطای نامشخص"}. دوباره امتحان کنید."
        }
    }

    ProtoPage(title = if (existingId == null) "انتخاب خودکار تازه" else "ویرایش $TITLE", onBack = onBack) {
        ProtoSection(
            title = "گروه سرورها",
            hint = "سریع‌ترینِ سرورهای این گروه اتصال را می‌گیرد؛ اگر از کار بیفتد، اتصال‌ها در یک لحظه جابه‌جا می‌شوند.",
        ) {
            ProtoChoice(
                title = null,
                options = AutoSelectLogic.groupOptions(subscriptions),
                selected = groupId,
                onSelect = { groupId = it },
            )
            EditorNote(
                "${members.size.fa()} سرور در «$currentLabel» هست." +
                    if (activeId == groupId) " این گروه الان فعال است." else "",
                tag = "proto_auto_members",
            )
            if (members.isEmpty()) {
                EditorNote(
                    "هیچ سروری در این گروه نیست. چند سرور اضافه کنید یا گروه دیگری را انتخاب کنید.",
                    NoteTone.Warn,
                    tag = "proto_auto_empty",
                )
            }
            if (AutoSelectLogic.renamable(scope)) {
                ProtoField(
                    label = "نام گروه",
                    value = groupName,
                    onValueChange = { groupName = it },
                    tag = "proto_auto_name",
                )
            }
        }

        ProtoSection(
            title = "تنظیم‌های سنجش",
            hint = "این تنظیم‌ها برای همهٔ گروه‌های خودکار یکی است و در اتصال بعدی اعمال می‌شود.",
        ) {
            if (settingsRepo == null) {
                EditorNote("تنظیمات برنامه در دسترس نیست؛ تغییرها ذخیره نمی‌شوند.", NoteTone.Warn)
            }
            ProtoField(
                label = "نشانی آزمون",
                value = tuning.testUrl,
                onValueChange = { tuning = tuning.copy(testUrl = it) },
                placeholder = AutoTuningForm.DEFAULT_URL,
                error = tuning.urlNote(),
                monospace = true,
                tag = "proto_auto_url",
            )
            ProtoChoice(
                title = "بررسی دوبارهٔ سرورِ در حال استفاده هر",
                options = AutoSelectLogic.withCurrent(AutoSelectLogic.CHECK_SECONDS, tuning.checkSeconds)
                    .map { it.toString() to "${it.fa()} ثانیه" },
                selected = tuning.checkSeconds.toString(),
                onSelect = { tuning = tuning.copy(checkSeconds = it.toInt()) },
            )
            ProtoChoice(
                title = "سنجش دوبارهٔ بقیهٔ سرورها هر",
                options = AutoSelectLogic.withCurrent(AutoSelectLogic.SWEEP_MINUTES, tuning.sweepMinutes)
                    .map { it.toString() to "${it.fa()} دقیقه" },
                selected = tuning.sweepMinutes.toString(),
                onSelect = { tuning = tuning.copy(sweepMinutes = it.toInt()) },
            )
            ProtoChoice(
                title = "جابه‌جایی به سرور سریع‌تر فقط وقتی سریع‌تر باشد به اندازهٔ",
                options = AutoSelectLogic.withCurrent(AutoSelectLogic.MARGIN_PERCENT, tuning.marginPercent)
                    .map { it.toString() to "٪${it.fa()}" },
                selected = tuning.marginPercent.toString(),
                onSelect = { tuning = tuning.copy(marginPercent = it.toInt()) },
            )
            ProtoSwitch(
                title = "جابه‌جا کردن اتصال‌های ناموفق",
                subtitle = "اتصالی که سرورش پاسخ ندهد به‌جای قطع شدن روی سرور بعدی ادامه پیدا می‌کند",
                checked = tuning.retry,
                onChange = { tuning = tuning.copy(retry = it) },
                tag = "proto_auto_retry",
            )
        }

        ProtoSection(title = "وضعیت زنده") {
            if (live == null) {
                EditorNote("وصل شوید تا به‌صورت زنده ببینید خودکار از کدام سرور استفاده می‌کند.", tag = "proto_auto_offline")
                members.sortedBy { (it.lastPingMs ?: -1).let { p -> if (p > 0) p else Int.MAX_VALUE } }
                    .forEach { StoredMemberRow(it) }
            } else {
                LivePanel(live, members)
            }
        }

        ProtoSection(title = "خودکار چطور کار می‌کند") {
            EditorNote(
                "سریع‌ترین سرورِ گروه اتصال‌های تازه را حمل می‌کند. اگر پاسخ ندهد، اتصال‌هایی که به آن خورده‌اند ظرف " +
                    "یکی‌دو ثانیه روی سرور بعدی ادامه پیدا می‌کنند، و خودکار همان‌جا می‌ماند تا سروری به‌وضوح سریع‌تر " +
                    "پیدا شود. خودکار از تنظیمات مسیریابی و DNS خودِ Hellboy VPN استفاده می‌کند.",
            )
        }

        ProtoSwitch(
            title = "این گروه را فعال کن",
            subtitle = "پروفایل فعال می‌شود: «${currentLabel}»",
            checked = activate,
            onChange = { activate = it },
            tag = "proto_auto_activate",
        )
        if (activate && members.isEmpty()) {
            EditorNote("برای فعال‌کردن، گروه دست‌کم یک سرور لازم دارد.", NoteTone.Error)
        }
        error?.let { EditorNote(it, NoteTone.Error, tag = "proto_auto_error") }
        ProtoButton(
            text = "ذخیره",
            icon = Icons.Rounded.Check,
            onClick = { save() },
            enabled = canSave,
            tag = "proto_save",
        )
    }
}

@Composable
private fun LivePanel(live: AutoSelect.Session, members: List<VpnProfile>) {
    val status = live.status
    val nameOf: (String) -> String? = { id -> members.firstOrNull { it.id == id }?.name }
    if (status == null) {
        ProtoLoading("در حال سنجش سرورها…")
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val color = when (status.phase) {
            AutoSelect.Phase.Ok -> StatusGreen
            AutoSelect.Phase.Degraded -> StatusAmber
            AutoSelect.Phase.Down -> StatusRed
            else -> NeonCyan
        }
        Chip(AutoSelectLogic.phaseText(status.phase), color)
    }
    val selectedName = live.selectedProfileId?.let(nameOf)
    if (selectedName != null) {
        Text("ترافیک شما از این سرور می‌گذرد", color = TextSecondary, fontSize = 12.sp)
        Text(selectedName, color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("proto_auto_now"))
        status.member(status.selected)?.let { m -> memberFacts(m)?.let { Text(it, color = TextSecondary, fontSize = 12.sp) } }
        if (!status.pinned.isNullOrBlank() && AutoSelect.supportsPinning) {
            OutlinedButton(onClick = { AutoSelect.pin("") }) { Text("برگرد به حالت خودکار") }
        }
    }

    val events = status.events.asReversed().take(6)
    if (events.isNotEmpty()) {
        Text("جابه‌جایی‌های اخیر", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        events.forEach { e ->
            fun tagName(tag: String) = live.memberProfiles[tag]?.let(nameOf) ?: tag
            val from = e.from.takeIf { it.isNotBlank() }?.let { tagName(it) }.orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    AutoSelectLogic.eventText(e.kind, tagName(e.to), from),
                    color = if (e.kind == AutoSelect.Reason.Failover) StatusRed else TextPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(AutoSelectLogic.agoText(System.currentTimeMillis() - e.atMs), color = TextSecondary, fontSize = 11.sp)
            }
        }
    }

    Text("سرورها", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    live.memberProfiles.entries.sortedWith(
        compareBy<Map.Entry<String, String>>(
            { AutoSelectLogic.healthRank(status.member(it.key)?.health ?: AutoSelect.Health.Unknown) },
            { status.member(it.key)?.delayMs?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE },
        ),
    ).forEach { (tag, profileId) ->
        val m = status.member(tag)
        val health = m?.health ?: AutoSelect.Health.Unknown
        val selected = tag == status.selected
        val pinned = tag == status.pinned
        ClubCard(Modifier.fillMaxWidth(), border = if (selected) NeonCyan else com.helboy.vpnclub.ui.theme.BorderDark) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nameOf(profileId) ?: tag,
                        color = TextPrimary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (AutoSelect.supportsPinning && !pinned) {
                        OutlinedButton(onClick = { AutoSelect.pin(tag) }) { Text("فقط از این سرور استفاده کن", fontSize = 11.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        AutoSelectLogic.healthText(health),
                        when (health) {
                            AutoSelect.Health.Alive -> StatusGreen
                            AutoSelect.Health.Suspect -> StatusAmber
                            AutoSelect.Health.Dead -> StatusRed
                            AutoSelect.Health.Unknown -> TextSecondary
                        },
                    )
                    if (pinned) Chip("ثابت", NeonCyan)
                    m?.let { memberFacts(it) }?.let { Text(it, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                }
                val err = m?.lastError?.takeIf {
                    it.isNotBlank() && (health == AutoSelect.Health.Dead || health == AutoSelect.Health.Suspect)
                }
                if (err != null) Text(err, color = StatusRed, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun StoredMemberRow(p: VpnProfile) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(p.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        p.lastPingMs?.let { ping ->
            Text(
                if (ping > 0) "${ping.fa()} ms" else "ناموفق",
                color = if (ping > 0) TextSecondary else StatusRed,
                fontSize = 12.sp,
            )
        }
    }
}

private fun memberFacts(m: AutoSelect.Member): String? {
    val parts = buildList {
        if (m.delayMs > 0) add("${m.delayMs.fa()} ms" + if (m.jitterMs > 0) " ±${m.jitterMs.fa()}" else "")
        if (m.connections > 0) add("${m.connections.fa()} اتصال باز")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
