package com.helboy.vpnclub.ui.proto.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helboy.vpnclub.ui.components.Chip
import com.helboy.vpnclub.ui.components.ClubCard
import com.helboy.vpnclub.ui.fa
import com.helboy.vpnclub.ui.proto.ProtoButton
import com.helboy.vpnclub.ui.proto.ProtoChoice
import com.helboy.vpnclub.ui.proto.ProtoConfirm
import com.helboy.vpnclub.ui.proto.ProtoDialog
import com.helboy.vpnclub.ui.proto.ProtoEmpty
import com.helboy.vpnclub.ui.proto.ProtoEnv
import com.helboy.vpnclub.ui.proto.ProtoField
import com.helboy.vpnclub.ui.proto.ProtoOutlined
import com.helboy.vpnclub.ui.proto.ProtoPage
import com.helboy.vpnclub.ui.proto.ProtoSection
import com.helboy.vpnclub.ui.proto.ProtoSwitch
import com.helboy.vpnclub.ui.theme.BorderDark
import com.helboy.vpnclub.ui.theme.NeonCyan
import com.helboy.vpnclub.ui.theme.StatusAmber
import com.helboy.vpnclub.ui.theme.StatusGreen
import com.helboy.vpnclub.ui.theme.StatusRed
import com.helboy.vpnclub.ui.theme.TextPrimary
import com.helboy.vpnclub.ui.theme.TextSecondary
import com.helboy.vpnclub.ui.theme.TextTertiary
import dev.valid7996.narcicgeteway.domain.config.ProfileSource
import dev.valid7996.narcicgeteway.domain.config.RulesetTransfer
import dev.valid7996.narcicgeteway.domain.model.DomainStrategy
import dev.valid7996.narcicgeteway.domain.model.RoutingMode
import dev.valid7996.narcicgeteway.domain.model.RoutingPresetType
import dev.valid7996.narcicgeteway.domain.model.RoutingPresets
import dev.valid7996.narcicgeteway.domain.model.RulesetItem
import dev.valid7996.narcicgeteway.domain.model.RulesetPresets
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The engine's routing: mode, domain strategy, ad blocking and the ordered rule list (add, edit, delete, reorder,
 * enable, presets, import and export). Everything is stored straight into the engine's settings.
 */
