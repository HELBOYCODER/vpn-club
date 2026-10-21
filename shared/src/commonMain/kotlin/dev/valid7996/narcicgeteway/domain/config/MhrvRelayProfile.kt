@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.valid7996.narcicgeteway.domain.config

import dev.valid7996.narcicgeteway.platform.inflateZlib
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Settings of the MasterHttpRelayVPN (mhrv-rs) core: a relay through the user's own Google Apps Script
 * deployment, fronted on a Google edge IP. The core exposes a local SOCKS5 listener that Narcic bridges to the TUN.
 */
@Serializable
data class MhrvRelayProfile(
    /** [MODE_APPS_SCRIPT], [MODE_FULL] or [MODE_DIRECT]. */
    val mode: String = MODE_APPS_SCRIPT,
    /** One deployment ID or full `.../macros/s/<ID>/exec` URL per line (or comma separated). */
    val scriptIds: String = "",
    val authKey: String = "",
    val googleIp: String = DEFAULT_GOOGLE_IP,
    val frontDomain: String = DEFAULT_FRONT_DOMAIN,
    /** Optional SNI rotation pool, one per line; empty lets the core expand it from [frontDomain]. */
    val sniHosts: String = "",
    val verifySsl: Boolean = true,
    val parallelRelay: Int = 1,
    val blockQuic: Boolean = true,
    val blockStun: Boolean = true,
    val blockDoh: Boolean = true,
    val tunnelDoh: Boolean = true,
    val coalesceStepMs: Int = 10,
    val coalesceMaxMs: Int = 1000,
    val youtubeViaRelay: Boolean = false,
    /** Optional `host:port` SOCKS5 the core sends raw TCP through. */
    val upstreamSocks5: String = "",
    val logLevel: String = "info",
) {
    fun scriptIdList(): List<String> = splitList(scriptIds).map(::extractId).filter { it.isNotEmpty() }.distinct()

    fun sniList(): List<String> = splitList(sniHosts)

    val needsCredentials: Boolean get() = mode != MODE_DIRECT

    /** Apps Script mode terminates TLS locally, so apps must trust the core's CA; full and direct mode do not. */
    val needsCa: Boolean get() = mode == MODE_APPS_SCRIPT

    val isValid: Boolean
        get() = mode in MODES && (!needsCredentials || (scriptIdList().isNotEmpty() && authKey.isNotBlank()))

    /** The JSON the native core reads (`Native.startProxy`). */
    fun toConfigJson(httpPort: Int, socksPort: Int): String {
        val obj = buildJsonObject {
            put("mode", mode)
            put("listen_host", "127.0.0.1")
            put("listen_port", httpPort)
            put("socks5_port", socksPort)
            putJsonArray("script_ids") { scriptIdList().forEach { add(JsonPrimitive(it)) } }
            put("auth_key", authKey.trim())
            put("front_domain", frontDomain.trim().ifEmpty { DEFAULT_FRONT_DOMAIN })
            put("google_ip", googleIp.trim().ifEmpty { DEFAULT_GOOGLE_IP })
            sniList().takeIf { it.isNotEmpty() }?.let { list ->
                putJsonArray("sni_hosts") { list.forEach { add(JsonPrimitive(it)) } }
            }
            put("verify_ssl", verifySsl)
            put("log_level", logLevel.ifBlank { "info" })
            put("parallel_relay", parallelRelay.coerceIn(1, 8))
            put("block_quic", blockQuic)
            put("block_stun", blockStun)
            put("block_doh", blockDoh)
            put("tunnel_doh", tunnelDoh)
            if (coalesceStepMs != 10) put("coalesce_step_ms", coalesceStepMs)
            if (coalesceMaxMs != 1000) put("coalesce_max_ms", coalesceMaxMs)
            if (youtubeViaRelay) put("youtube_via_relay", true)
            upstreamSocks5.trim().takeIf { it.isNotEmpty() }?.let { put("upstream_socks5", it) }
            put("fetch_ips_from_api", false)
            put("max_ips_to_scan", 20)
        }
        return obj.toString()
    }

    companion object {
        const val MODE_APPS_SCRIPT = "apps_script"
        const val MODE_FULL = "full"
        const val MODE_DIRECT = "direct"
        val MODES = listOf(MODE_APPS_SCRIPT, MODE_FULL, MODE_DIRECT)

        const val DEFAULT_GOOGLE_IP = "216.239.38.120"
        const val DEFAULT_FRONT_DOMAIN = "www.google.com"
        const val SHARE_PREFIX = "mhrv-rs://"

        val DEFAULT_SNI_POOL: List<String> = listOf(
            "www.google.com",
            "mail.google.com",
            "drive.google.com",
            "docs.google.com",
            "calendar.google.com",
            "accounts.google.com",
            "scholar.google.com",
            "maps.google.com",
            "chat.google.com",
            "translate.google.com",
            "play.google.com",
            "lens.google.com",
            "chromewebstore.google.com",
        )

        private val json = Json { ignoreUnknownKeys = true }

        private fun splitList(raw: String): List<String> =
            raw.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

        /** A bare deployment ID from either an ID or a `https://script.google.com/macros/s/<ID>/exec` URL. */
        fun extractId(input: String): String {
            var s = input.trim()
            val marker = "/macros/s/"
            val at = s.indexOf(marker)
            if (at >= 0) s = s.substring(at + marker.length)
            s = s.substringBefore('/').substringBefore('?')
            return s.trim()
        }

        /**
         * Reads what a user is most likely to paste: the core's own JSON config, an `mhrv-rs://` share string
         * (base64, usually deflated JSON), or one or more bare Apps Script deployment URLs / IDs.
         */
        fun fromText(text: String): MhrvRelayProfile? {
            val t = text.trim()
            if (t.isEmpty()) return null
            if (t.startsWith("{")) return fromJson(t)
            if (t.startsWith(SHARE_PREFIX, ignoreCase = true)) {
                return decodeShare(t.substring(SHARE_PREFIX.length))?.let(::fromJson)
            }
            if (t.contains("script.google.com")) {
                return MhrvRelayProfile(scriptIds = splitList(t).joinToString("\n")).takeIf { it.scriptIdList().isNotEmpty() }
            }
            return null
        }

        private fun decodeShare(body: String): String? {
            val raw = runCatching {
                Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(body.trim().trimEnd('='))
            }.getOrNull() ?: return null
            val inflated = inflateZlib(raw)?.decodeToString()
            val text = inflated ?: raw.decodeToString()
            return text.takeIf { it.trimStart().startsWith("{") }
        }

        private fun fromJson(text: String): MhrvRelayProfile? {
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
            if ("mode" !in obj && "script_ids" !in obj && "script_id" !in obj && "auth_key" !in obj) return null

            fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull
            fun list(key: String): String? = when (val e: JsonElement? = obj[key]) {
                is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("\n")
                is JsonPrimitive -> e.contentOrNull
                else -> null
            }
            val d = MhrvRelayProfile()
            val mode = when (str("mode")) {
                MODE_FULL -> MODE_FULL
                MODE_DIRECT, "google_only" -> MODE_DIRECT
                else -> MODE_APPS_SCRIPT
            }
            return MhrvRelayProfile(
                mode = mode,
                scriptIds = list("script_ids") ?: list("script_id") ?: "",
                authKey = str("auth_key") ?: "",
                googleIp = str("google_ip") ?: d.googleIp,
                frontDomain = str("front_domain") ?: d.frontDomain,
                sniHosts = list("sni_hosts") ?: "",
                verifySsl = (obj["verify_ssl"] as? JsonPrimitive)?.booleanOrNull ?: d.verifySsl,
                parallelRelay = (obj["parallel_relay"] as? JsonPrimitive)?.intOrNull ?: d.parallelRelay,
                blockQuic = (obj["block_quic"] as? JsonPrimitive)?.booleanOrNull ?: d.blockQuic,
                blockStun = (obj["block_stun"] as? JsonPrimitive)?.booleanOrNull ?: d.blockStun,
                blockDoh = (obj["block_doh"] as? JsonPrimitive)?.booleanOrNull ?: d.blockDoh,
                tunnelDoh = (obj["tunnel_doh"] as? JsonPrimitive)?.booleanOrNull ?: d.tunnelDoh,
                coalesceStepMs = (obj["coalesce_step_ms"] as? JsonPrimitive)?.intOrNull ?: d.coalesceStepMs,
                coalesceMaxMs = (obj["coalesce_max_ms"] as? JsonPrimitive)?.intOrNull ?: d.coalesceMaxMs,
                youtubeViaRelay = (obj["youtube_via_relay"] as? JsonPrimitive)?.booleanOrNull ?: d.youtubeViaRelay,
                upstreamSocks5 = str("upstream_socks5") ?: "",
                logLevel = str("log_level") ?: d.logLevel,
            )
        }
    }
}
