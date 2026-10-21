package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.MhrvRelayProfile

/** The Apps Script relay (MHRV) form. Deployment IDs are a list: the core rotates to the next one when a quota runs out. */
data class MhrvForm(
    val name: String = "",
    val mode: String = MhrvRelayProfile.MODE_APPS_SCRIPT,
    val scriptIds: List<String> = emptyList(),
    val authKey: String = "",
    val googleIp: String = MhrvRelayProfile.DEFAULT_GOOGLE_IP,
    val frontDomain: String = MhrvRelayProfile.DEFAULT_FRONT_DOMAIN,
    /** The enabled SNI names, one per line; empty means the core expands the pool itself. */
    val sniHosts: String = "",
    val verifySsl: Boolean = true,
    val parallel: Int = 1,
    val blockQuic: Boolean = true,
    val blockStun: Boolean = true,
    val blockDoh: Boolean = true,
    val tunnelDoh: Boolean = true,
    val coalesceStep: Int = 10,
    val coalesceMax: Int = 1000,
    val youtubeViaRelay: Boolean = false,
    val upstreamSocks5: String = "",
    val keptLogLevel: String = "info",
) {
    fun toProfile(): MhrvRelayProfile = MhrvRelayProfile(
        mode = mode,
        scriptIds = scriptIds.joinToString("\n"),
        authKey = authKey.trim(),
        googleIp = googleIp.trim(),
        frontDomain = frontDomain.trim(),
        sniHosts = sniHosts.trim(),
        verifySsl = verifySsl,
        parallelRelay = parallel.coerceIn(1, 8),
        blockQuic = blockQuic,
        blockStun = blockStun,
        blockDoh = blockDoh,
        tunnelDoh = tunnelDoh,
        coalesceStepMs = coalesceStep,
        coalesceMaxMs = coalesceMax,
        youtubeViaRelay = youtubeViaRelay,
        upstreamSocks5 = upstreamSocks5.trim(),
        logLevel = keptLogLevel,
    )

    /** The profile's own rule (`isValid`) with a message for the part that is missing. */
    fun check(): FormCheck = formCheck {
        val p = toProfile()
        if (p.needsCredentials) {
            if (p.scriptIdList().isEmpty()) error(F_IDS, "دست‌کم یک شناسهٔ Deployment وارد کنید.")
            if (p.authKey.isBlank()) error(F_AUTH, "کلید احراز هویت را وارد کنید.")
        }
        if (p.mode !in MhrvRelayProfile.MODES) error(F_MODE, "حالت نامعتبر است.")
        if (upstreamSocks5.isNotBlank() && !FormText.isHostPort(upstreamSocks5)) warn(F_UPSTREAM, "قالب درست host:port است؛ مثل 127.0.0.1:10808.")
    }

    /** Copies everything a pasted config carries, keeping the current name, auth key (when the paste has none) and log level. */
    fun withPasted(parsed: MhrvRelayProfile): MhrvForm = copy(
        mode = parsed.mode,
        scriptIds = parsed.scriptIdList(),
        authKey = parsed.authKey.ifBlank { authKey },
        googleIp = parsed.googleIp,
        frontDomain = parsed.frontDomain,
        sniHosts = parsed.sniHosts,
        verifySsl = parsed.verifySsl,
        parallel = parsed.parallelRelay.coerceIn(1, 8),
        blockQuic = parsed.blockQuic,
        blockStun = parsed.blockStun,
        blockDoh = parsed.blockDoh,
        tunnelDoh = parsed.tunnelDoh,
        coalesceStep = parsed.coalesceStepMs,
        coalesceMax = parsed.coalesceMaxMs,
        youtubeViaRelay = parsed.youtubeViaRelay,
        upstreamSocks5 = parsed.upstreamSocks5,
    )

    companion object {
        const val F_IDS = "ids"
        const val F_AUTH = "auth"
        const val F_MODE = "mode"
        const val F_UPSTREAM = "upstream"

        val MODES = listOf(
            MhrvRelayProfile.MODE_APPS_SCRIPT to "Apps Script (نیاز به گواهی CA)",
            MhrvRelayProfile.MODE_FULL to "تونل کامل (بدون CA)",
            MhrvRelayProfile.MODE_DIRECT to "اتصال مستقیم به گوگل",
        )

        fun from(p: MhrvRelayProfile?, name: String = ""): MhrvForm =
            if (p == null) MhrvForm(name = name) else MhrvForm(
                name = name,
                mode = p.mode,
                scriptIds = p.scriptIdList(),
                authKey = p.authKey,
                googleIp = p.googleIp,
                frontDomain = p.frontDomain,
                sniHosts = p.sniHosts,
                verifySsl = p.verifySsl,
                parallel = p.parallelRelay.coerceIn(1, 8),
                blockQuic = p.blockQuic,
                blockStun = p.blockStun,
                blockDoh = p.blockDoh,
                tunnelDoh = p.tunnelDoh,
                coalesceStep = p.coalesceStepMs,
                coalesceMax = p.coalesceMaxMs,
                youtubeViaRelay = p.youtubeViaRelay,
                upstreamSocks5 = p.upstreamSocks5,
                keptLogLevel = p.logLevel,
            )
    }
}

