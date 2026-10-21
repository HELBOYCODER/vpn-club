package com.helboy.vpnclub.ui.proto.profiles

import com.helboy.vpnclub.ui.proto.ProtoKind
import dev.valid7996.narcicgeteway.domain.config.Subscription
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import java.util.Locale

/*
 * Pure helpers behind the "Protocols" profile list: filtering, grouping, ordering and the small bits of text a card
 * shows. Nothing here touches Compose or the repository, so all of it is covered by plain JUnit tests.
 */

/** The "all profiles" chip. A subscription id can never contain a NUL, so this cannot clash with one. */
const val GROUP_ALL: String = "\u0000all"

/** Profiles that belong to no subscription or group (their `subscriptionId` is empty). */
const val GROUP_MANUAL: String = ""

/** One chip above the list. */
data class GroupChip(val id: String, val label: String, val count: Int)

/** How a measured delay is coloured. */
enum class LatencyBucket { Unknown, Failed, Fast, Medium, Slow }

/** Delay in ms (`null` = never measured, zero or negative = the test failed). */
fun latencyBucket(ms: Int?): LatencyBucket = when {
    ms == null -> LatencyBucket.Unknown
    ms <= 0 -> LatencyBucket.Failed
    ms < 300 -> LatencyBucket.Fast
    ms < 800 -> LatencyBucket.Medium
    else -> LatencyBucket.Slow
}

/** The chips above the list: "همه" (when [showAll]), the manual group (when there are groups) and every subscription. */
fun groupChips(profiles: List<VpnProfile>, subscriptions: List<Subscription>, showAll: Boolean): List<GroupChip> {
    val chips = ArrayList<GroupChip>()
    if (showAll) chips += GroupChip(GROUP_ALL, "همه", profiles.size)
    if (subscriptions.isNotEmpty() || !showAll) {
        chips += GroupChip(GROUP_MANUAL, "دستی", profiles.count { it.subscriptionId.isEmpty() })
    }
    subscriptions.forEach { sub ->
        chips += GroupChip(sub.id, sub.name.ifBlank { sub.url.take(24) }, profiles.count { it.subscriptionId == sub.id })
    }
    return chips
}

/** The group to show when the stored choice is no longer offered (a subscription was deleted, "همه" was turned off). */
fun effectiveGroup(selected: String, chips: List<GroupChip>): String =
    if (chips.any { it.id == selected }) selected else chips.firstOrNull()?.id ?: GROUP_ALL

/** Profiles of one group (or all of them). */
fun inGroup(profiles: List<VpnProfile>, group: String): List<VpnProfile> = when (group) {
    GROUP_ALL -> profiles
    else -> profiles.filter { it.subscriptionId == group }
}

/** Case-insensitive match on name, address, protocol and transport; a blank query matches everything. */
fun matchesQuery(profile: VpnProfile, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return profile.name.contains(q, ignoreCase = true) ||
        profile.address.contains(q, ignoreCase = true) ||
        profile.protocol.contains(q, ignoreCase = true) ||
        profile.transportLabel.contains(q, ignoreCase = true)
}

/** What the list shows: the chosen group, narrowed by the search text. Keeps the stored order. */
fun filterProfiles(profiles: List<VpnProfile>, query: String, group: String): List<VpnProfile> =
    inGroup(profiles, group).filter { matchesQuery(it, query) }

/** Fastest first; failed and untested ones last; ties keep their order. */
fun sortedByLatency(profiles: List<VpnProfile>): List<VpnProfile> =
    profiles.sortedBy { it.lastPingMs?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE }

/** [ids] with [id] moved by [delta] places (clamped to the ends); unchanged when [id] is not in the list. */
fun moveWithin(ids: List<String>, id: String, delta: Int): List<String> {
    val from = ids.indexOf(id)
    if (from < 0 || delta == 0) return ids
    val to = (from + delta).coerceIn(0, ids.size - 1)
    if (to == from) return ids
    val out = ids.toMutableList()
    out.removeAt(from)
    out.add(to, id)
    return out
}

/** Regional-indicator flag for a two letter country code, or null when it is not one. */
fun flagEmoji(code: String?): String? {
    val cc = code?.trim()?.uppercase(Locale.ROOT) ?: return null
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return null
    val a = 0x1F1E6 + (cc[0] - 'A')
    val b = 0x1F1E6 + (cc[1] - 'A')
    return String(Character.toChars(a)) + String(Character.toChars(b))
}

/** The country code written as a flag emoji inside [text] (profile names often start with one). */
fun countryFromName(text: String): String? {
    var i = 0
    while (i <= text.length - 4) {
        if (text[i] == '\uD83C' && text[i + 1] in '\uDDE6'..'\uDDFF' &&
            text[i + 2] == '\uD83C' && text[i + 3] in '\uDDE6'..'\uDDFF'
        ) {
            val c1 = ('A'.code + (text[i + 1].code - 0xDDE6)).toChar()
            val c2 = ('A'.code + (text[i + 3].code - 0xDDE6)).toChar()
            return "$c1$c2"
        }
        i++
    }
    return null
}