@Composable
fun RoutingPage(onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val store = ProtoEnv.settings
    val settings by store.settings.collectAsState()
    val profiles by ProtoEnv.repo.profiles.collectAsState()
    val scope = rememberCoroutineScope()

    val rules = remember(settings) { effectiveRules(settings) }
    val targets = remember(profiles) { profiles.filter { it.source is ProfileSource.Link }.map { it.id to it.name } }

    fun mutate(change: (List<RulesetItem>) -> List<RulesetItem>) {
        store.update { s -> s.withRules(change(effectiveRules(s))) }
    }

    var editing by remember { mutableStateOf<RulesetItem?>(null) }
    var deleting by remember { mutableStateOf<RulesetItem?>(null) }
    var presetsOpen by remember { mutableStateOf(false) }
    var replaceWith by remember { mutableStateOf<RoutingPresetType?>(null) }
    var importOpen by remember { mutableStateOf(false) }

    val geo = platform.geoAssets
    val geoMissing by produceState(false, geo, rules) {
        value = geo != null && usesGeoData(rules) && withContext(Dispatchers.IO) { !geo.allPresent() }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            isNew = rules.none { it.id == rule.id },
            targets = targets,
            sniffingEnabled = settings.sniffingEnabled,
            onDismiss = { editing = null },
            onSave = { saved ->
                mutate { list -> if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved }
                editing = null
            },
        )
    }
    deleting?.let { rule ->
        ProtoConfirm(
            title = "قاعده حذف شود؟",
            text = "«${rule.remarks.ifBlank { outboundLabel(rule.outboundTag, targets) }}» از قاعده‌های مسیریابی شما حذف می‌شود.",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { mutate { l -> l.filterNot { it.id == rule.id } }; deleting = null },
            onDismiss = { deleting = null },
        )
    }
    replaceWith?.let { type ->
        ProtoConfirm(
            title = "جایگزینی با «${presetTypeLabel(type)}»؟",
            text = "همهٔ قاعده‌های فعلی (به‌جز قفل‌شده‌ها) حذف و با این مجموعه عوض می‌شوند.",
            confirmLabel = "جایگزین کن",
            danger = true,
            onConfirm = {
                mutate { RoutingPresets.apply(it, RoutingPresets.rules(type)) }
                replaceWith = null
                presetsOpen = false
            },
            onDismiss = { replaceWith = null },
        )
    }
    if (presetsOpen) {
        ProtoDialog(title = "پیش‌فرض‌ها", onDismiss = { presetsOpen = false }) {
            Text("افزودن به فهرست فعلی", color = TextSecondary, fontSize = 12.sp)
            RulesetPresets.all().forEach { preset ->
                val fresh = presetFreshCount(rules, preset.rules())
                ProtoOutlined(
                    text = presetLabel(preset.label) + if (fresh == 0) " (از قبل هست)" else "",
                    enabled = fresh > 0,
                    onClick = { mutate { addPreset(it, preset.rules()) }; presetsOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("جایگزینی کل فهرست", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            RoutingPresetType.entries.forEach { type ->
                ProtoOutlined(text = presetTypeLabel(type), onClick = { replaceWith = type }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (importOpen) {
        ImportRulesDialog(
            existing = rules,
            onDismiss = { importOpen = false },
            onApply = { incoming, mode ->
                mutate { applyImport(it, incoming, mode) }
                platform.toast("${incoming.size.fa()} قاعده وارد شد")
                importOpen = false
            },
        )
    }

    ProtoPage(title = "مسیریابی", onBack = onBack) {
        if (hasLegacyRules(settings)) {
            ProtoSection(title = "تبدیل قاعده‌های قدیمی", border = StatusAmber) {
                Text(
                    "قاعده‌های متنی قدیمی (پروکسی / مستقیم / مسدود) به قاعدهٔ جدید تبدیل شده و در فهرست پایین دیده می‌شوند، " +
                        "ولی هنوز ذخیره نشده‌اند. با تبدیل، متن قدیمی پاک و فهرست جدید ذخیره می‌شود.",
                    color = TextSecondary, fontSize = 12.sp,
                )
                ProtoButton("تبدیل و ذخیره", Icons.Rounded.Bolt, onClick = { mutate { it } }, tag = "proto_migrate")
            }
        }

        ProtoSection(title = "حالت مسیریابی", hint = "برای ترافیکی که با هیچ قاعدهٔ شما نخورد.") {
            ProtoChoice(
                null,
                listOf(
                    RoutingMode.Global.name to "پروکسی سراسری",
                    RoutingMode.BypassLan.name to "عبور از شبکه محلی",
                    RoutingMode.BypassIran.name to "عبور از ایران",
                    RoutingMode.BypassLanAndIran.name to "شبکه محلی و ایران",
                ),
                settings.routingMode.name,
                { name -> store.update { it.copy(routingMode = RoutingMode.valueOf(name)) } },
            )
            ProtoChoice(
                "راهبرد دامنه",
                DomainStrategy.entries.map { it.name to it.value },
                settings.domainStrategy.name,
                { name -> store.update { it.copy(domainStrategy = DomainStrategy.valueOf(name)) } },
                hint = DOMAIN_STRATEGY_HINT,
            )
            ProtoSwitch(
                "مسدودسازی تبلیغات", settings.blockAds,
                { v -> store.update { it.copy(blockAds = v) } },
                subtitle = "درخواست‌های منطبق با فهرست دامنه‌های تبلیغاتی رد شوند",
                tag = "proto_block_ads",
            )
        }

        ProtoSection(title = "ترتیب بررسی", hint = EVALUATION_ORDER_TEXT) {}

        if (geoMissing) {
            ProtoSection(title = "فایل‌های geo نصب نیستند", border = StatusAmber) {
                Text(
                    "بعضی قاعده‌ها geoip: یا geosite: دارند ولی فایل‌های داده نصب نیست؛ هسته آن قاعده‌ها را نادیده می‌گیرد. " +
                        "از «فایل‌های داده» دریافتشان کنید.",
                    color = StatusAmber, fontSize = 12.sp,
                )
            }
        }

        ProtoSection(title = "قاعده‌ها (${rules.size.fa()})") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProtoButton(
                    "قاعدهٔ جدید", Icons.Rounded.Add,
                    onClick = { editing = RulesetItem(id = RulesetItem.newId()) },
                    modifier = Modifier.weight(1f), tag = "proto_add_rule",
                )
                ProtoOutlined("پیش‌فرض‌ها", onClick = { presetsOpen = true }, tag = "proto_presets")
            }
            if (rules.isEmpty()) {
                ProtoEmpty(
                    "هنوز قاعده‌ای ندارید",
                    "یکی بسازید یا از پیش‌فرض‌ها انتخاب کنید. بدون قاعده، همه‌چیز طبق حالت مسیریابی می‌رود.",
                    actionText = "پیش‌فرض‌ها", onAction = { presetsOpen = true },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("proto_rules")) {
                    rules.forEachIndexed { index, rule ->
                        RuleCard(
                            rule = rule,
                            targets = targets,
                            isFirst = index == 0,
                            isLast = index == rules.lastIndex,
                            onToggle = { on -> mutate { l -> l.map { if (it.id == rule.id) it.copy(enabled = on) else it } } },
                            onEdit = { editing = rule },
                            onDelete = {
                                if (settings.confirmRemove) deleting = rule
                                else mutate { l -> l.filterNot { it.id == rule.id } }
                            },
                            onUp = { mutate { l -> l.moved(l.indexOfFirst { it.id == rule.id }, -1) } },
                            onDown = { mutate { l -> l.moved(l.indexOfFirst { it.id == rule.id }, +1) } },
                        )
                    }
                }
            }
        }

        ProtoSection(title = "ورود و خروج قاعده‌ها", hint = "قاعده‌ها به‌صورت JSON منتقل می‌شوند و برای هر دستگاهی قابل استفاده‌اند.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProtoOutlined("وارد کردن", onClick = { importOpen = true }, modifier = Modifier.weight(1f), tag = "proto_import_rules")
                ProtoOutlined(
                    "کپی", enabled = rules.isNotEmpty(), modifier = Modifier.weight(1f), tag = "proto_export_copy",
                    onClick = {
                        platform.copyToClipboard(RulesetTransfer.encode(rules))
                        platform.toast("قاعده‌ها در کلیپ‌بورد کپی شد")
                    },
                )
                ProtoOutlined(
                    "فایل", enabled = rules.isNotEmpty(), modifier = Modifier.weight(1f), tag = "proto_export_file",
                    onClick = {
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) { RulesetTransfer.encode(rules).encodeToByteArray() }
                            platform.shareFiles(listOf("hellboy-routing-rules.json" to bytes))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: RulesetItem,
    targets: List<Pair<String, String>>,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val accent: Color = when {
        RulesetItem.profileIdOf(rule.outboundTag) != null -> NeonCyan
        rule.outboundTag == RulesetItem.OUTBOUND_DIRECT -> StatusGreen
        rule.outboundTag == RulesetItem.OUTBOUND_BLOCK -> StatusRed
        else -> NeonCyan
    }
    val label = outboundLabel(rule.outboundTag, targets)
    ClubCard(Modifier.fillMaxWidth().testTag("proto_rule_card"), border = if (rule.enabled) accent.copy(alpha = 0.5f) else BorderDark) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (rule.locked) Icon(Icons.Rounded.Lock, "قفل‌شده", tint = TextTertiary, modifier = Modifier.padding(end = 2.dp))
                        Text(
                            rule.remarks.ifBlank { label },
                            color = if (rule.enabled) TextPrimary else TextTertiary,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false),
                        )
                        Chip(label, accent)
                    }
                    Text(ruleSummary(rule), color = TextSecondary, fontSize = 12.sp, maxLines = 2)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("proto_rule_enabled"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                IconButton(onClick = onUp, enabled = !isFirst, modifier = Modifier.testTag("proto_rule_up")) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "بالا بردن", tint = TextSecondary)
                }
                IconButton(onClick = onDown, enabled = !isLast, modifier = Modifier.testTag("proto_rule_down")) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "پایین بردن", tint = TextSecondary)
                }
                IconButton(onClick = onEdit, modifier = Modifier.testTag("proto_rule_edit")) {
                    Icon(Icons.Rounded.Edit, "ویرایش", tint = TextSecondary)
                }
                IconButton(onClick = onDelete, enabled = !rule.locked, modifier = Modifier.testTag("proto_rule_delete")) {
                    Icon(Icons.Rounded.Delete, "حذف", tint = if (rule.locked) TextTertiary else StatusRed)
                }
            }
        }
    }
}

