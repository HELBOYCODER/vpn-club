package com.helboy.vpnclub.corex.aether

import kotlinx.serialization.json.*


/**
 * CDN fronting of the Psiphon client, as Shirokhorshid configures it (the app this one is measured against: the same
 * edge IPs and server names work there and did not work here).
 *
 * What a custom IP or SNI needs to work is more than the scan spec the list of candidates fills:
 *  - the dial overrides, which point the fronted-meek dials at the CDN edges (Akamai's by address, Fastly's by
 *    provider) with a browser ClientHello, and name the certificates the edges answer with. A fronted dial presents a
 *    server name that is not the edge's own, so the certificate is checked against [VerifyServerNames], and the
 *    list here held only the SNI the user typed: every handshake failed its verification;
 *  - the probability 1.0 of the overrides: without it they are not used (the default is zero);
 *  - the built-in scan spec kept alongside the user's candidates, which are tried in addition to it, not instead of it;
 *  - "beast mode", the aggressive establishment that tries every protocol on every server at once.
 */
object PsiphonFronting {

    private val SEPARATORS = charArrayOf(' ', '\t', '\n', '\r', ',', ';')

    /** The addresses of [raw] the client can dial: IPv4 and IPv4 CIDR, whatever separated them; the rest is dropped. */
    fun ipCandidates(raw: String): List<String> = raw.split(*SEPARATORS)
        .map { it.trim() }.filter { it.isNotEmpty() && (isIpv4(it) || isIpv4Cidr(it)) }.distinct()

    /** The server names of [raw]: host names, with a scheme or a path in front or behind taken off. */
    fun sniCandidates(raw: String): List<String> = raw.split(*SEPARATORS)
        .map { normalizeHostname(it) }.filter { it.isNotEmpty() }.distinct()

    /** [raw] as the comma separated list the core's --psiphon-cdn-ips takes; null when nothing in it is an address. */
    fun ipFlagValue(raw: String): String? = ipCandidates(raw).takeIf { it.isNotEmpty() }?.joinToString(",")

    /** [raw] as the comma separated list the core's --psiphon-cdn-sni takes; null when nothing in it is a host name. */
    fun sniFlagValue(raw: String): String? = sniCandidates(raw).takeIf { it.isNotEmpty() }?.joinToString(",")

    private val AKAMAI_EDGES = listOf(
        "edge-a-1" to "23.215.0.206",
        "edge-a-2" to "23.215.0.203",
        "edge-b-1" to "23.212.250.91",
        "edge-b-2" to "23.212.250.78",
        "edge-c-1" to "23.12.147.13",
        "edge-c-2" to "23.12.147.29",
        "edge-d-1" to "23.73.207.8",
        "edge-d-2" to "23.73.207.15",
        "edge-original" to "92.123.102.43",
    )

    private val AKAMAI_NAMES = listOf(
        "a248.e.akamai.net", "a.akamaized.net", "a.akamaized-staging.net",
        "a.akamaihd.net", "a.akamaihd-staging.net", "www.akamai.com",
    )

    private val FASTLY_NAMES = listOf(
        "www.python.org", "pypi.org", "fastly.com", "www.fastly.com", "developer.fastly.com",
        "githubassets.com", "github.com", "github.io", "githubusercontent.com",
    )

    private const val TLS_PROFILE = "Chrome-83"

    /** The fronted-meek dial overrides: Fastly by provider and by address, then each Akamai edge, with the user's first SNI when there is one. */
    fun dialOverrides(customSni: String): JsonArray {
        val sni = sniCandidates(customSni).firstOrNull().orEmpty()
        return JsonArray(
            buildList {
                add(
                    dialOverride(
                        id = "fastly-provider", providers = listOf("(?i)fastly"), addresses = null,
                        dial = "pypi.org", sni = "pypi.org", verify = FASTLY_NAMES, alpn = listOf("h2", "http/1.1"),
                    ),
                )
                add(
                    dialOverride(
                        id = "fastly-address", providers = null, addresses = listOf("(?i)(fastly|pypi|python|github)"),
                        dial = "pypi.org", sni = "pypi.org", verify = FASTLY_NAMES, alpn = listOf("h2", "http/1.1"),
                    ),
                )
                val seen = HashSet<String>()
                for ((id, ip) in AKAMAI_EDGES) {
                    if (!seen.add(ip)) continue
                    val name = sni.ifEmpty { ip }
                    add(
                        dialOverride(
                            id = id, providers = null, addresses = listOf(".*"),
                            dial = ip, sni = name, verify = (listOf(name, ip) + AKAMAI_NAMES).distinct(), alpn = listOf("http/1.1"),
                        ),
                    )
                }
            },
        )
    }