/** List editing and SNI pool maths for the relay editor, kept pure so they can be tested. */
object MhrvLists {
    /** Splits pasted text into IDs, turning full `.../macros/s/<ID>/exec` URLs into bare IDs, without duplicates. */
    fun parseIds(input: String): List<String> =
        input.split(',', '\n', ' ', ';', '\t').map { MhrvRelayProfile.extractId(it) }.filter { it.isNotEmpty() }.distinct()

    fun addIds(current: List<String>, input: String): List<String> = (current + parseIds(input)).distinct()

    fun removeAt(current: List<String>, index: Int): List<String> = current.filterIndexed { i, _ -> i != index }

    /** Replaces item [index] with [edited]; pasting several IDs into one row splits them into rows. */
    fun editAt(current: List<String>, index: Int, edited: String): List<String> {
        if (index !in current.indices) return current
        val parts = edited.split(',', '\n', ' ', ';', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        val next = current.toMutableList()
        if (parts.size > 1) {
            next.removeAt(index)
            next.addAll(index, parts.map { MhrvRelayProfile.extractId(it) }.filter { it.isNotEmpty() })
        } else {
            next[index] = edited.trim()
        }
        return next.distinct()
    }

    /** The enabled names of a stored pool; an empty pool means "the defaults". */
    fun activePool(sniHosts: String): List<String> {
        val list = sniHosts.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { MhrvRelayProfile.DEFAULT_SNI_POOL }
    }

    /** Every name shown in the pool list: the defaults plus whatever else is enabled or was added by hand. */
    fun shownPool(sniHosts: String, added: List<String> = emptyList()): List<String> =
        (MhrvRelayProfile.DEFAULT_SNI_POOL + activePool(sniHosts) + added).distinct()

    fun setEnabled(sniHosts: String, name: String, enabled: Boolean): String {
        val active = activePool(sniHosts)
        val next = if (enabled) (active + name).distinct() else active.filter { it != name }
        return next.joinToString("\n")
    }

    fun addCustom(sniHosts: String, input: String): String {
        val tokens = input.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
        return (activePool(sniHosts) + tokens).distinct().joinToString("\n")
    }
}

/** What the native SNI probe answers, read from its JSON line. */
sealed interface SniProbe {
    data object Idle : SniProbe
    data object Running : SniProbe
    data class Ok(val ms: Long) : SniProbe
    data class Failed(val reason: String) : SniProbe

    companion object {
        private val latency = Regex(""""latencyMs"\s*:\s*(\d+)""")
        private val error = Regex(""""error"\s*:\s*"([^"]*)"""")

        fun parse(raw: String): SniProbe {
            if (raw.isBlank()) return Failed("پاسخی نیامد")
            return if (raw.replace(" ", "").contains("\"ok\":true")) {
                Ok(latency.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L)
            } else {
                Failed(error.find(raw)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "خطا در اتصال")
            }
        }
    }
}
