package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.domain.config.RoutingMigration
import dev.valid7996.narcicgeteway.domain.model.AppSettings
import dev.valid7996.narcicgeteway.domain.model.RulesetItem

/** What the rule editor shows: every list is one comma/newline separated text, exactly as a person types it. */
data class RuleDraft(
    val remarks: String = "",
    val outbound: String = RulesetItem.OUTBOUND_PROXY,
    val domain: String = "",
    val ip: String = "",
    val port: String = "",
    val network: String = "",
    val protocol: String = "",
    val locked: Boolean = false,
)

/** Splits "a, b\nc" into trimmed, non-empty entries. */
fun splitEntries(text: String): List<String> =
    text.split(',', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }

fun joinEntries(list: List<String>): String = list.joinToString(", ")

fun RulesetItem.toDraft(): RuleDraft = RuleDraft(
    remarks = remarks,
    outbound = outboundTag,
    domain = joinEntries(domain),
    ip = joinEntries(ip),
    port = port,
    network = network,
    protocol = joinEntries(protocol),
    locked = locked,
)

/** Applies the draft on top of [base] (keeps id and enabled flag). */
fun RuleDraft.applyTo(base: RulesetItem): RulesetItem = base.copy(
    remarks = remarks.trim(),
    outboundTag = outbound,
    domain = splitEntries(domain),
    ip = splitEntries(ip),
    port = port.trim(),
    network = network,
    protocol = splitEntries(protocol).map { it.lowercase() },
    locked = locked,
)

/** True when two rules would match exactly the same traffic and send it to the same place. */
fun RulesetItem.sameMatcher(other: RulesetItem): Boolean =
    outboundTag == other.outboundTag &&
        domain == other.domain &&
        ip == other.ip &&
        port == other.port &&
        network == other.network &&
        protocol == other.protocol

/** Moves the element at [from] by one step (-1 up, +1 down); returns the same list when impossible. */
fun <T> List<T>.moved(from: Int, delta: Int): List<T> {
    val to = from + delta
    if (from !in indices || to !in indices) return this
    val out = toMutableList()
    val item = out.removeAt(from)
    out.add(to, item)
    return out
}

/** Short Persian name of where a rule sends traffic. [targets] are (profileId, name) pairs. */
fun outboundLabel(tag: String, targets: List<Pair<String, String>> = emptyList()): String {
    RulesetItem.profileIdOf(tag)?.let { id ->
        return targets.firstOrNull { it.first == id }?.second ?: "پروکسی (سرور مقصد موجود نیست)"
    }
    return when (tag) {
        RulesetItem.OUTBOUND_DIRECT -> "مستقیم"
        RulesetItem.OUTBOUND_BLOCK -> "مسدود"
        else -> "پروکسی"
    }
}

/** One line describing what a rule matches (first entries only). */
fun ruleSummary(rule: RulesetItem): String = buildList {
    rule.domain.take(2).forEach { add(it) }
    if (rule.domain.size > 2) add("+${rule.domain.size - 2}")
    rule.ip.take(2).forEach { add(it) }
    if (rule.ip.size > 2) add("+${rule.ip.size - 2}")
    if (rule.port.isNotBlank()) add("port:${rule.port}")
    if (rule.network.isNotBlank()) add(rule.network)
    rule.protocol.forEach { add(it) }
}.joinToString("، ").ifBlank { "بدون شرط" }

/** True when some enabled rule needs geoip.dat / geosite.dat. */
fun usesGeoData(rules: List<RulesetItem>): Boolean = rules.any { r ->
    r.enabled && (r.domain.any { dev.valid7996.narcicgeteway.domain.config.RuleValidation.isGeoDomain(it) } ||
        r.ip.any { dev.valid7996.narcicgeteway.domain.config.RuleValidation.isGeoIp(it) })
}

/** True when the old free-text rule boxes still hold rules that have not been converted yet. */
fun hasLegacyRules(s: AppSettings): Boolean =
    !s.routingMigrated && s.rulesets.isEmpty() &&
        (s.customProxyRules.isNotBlank() || s.customDirectRules.isNotBlank() || s.customBlockRules.isNotBlank())

/** The settings with [next] stored as the rule list and the legacy text boxes emptied (the reference does the same). */
fun AppSettings.withRules(next: List<RulesetItem>): AppSettings = copy(
    rulesets = next,
    routingMigrated = true,
    customProxyRules = "",
    customDirectRules = "",
    customBlockRules = "",
)

/** Rules to show: migrated list, or the legacy boxes converted on the fly. */
fun effectiveRules(s: AppSettings): List<RulesetItem> = RoutingMigration.effectiveRulesets(s)