@Composable
private fun ImportRulesDialog(
    existing: List<RulesetItem>,
    onDismiss: () -> Unit,
    onApply: (List<RulesetItem>, ImportMode) -> Unit,
) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ImportMode.Append) }
    var fileError by remember { mutableStateOf<String?>(null) }
    val preview = remember(text, existing) { previewImport(text, existing) }

    ProtoDialog(
        title = "وارد کردن قاعده‌ها",
        onDismiss = onDismiss,
        confirmLabel = "وارد کن",
        confirmEnabled = preview?.valid == true,
        onConfirm = { preview?.takeIf { it.valid }?.let { onApply(it.incoming, mode) } },
    ) {
        ProtoField(
            "متن JSON", text, { text = it; fileError = null },
            placeholder = "[ { \"remarks\": \"...\", \"outboundTag\": \"direct\", \"domain\": [\"geosite:ir\"] } ]",
            singleLine = false, minLines = 4, monospace = true, tag = "proto_import_text",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ProtoOutlined("جای‌گذاری", modifier = Modifier.weight(1f), onClick = {
                val clip = platform.readClipboard()
                if (clip.isNullOrBlank()) platform.toast("کلیپ‌بورد خالی است") else { text = clip; fileError = null }
            })
            ProtoOutlined("از فایل", modifier = Modifier.weight(1f), onClick = {
                scope.launch {
                    val pick = platform.pickFileBytes() ?: return@launch
                    val decoded = withContext(Dispatchers.Default) { runCatching { pick.bytes.decodeToString() }.getOrNull() }
                    if (decoded == null) fileError = "فایل خوانده نشد" else { text = decoded; fileError = null }
                }
            })
        }
        fileError?.let { Text(it, color = StatusRed, fontSize = 11.sp) }
        when {
            preview == null -> Text("متن را جای‌گذاری کنید یا فایل را انتخاب کنید.", color = TextTertiary, fontSize = 11.sp)
            !preview.valid -> Text(
                "این متن مجموعه‌قاعدهٔ معتبری نیست (باید فهرستی از قاعده‌ها به‌صورت JSON باشد).",
                color = StatusRed, fontSize = 12.sp, modifier = Modifier.testTag("proto_import_invalid"),
            )
            else -> {
                Text(
                    "${preview.incoming.size.fa()} قاعدهٔ معتبر پیدا شد" +
                        (if (preview.duplicates > 0) " (${preview.duplicates.fa()} مورد تکراری)" else ""),
                    color = StatusGreen, fontSize = 12.sp, modifier = Modifier.testTag("proto_import_ok"),
                )
                ProtoChoice(
                    "روش",
                    listOf(ImportMode.Append.name to "افزودن به فهرست", ImportMode.Replace.name to "جایگزینی فهرست"),
                    mode.name,
                    { mode = ImportMode.valueOf(it) },
                    hint = if (mode == ImportMode.Replace)
                        "قاعده‌های فعلی حذف می‌شوند" + (if (preview.keptLocked > 0) " (${preview.keptLocked.fa()} قاعدهٔ قفل‌شده می‌ماند)" else "") + "."
                    else "قاعده‌های تکراری اضافه نمی‌شوند.",
                )
            }
        }
    }
}
