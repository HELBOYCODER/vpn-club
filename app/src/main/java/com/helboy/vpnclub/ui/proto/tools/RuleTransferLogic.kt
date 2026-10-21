package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.domain.config.RulesetTransfer
import dev.valid7996.narcicgeteway.domain.model.RoutingPresetType
import dev.valid7996.narcicgeteway.domain.model.RoutingPresets
import dev.valid7996.narcicgeteway.domain.model.RulesetItem

enum class ImportMode { Append, Replace }

/** What an import would do, so the dialog can show it before anything changes. */
data class ImportPreview(
    val valid: Boolean,
    val incoming: List<RulesetItem>,
    val duplicates: Int,
    val keptLocked: Int,
) {
    val fresh: Int get() = incoming.size - duplicates
}

/** Parses [text] with the engine's [RulesetTransfer] and compares it with [existing]. A blank text is "nothing yet", not an error. */
fun previewImport(text: String, existing: List<RulesetItem>): ImportPreview? {
    if (text.isBlank()) return null
    val parsed = RulesetTransfer.decode(text)
    if (parsed.isNullOrEmpty()) return ImportPreview(valid = false, incoming = emptyList(), duplicates = 0, keptLocked = 0)
    return ImportPreview(
        valid = true,
        incoming = parsed,
        duplicates = parsed.count { c -> existing.any { it.sameMatcher(c) } },
        keptLocked = existing.count { it.locked },
    )
}

/** The list after importing [incoming] into [existing]. Replace keeps locked rules (as presets do). */
fun applyImport(existing: List<RulesetItem>, incoming: List<RulesetItem>, mode: ImportMode): List<RulesetItem> = when (mode) {
    ImportMode.Append -> existing + incoming.filterNot { c -> existing.any { it.sameMatcher(c) } }
    ImportMode.Replace -> RoutingPresets.apply(existing, incoming)
}

/** Adds only the preset rules that are not already present. Returns the new list. */
fun addPreset(existing: List<RulesetItem>, preset: List<RulesetItem>): List<RulesetItem> =
    existing + preset.filterNot { c -> existing.any { it.sameMatcher(c) } }

fun presetFreshCount(existing: List<RulesetItem>, preset: List<RulesetItem>): Int =
    preset.count { c -> existing.none { it.sameMatcher(c) } }

/** Persian names of the add-on presets (the engine labels are English). */
fun presetLabel(engineLabel: String): String = when (engineLabel) {
    "Block ads" -> "مسدودسازی تبلیغات"
    "Bypass LAN" -> "عبور مستقیم شبکه محلی"
    "Bypass private (no geo assets)" -> "عبور مستقیم آدرس‌های خصوصی (بدون فایل geo)"
    "Bypass Iran" -> "عبور مستقیم ایران"
    "Bypass China" -> "عبور مستقیم چین"
    "Block BitTorrent" -> "مسدودسازی بیت‌تورنت"
    else -> engineLabel
}

fun presetTypeLabel(type: RoutingPresetType): String = when (type) {
    RoutingPresetType.IranWhitelist -> "لیست سفید ایران"
    RoutingPresetType.ChinaWhitelist -> "لیست سفید چین"
    RoutingPresetType.ChinaBlacklist -> "لیست سیاه چین"
    RoutingPresetType.Global -> "پروکسی سراسری"
    RoutingPresetType.RussiaWhitelist -> "لیست سفید روسیه"
}

/** Text shown under the rules explaining how they are evaluated. */
const val EVALUATION_ORDER_TEXT =
    "قاعده‌ها از بالا به پایین بررسی می‌شوند و اولین تطابق برنده است؛ قاعده‌های بعدی دیگر دیده نمی‌شوند. " +
        "قاعده‌ای که خاموش است نادیده گرفته می‌شود. ترافیکی که با هیچ قاعده‌ای نخورد، از حالت مسیریابی بالای صفحه پیروی می‌کند. " +
        "برای تغییر اولویت، قاعده را با فلش‌ها بالا یا پایین ببرید."

const val DOMAIN_STRATEGY_HINT =
    "AsIs دامنه را همان‌طور که هست تطبیق می‌دهد. IPIfNonMatch وقتی هیچ قاعدهٔ دامنه‌ای نخورد، دامنه را resolve می‌کند و دوباره روی IP امتحان می‌کند. " +
        "IPOnDemand همیشه برای قاعده‌های IP، دامنه را resolve می‌کند (کندتر ولی دقیق‌تر)."
