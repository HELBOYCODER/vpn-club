package com.helboy.vpnclub.ui.proto.editors2

import dev.valid7996.narcicgeteway.data.config.CrossChainUdpUnsupportedException
import com.helboy.vpnclub.ui.fa
import dev.valid7996.narcicgeteway.domain.config.VpnProfile

/*
 * Pure rules of the two composite editors: which profiles may be a member, how members are ordered, and what is
 * wrong with a choice. Nothing here touches Compose or the repository, so plain JUnit covers it.
 */

/** Rules of the proxy-chain editor (an ordered list of two or more profiles). */
object ChainLogic {

    const val MIN_MEMBERS = 2

    const val DESCRIPTION =
        "ترافیک را به‌ترتیب از چند سرور عبور بده. ترافیک وارد اولین گره می‌شود و از آخرین گره (خروجی) بیرون می‌رود."
    const val MIN_TEXT = "حداقل دو سرور انتخاب کنید."
    const val NO_CANDIDATES_TEXT = "سرور Xray برای زنجیره‌کردن موجود نیست. ابتدا چند سرور اضافه کنید."

    /**
     * The profiles a chain may be built from: the ones that have a link or a JSON of their own (a custom JSON only
     * when it is serverless), that are not a managed tunnel, another chain or a whole sing-box config, and are not locked.
     */
    fun chainCandidates(profiles: List<VpnProfile>): List<VpnProfile> = profiles.filter {
        !it.isLocked && it.rawPayload() != null && (!it.isCustom || it.isServerless) &&
            !it.isManagedTunnel && !it.isProxyChain && !it.isSingBoxConfig
    }

    /** [list] narrowed to the profiles whose name or address contains [query] (blank query keeps all). */
    fun filterByQuery(list: List<VpnProfile>, query: String): List<VpnProfile> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { it.name.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true) }
    }

    /** The members of a stored chain that are still usable, in their stored order. */
    fun keptMembers(initial: List<String>, byId: Map<String, VpnProfile>): List<String> =
        initial.filter { byId.containsKey(it) }

    /** How many stored members are gone (deleted, locked, or no longer allowed in a chain). */
    fun droppedCount(initial: List<String>, byId: Map<String, VpnProfile>): Int =
        initial.count { !byId.containsKey(it) }

    /**
     * [selected] with [picked] added at the end. A serverless profile (a config that only has a freedom outbound)
     * can only be the first hop, and only one of them fits: it goes to the front and replaces the other.
     */
    fun addMember(selected: List<String>, picked: VpnProfile, byId: Map<String, VpnProfile>): List<String> {
        if (picked.id in selected) return selected
        return if (picked.isServerless) {
            listOf(picked.id) + selected.filterNot { byId[it]?.isServerless == true }
        } else {
            selected + picked.id
        }
    }

    /** [selected] with the member at [from] moved to [to]; out-of-range moves leave the list as it was. */
    fun move(selected: List<String>, from: Int, to: Int): List<String> {
        if (from !in selected.indices || to !in selected.indices || from == to) return selected
        val out = selected.toMutableList()
        val item = out.removeAt(from)
        out.add(to, item)
        return out
    }

    fun remove(selected: List<String>, index: Int): List<String> =
        if (index in selected.indices) selected.filterIndexed { i, _ -> i != index } else selected

    /**
     * The first member that needs UDP hopping (WireGuard, Hysteria) but is placed after one that cannot carry UDP:
     * (its name, the previous member's name); null when the order works.
     */
    fun udpHopWarning(selected: List<String>, byId: Map<String, VpnProfile>): Pair<String, String>? {
        for (i in 1 until selected.size) {
            val current = byId[selected[i]] ?: continue
            val previous = byId[selected[i - 1]] ?: continue
            if (current.needsUdpHop && !previous.carriesUdpHop) return current.name to previous.name
        }
        return null
    }

    fun udpHopText(warning: Pair<String, String>): String =
        "«${warning.first}» به UDP نیاز دارد ولی «${warning.second}» که قبل از آن است نمی‌تواند UDP را عبور دهد. " +
            "آن را اول بگذارید یا گره دیگری انتخاب کنید."

    fun droppedText(count: Int): String = "${count.fa()} عضو این زنجیره دیگر وجود ندارد و از فهرست حذف شد"

    data class Check(val count: Int, val udpWarning: Pair<String, String>?) {
        val tooFew: Boolean get() = count < MIN_MEMBERS
        val canSave: Boolean get() = !tooFew && udpWarning == null
    }

    fun check(selected: List<String>, byId: Map<String, VpnProfile>): Check =
        Check(selected.size, udpHopWarning(selected, byId))

    /** A suggested name for a chain, shown as the placeholder: "first → … → last". */
    fun suggestedName(selected: List<String>, byId: Map<String, VpnProfile>): String =
        selected.mapNotNull { byId[it]?.name }.joinToString(" ← ").ifBlank { "زنجیرهٔ پروکسی" }
}