    private fun dialOverride(
        id: String,
        providers: List<String>?,
        addresses: List<String>?,
        dial: String,
        sni: String,
        verify: List<String>,
        alpn: List<String>,
    ): JsonObject = buildJsonObject {
        put("OverrideID", id)
        providers?.let { list -> putJsonArray("MatchFrontingProviderIDRegexes") { list.forEach { add(it) } } }
        addresses?.let { list -> putJsonArray("MatchDialAddressRegexes") { list.forEach { add(it) } } }
        putJsonArray("DialAddresses") { add(dial) }
        put("SNIServerName", sni)
        putJsonArray("VerifyServerNames") { verify.forEach { add(it) } }
        putJsonArray("ALPNProtocols") { alpn.forEach { add(it) } }
        put("TLSProfile", TLS_PROFILE)
    }

    /** The scan spec of the user's own candidates; null when there is no address to scan. */
    fun scanSpec(ips: String, snis: String): JsonObject? {
        val addresses = ipCandidates(ips)
        if (addresses.isEmpty()) return null
        val names = sniCandidates(snis)
        return buildJsonObject {
            putJsonArray("IPCandidates") { addresses.forEach { add(it) } }
            if (names.isNotEmpty()) putJsonArray("SNIServerNames") { names.forEach { add(it) } }
        }
    }

    /**
     * The keys of CDN fronting in a Psiphon client configuration. With [withScanSpec] false the user's candidates are left to
     * whoever builds the scan spec (the Aether core builds its own from --psiphon-cdn-ips / --psiphon-cdn-sni).
     */
    fun JsonObjectBuilder.putFronting(ips: String, snis: String, withScanSpec: Boolean = true) {
        put("FrontedMeekDialOverrides", dialOverrides(snis))
        put("FrontedMeekDialOverridesProbability", JsonPrimitive(1.0))
        put("FrontedMeekCDNScanUseBuiltInSpec", true)
        if (withScanSpec) scanSpec(ips, snis)?.let { put("FrontedMeekCDNScanSpec", it) }
    }

    /** Beast mode: the client tries every protocol on every server at once, which gets through where trying them one by one does not. */
    fun JsonObjectBuilder.putBeast(beast: Boolean) {
        if (beast) put("AggressiveEstablishment", true)
    }

    /** The configuration laid over the Aether core's own Psiphon configuration (--psiphon-config): the overrides and beast mode. */
    fun overlay(snis: String, beast: Boolean): String = buildJsonObject {
        putFronting(ips = "", snis = snis, withScanSpec = false)
        putBeast(beast)
        // Resolvers for the fetch of the server list, which the DNS of a filtered network answers wrongly.
        putJsonArray("DNSResolverAlternateServers") { RESOLVERS.forEach { add(it) } }
    }.toString()

    val RESOLVERS = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4")

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } && (p.toIntOrNull() ?: 256) <= 255 }
    }

    private fun isIpv4Cidr(value: String): Boolean {
        val parts = value.split('/')
        if (parts.size != 2) return false
        return isIpv4(parts[0]) && parts[1].isNotEmpty() && parts[1].all { it.isDigit() } && (parts[1].toIntOrNull() ?: 33) <= 32
    }

    private fun normalizeHostname(raw: String): String {
        var v = raw.trim().lowercase()
        for (prefix in listOf("https://", "http://")) v = v.removePrefix(prefix)
        v = v.substringBefore('/').trim('.')
        if (v.isEmpty() || !v.contains('.')) return ""
        if (isIpv4(v)) return ""
        if (v.any { it.code > 127 || !(it.isLetterOrDigit() || it == '.' || it == '-') }) return ""
        if (v.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return ""
        return v
    }
}
