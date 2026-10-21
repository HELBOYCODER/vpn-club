package com.helboy.vpnclub.ui.proto.engine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoConfirm
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.domain.config.effectiveLanSocksPort
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private typealias Update = ((AppSettings) -> AppSettings) -> Unit

/**
 * Every connection setting of the engine, grouped in collapsible cards. The groups come from [EngineSettingsTable]
 * (one row per AppSettings field), every change is written straight away through ProtoEnv.settings.update, and each
 * group can be put back to its defaults after a confirmation.
 */
@Composable
fun EngineSettingsPage(onBack: () -> Unit) {
    val repo = ProtoEnv.settings
    val settings by repo.settings.collectAsState()
    val platform = LocalPlatform.current
    var open by remember { mutableStateOf(setOf<String>()) }
    var resetting by remember { mutableStateOf<EngineSection?>(null) }
    val sections = remember(platform) {
        EngineSettingsTable.sections.filter { it.id != "tun" || platform.choosesTunEngine }
    }
    val update: Update = { transform -> repo.update(transform) }

    ProtoPage(title = "تنظیمات موتور اتصال", onBack = onBack) {
        ProtoSection(
            hint = "همه‌ی تغییرات همان لحظه ذخیره می‌شوند. بخش‌های دارای برچسب «اتصال بعدی» روی اتصال در حال اجرا اثر نمی‌گذارند؛ " +
                "یک بار قطع و وصل کنید. اگر مطمئن نیستید، مقدار پیش‌فرض بهترین انتخاب است.",
        ) {}
        sections.forEach { sec ->
            val expanded = sec.id in open
            ClubCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { open = if (expanded) open - sec.id else open + sec.id }
                            .testTag("proto_engine_section_${sec.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sec.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(sec.hint, color = TextSecondary, fontSize = 12.sp)
                        }
                        if (sec.nextConnect) Chip("اتصال بعدی", StatusAmber)
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null, tint = TextSecondary,
                        )
                    }
                    if (expanded) {
                        if (sec.nextConnect) {
                            Text(
                                "این تنظیمات از اتصال بعدی اعمال می‌شوند. اگر الان وصل هستید، یک بار قطع و دوباره وصل کنید.",
                                color = TextTertiary, fontSize = 11.sp,
                            )
                        }
                        if (sec.id == "mode" && platform.supportsBatteryOptimization) {
                            Text(
                                if (platform.isIgnoringBatteryOptimizations) "بهینه‌سازی باتری برای برنامه خاموش است (مناسب اتصال پایدار)."
                                else "بهینه‌سازی باتری روشن است و ممکن است اتصال را در پس‌زمینه ببندد.",
                                color = TextSecondary, fontSize = 12.sp,
                            )
                            ProtoOutlined("تنظیم بهینه‌سازی باتری", { platform.openBatteryOptimizationSettings() })
                        }
                        sec.controls.forEach { c -> ControlView(c, settings, update) }
                        if (sec.id == "proxy" && settings.proxySharing) LanShareInfo(settings)
                        Row(
                            Modifier.fillMaxWidth().clickable { resetting = sec }
                                .padding(top = 4.dp).testTag("proto_engine_reset_${sec.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.RestartAlt, null, tint = NeonCyan)
                            Text("بازگردانی پیش‌فرض این بخش", color = NeonCyan, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    resetting?.let { sec ->
        ProtoConfirm(
            title = "بازگردانی «${sec.title}»",
            text = "همه‌ی تنظیمات این بخش به مقدار پیش‌فرض برمی‌گردند. بخش‌های دیگر دست نمی‌خورند.",
            confirmLabel = "بازگردانی",
            danger = true,
            onConfirm = {
                repo.update { sec.restore(it, EngineDefaults.settings) }
                resetting = null
            },
            onDismiss = { resetting = null },
        )
    }
}

@Composable
private fun ControlView(c: EngineControl, s: AppSettings, update: Update) {
    if (!c.visibleWhen(s)) return
    val enabled = c.enabledWhen(s)
    val tag = "proto_engine_${c.id}"
    when (c) {
        is SwitchControl -> ProtoSwitch(
            title = c.title, checked = c.get(s), onChange = { v -> update { cur -> c.set(cur, v) } },
            subtitle = c.hint, enabled = enabled, tag = tag,
        )
        is ChoiceControl -> Box(Modifier.alpha(if (enabled) 1f else 0.45f).testTag(tag)) {
            ProtoChoice(
                title = c.title, options = c.options, selected = c.get(s),
                onSelect = { v -> if (enabled) update { cur -> c.set(cur, v) } },
                hint = c.hint,
            )
        }
        is TextControl -> TextRow(c, c.get(s), enabled, update, tag)
        is NumberControl -> NumberRow(c, c.get(s), enabled, update, tag)
        is InfoControl -> Column {
            Text(c.title, color = TextPrimary, fontSize = 14.sp)
            if (c.hint != null) Text(c.hint, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TextRow(c: TextControl, current: String, enabled: Boolean, update: Update, tag: String) {
    var draft by remember(c.id) { mutableStateOf(current) }
    // What the settings hold after our own last write (set() may trim), so only foreign changes reset the draft.
    var committed by remember(c.id) { mutableStateOf(current) }
    LaunchedEffect(current) {
        if (current != committed) {
            draft = current
            committed = current
        }
    }
    fun commit(v: String) {
        draft = v
        if (c.validate(v) == null) {
            committed = c.get(c.set(EngineDefaults.settings, v))
            update { cur -> c.set(cur, v) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProtoField(
            label = c.title, value = draft, onValueChange = { commit(it) },
            placeholder = c.placeholder, error = c.validate(draft),
            password = c.password, singleLine = !c.multiline, minLines = if (c.multiline) 3 else 1,
            monospace = c.monospace, enabled = enabled, tag = tag,
        )
        if (c.hint != null) Text(c.hint, color = TextTertiary, fontSize = 11.sp)
        if (c.presets.isNotEmpty()) {
            Box(Modifier.alpha(if (enabled) 1f else 0.45f)) {
                ProtoChoice(null, c.presets, draft, onSelect = { if (enabled) commit(it) })
            }
        }
    }
}

@Composable
private fun NumberRow(c: NumberControl, current: Int, enabled: Boolean, update: Update, tag: String) {
    var draft by remember(c.id) { mutableStateOf(current.toString()) }
    var committed by remember(c.id) { mutableStateOf(current) }
    LaunchedEffect(current) {
        if (current != committed) {
            draft = current.toString()
            committed = current
        }
    }
    val check = SettingsValidation.checkInt(draft, c.min, c.max)
    val error = when (check) {
        is Checked.Bad -> check.message
        is Checked.Ok -> c.extraCheck(check.value)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProtoField(
            label = if (c.unit.isEmpty()) c.title else "${c.title} (${c.unit})",
            value = draft,
            onValueChange = { v ->
                draft = v
                val ok = SettingsValidation.checkInt(v, c.min, c.max)
                if (ok is Checked.Ok && c.extraCheck(ok.value) == null) {
                    committed = ok.value
                    update { cur -> c.set(cur, ok.value) }
                }
            },
            error = error, keyboard = KeyboardType.Number, enabled = enabled, tag = tag,
        )
        if (c.hint != null) Text(c.hint, color = TextTertiary, fontSize = 11.sp)
    }
}

/** How another device on the Wi-Fi reaches the shared proxy; the addresses are read off the main thread. */
@Composable
private fun LanShareInfo(s: AppSettings) {
    val platform = LocalPlatform.current
    var addresses by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) {
        addresses = withContext(Dispatchers.IO) { runCatching { platform.lanIpv4Addresses() }.getOrDefault(emptyList()) }
    }
    val list = addresses
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("آدرس اتصال برای دستگاه‌های دیگر", color = TextPrimary, fontSize = 14.sp)
        when {
            list == null -> Text("در حال پیدا کردن آدرس…", color = TextTertiary, fontSize = 11.sp)
            list.isEmpty() -> Text(
                "آدرس شبکه‌ی محلی پیدا نشد. به Wi-Fi یا هات‌اسپات وصل شوید.",
                color = TextTertiary, fontSize = 11.sp,
            )
            else -> LanShare.lines(list, s.effectiveLanSocksPort, s.appendHttpProxy, s.socksUsername).forEach {
                Text(it, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