/** Rules of the cross-chain editor (an inner profile dialled through an outer carrier). */
object CrossChainLogic {

    const val DESCRIPTION =
        "دو موتور تونل متفاوت را روی هم بگذارید. حامل اول وصل می‌شود و همان چیزی است که شبکه می‌بیند؛ " +
            "سپس موتور خروجی از داخل حامل به سرور خودش می‌رسد."
    const val CARRIER_HINT = "اول اجرا می‌شود. ترافیک آن چیزی است که ناظر شبکه می‌بیند."
    const val EXIT_HINT = "از داخل حامل به سرور خودش وصل می‌شود. ترافیک شما از سرور آن بیرون می‌آید."
    const val NO_CARRIERS = "هیچ پروفایلی اینجا نمی‌تواند حامل باشد. فقط سرورهای Xray و سایفون و تور می‌توانند حامل باشند."
    const val NO_EXITS =
        "هیچ پروفایلی اینجا نمی‌تواند از داخل تونل دیگری وصل شود. فقط سرورهای Xray و سایفون و تور می‌توانند خروجی باشند."
    const val SAME_PROFILE = "دو پروفایل متفاوت انتخاب کنید"
    const val PSIPHON_NOTE =
        "وقتی سایفون از داخل تونل دیگری اجرا شود پروتکل‌های QUIC آن کنار گذاشته می‌شوند، چون UDP از پروکسی TCP رد نمی‌شود."
    const val TOR_NOTE =
        "بعضی از ترنسپورت‌های پل تور پروکسی بالادست را نمی‌پذیرند. اگر تور بالا نیامد، پل‌ها را خاموش کنید یا obfs4 یا snowflake را انتخاب کنید."
    const val FRAGMENT_NOTE =
        "در حالت زنجیره، تکه‌تکه‌سازی TLS انجام نمی‌شود: شبکه محلی فقط تونل خودِ حامل را می‌بیند، نه این دست‌دهی را."

    /** Profiles that may be the outer carrier (the tunnel the network sees). */
    fun carriers(profiles: List<VpnProfile>): List<VpnProfile> =
        profiles.filter { !it.isLocked && it.canCarryChain && !it.isCrossChain }

    /** Profiles that may be the inner exit (dialled from inside the carrier). */
    fun exits(profiles: List<VpnProfile>): List<VpnProfile> =
        profiles.filter { !it.isLocked && it.canDialThroughProxy && !it.isCrossChain }

    fun udpText(protocol: String, carrier: String): String =
        "$protocol به UDP نیاز دارد ولی $carrier فقط TCP را عبور می‌دهد. " +
            "یک حامل با پشتیبانی UDP انتخاب کنید یا خروجی‌ای که به UDP نیاز ندارد."

    fun summary(inner: VpnProfile, outer: VpnProfile): String =
        "مسیر ترافیک: دستگاه ← ${outer.name} ← ${inner.name} ← اینترنت"

    data class Check(
        val inner: VpnProfile?,
        val outer: VpnProfile?,
        val same: Boolean,
        val udp: String?,
        /** True when a member was picked before but is no longer in the list (deleted or locked since). */
        val innerMissing: Boolean,
        val outerMissing: Boolean,
    ) {
        val valid: Boolean get() = inner != null && outer != null && !same && udp == null
    }

    /**
     * Checks a choice. [udpMismatch] is the repository's own test (ConfigRepository.udpMismatch), so the rule is the
     * engine's and not a copy of it.
     */
    fun check(
        innerId: String,
        outerId: String,
        exits: List<VpnProfile>,
        carriers: List<VpnProfile>,
        udpMismatch: (VpnProfile, VpnProfile) -> CrossChainUdpUnsupportedException?,
    ): Check {
        val inner = exits.firstOrNull { it.id == innerId }
        val outer = carriers.firstOrNull { it.id == outerId }
        val same = innerId.isNotEmpty() && innerId == outerId
        val udp = if (inner != null && outer != null && !same) {
            udpMismatch(inner, outer)?.let { udpText(it.protocol, it.carrier) }
        } else null
        return Check(
            inner = inner,
            outer = outer,
            same = same,
            udp = udp,
            innerMissing = innerId.isNotEmpty() && inner == null,
            outerMissing = outerId.isNotEmpty() && outer == null,
        )
    }

    /** The notes that explain what a chosen exit means for the chain. */
    fun notes(inner: VpnProfile?): List<String> = buildList {
        if (inner?.isPsiphon == true) add(PSIPHON_NOTE)
        if (inner?.isTor == true) add(TOR_NOTE)
        if (inner != null && !inner.isManagedTunnel) add(FRAGMENT_NOTE)
    }
}
