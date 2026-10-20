package dev.valid7996.narcicgeteway.data.config

import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import dev.valid7996.narcicgeteway.platform.httpJson

/**
 * Client for the optional "short link" relay (see nrg-relay-worker.js).
 * The relay only ever stores the already-encrypted .nrg blob; the link carries just an id and the relay host.
 */
/** The relay holds a one-time key for this link and none was supplied. */
class RelayKeyRequiredException : Exception("A private key is required for this link")

/** The one-time key of this link was already activated on another device. */
class RelayAlreadyClaimedException : Exception("This link was already activated on another device")

object NrgRelay {
    private val ID_REGEX = Regex("\"id\"\\s*:\\s*\"([A-Za-z0-9_-]{6,64})\"")
    private val HOST_REGEX = Regex("[A-Za-z0-9.-]+(:\\d{1,5})?")
    private val IPV4_REGEX = Regex("\\d{1,3}(\\.\\d{1,3}){3}")
    private const val MAX_BODY_CHARS = 6_000_000

    /** "https://nrg.me.workers.dev/" -> "nrg.me.workers.dev"; null when it is not a usable public host. */
    fun hostOf(endpoint: String): String? {
        val host = endpoint.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        return host.takeIf { validHost(it) }
    }

    fun validHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253 || !HOST_REGEX.matches(host)) return false
        val name = host.substringBefore(':').lowercase()
        if ('.' !in name || name.startsWith('.') || name.endsWith('.')) return false
        if (name == "localhost" || name.endsWith(".local") || name.endsWith(".internal")) return false
        if (IPV4_REGEX.matches(name)) return false
        return true
    }

    suspend fun upload(endpoint: String, token: String?, blob: ByteArray, expiresAt: Long?, userKey: String? = null): NrgLink.Relay {
        val host = hostOf(endpoint) ?: throw IllegalArgumentException("آدرس سرور رله نامعتبر است")
        val ttl = expiresAt?.let { ((it - currentTimeMillis()) / 1000L).coerceAtLeast(60L) + 3600L }
        val url = "https://$host/put" + (if (ttl != null) "?ttl=$ttl" else "")
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "text/plain"
        headers["User-Agent"] = "NarcicGetway"
        if (!token.isNullOrBlank()) headers["X-NRG-Token"] = token.trim()
        // The relay keeps only a hash of the key and releases the blob to the first device that proves it knows the key.
        if (!userKey.isNullOrBlank()) headers["X-NRG-Proof"] = ZsxCrypto.claimProof(userKey)
        val res = httpJson("POST", url, headers, NrgLink.toUrlSafe(blob), 15_000, 30_000, null)
        check(res.code in 200..299) { "HTTP ${res.code}" }
        val id = ID_REGEX.find(res.body)?.groupValues?.get(1) ?: error("پاسخ نامعتبر از سرور رله")
        return NrgLink.Relay(id, host)
    }

    suspend fun download(relay: NrgLink.Relay, userKey: String?, deviceId: String): ByteArray {
        require(validHost(relay.host)) { "آدرس سرور رله در لینک نامعتبر است" }
        val res = if (userKey.isNullOrBlank()) {
            httpJson(
                "GET", "https://${relay.host}/get/${relay.id}",
                mapOf("User-Agent" to "NarcicGetway"), null, 15_000, 30_000, null,
            )
        } else {
            httpJson(
                "POST", "https://${relay.host}/claim/${relay.id}",
                mapOf("User-Agent" to "NarcicGetway", "Content-Type" to "application/json"),
                "{\"proof\":\"${ZsxCrypto.claimProof(userKey)}\",\"device\":\"$deviceId\"}",
                15_000, 30_000, null,
            )
        }
        when (res.code) {
            in 200..299 -> Unit
            401 -> throw RelayKeyRequiredException()
            403 -> throw ZsxWrongPasswordException()
            404 -> error("این لینک پیدا نشد یا منقضی شده است")
            409 -> throw RelayAlreadyClaimedException()
            else -> error("HTTP ${res.code}")
        }
        require(res.body.length <= MAX_BODY_CHARS) { "response too large" }
        return NrgLink.decode(res.body)
    }
}