/** The name without a flag emoji and without doubled spaces; the original when nothing else is left. */
fun cleanName(name: String): String {
    val sb = StringBuilder(name.length)
    var i = 0
    while (i < name.length) {
        if (i <= name.length - 4 &&
            name[i] == '\uD83C' && name[i + 1] in '\uDDE6'..'\uDDFF' &&
            name[i + 2] == '\uD83C' && name[i + 3] in '\uDDE6'..'\uDDFF'
        ) {
            i += 4
        } else {
            sb.append(name[i])
            i++
        }
    }
    return sb.toString().replace(Regex("""\s+"""), " ").trim().ifBlank { name }
}

/** The flag of a profile: the stored country first, then a flag inside its name. */
fun profileFlag(profile: VpnProfile): String? =
    flagEmoji(profile.countryCode) ?: flagEmoji(countryFromName(profile.name))

/** Two or three letters for the badge shown when no flag is known. */
fun protocolBadge(profile: VpnProfile): String {
    val p = profile.protocol.trim().ifEmpty { "?" }
    return p.take(3).uppercase(Locale.ROOT)
}

/** "VLESS", or "VLESS · WS" when the transport adds something. */
fun protocolLabel(profile: VpnProfile): String {
    val p = profile.protocol.trim().uppercase(Locale.ROOT)
    val t = profile.transportLabel.trim()
    return if (t.isEmpty() || t.equals(p, ignoreCase = true) || t == "-") p else "$p · ${t.uppercase(Locale.ROOT)}"
}

/** "host:port"; just the host when there is no port; a dash for profiles with no server of their own. */
fun addressLabel(profile: VpnProfile): String {
    val host = profile.address.trim()
    if (host.isEmpty() || host == "-") return "—"
    return if (profile.port > 0) "$host:${profile.port}" else host
}

/** Persian remaining time of a lock, null when it never expires. [now] and [expiresAt] are epoch milliseconds. */
fun expiryLabel(expiresAt: Long?, now: Long): String? {
    if (expiresAt == null) return null
    val left = expiresAt - now
    if (left <= 0) return "منقضی شده"
    val minutes = left / 60_000L
    val text = when {
        minutes < 1 -> "کمتر از یک دقیقه"
        minutes < 60 -> "$minutes دقیقه"
        minutes < 48 * 60 -> "${minutes / 60} ساعت"
        else -> "${minutes / (24 * 60)} روز"
    }
    return "$text دیگر"
}

/** The ids a bulk action works on: the selection if there is one, otherwise everything shown. */
fun bulkTargets(shown: List<VpnProfile>, selected: Set<String>): List<VpnProfile> =
    if (selected.isEmpty()) shown else shown.filter { it.id in selected }

/** One way to add a profile from the "افزودن" dialog that opens an editor. */
data class AddKindEntry(val kind: ProtoKind, val title: String, val hint: String)

/** One entry per [ProtoKind], in the order the dialog lists them. */
val ADD_KIND_ENTRIES: List<AddKindEntry> = listOf(
    AddKindEntry(ProtoKind.Manual, "کانفیگ دستی", "ساخت کانفیگ VLESS، VMess، Trojan، Shadowsocks، WireGuard و … با پر کردن فیلدها"),
    AddKindEntry(ProtoKind.Psiphon, "سایفون", "اتصال از راه شبکه‌ی Psiphon، بدون نیاز به سرور شخصی"),
    AddKindEntry(ProtoKind.Aether, "اتر / WARP", "تونل WireGuard با کلید و نقطه‌ی پایانی WARP یا اتر"),
    AddKindEntry(ProtoKind.SniSpoof, "جعل SNI", "عبور از فیلتر با SNI ساختگی روی IP تمیز"),
    AddKindEntry(ProtoKind.Ikev2, "IKEv2", "اتصال IKEv2/IPsec با کاربر و رمز یا گواهی"),
    AddKindEntry(ProtoKind.OpenConnect, "اوپن‌کانکت", "اتصال به سرور OpenConnect یا AnyConnect"),
    AddKindEntry(ProtoKind.Ssh, "SSH", "تونل SOCKS روی یک سرور SSH با رمز یا کلید"),
    AddKindEntry(ProtoKind.DnsTunnel, "تونل DNS", "عبور ترافیک از راه پرس‌وجوهای DNS (dnstt و مشابه)"),
    AddKindEntry(ProtoKind.MasterDns, "مستر DNS", "تونل DNS با چند ریزالور و پاسخ‌گیری سریع‌تر"),
    AddKindEntry(ProtoKind.Tor, "تور", "اتصال از شبکه‌ی Tor؛ کند ولی ناشناس"),
    AddKindEntry(ProtoKind.Mhrv, "رله‌ی Apps-Script (MHRV)", "رله از راه Google Apps Script و دامنه‌ی جلویی گوگل"),
    AddKindEntry(ProtoKind.ProxyChain, "زنجیره‌ی پروکسی", "ترافیک از چند پروفایل پشت سر هم عبور می‌کند"),
    AddKindEntry(ProtoKind.CrossChain, "زنجیره‌ی متقاطع", "یک پروفایل را درون تونل پروفایل دیگر اجرا می‌کند"),
    AddKindEntry(ProtoKind.AutoSelect, "گروه انتخاب خودکار", "سریع‌ترین پروفایل یک گروه را خودکار برمی‌گزیند"),
)
