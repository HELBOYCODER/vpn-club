package dev.valid7996.narcicgeteway.domain.config

/**
 * The aether:// share link, the one PattNG writes and reads, so that an Aether profile moves between the two apps by
 * link or QR code: `aether://<endpoint>?protocol=..&scan=..#<name>`. A two-hop protocol carries its hops as the
 * outer and inner query values instead of an endpoint. Narcic adds three keys PattNG ignores (target, cmd, and the
 * exit-node's fm / dialMode, which PattNG has as well), so that its own profiles round-trip whole.
 */
object AetherLink {
    const val SCHEME = "aether://"

    private const val HEX = "0123456789ABCDEF"
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    fun isAetherLink(text: String): Boolean = text.trim().startsWith(SCHEME, ignoreCase = true)

    fun build(profile: AetherProfile, name: String): String {
        val protocol = profile.protocol
        val query = linkedMapOf("protocol" to protocol, "scan" to profile.scanMode)

        val overHttp2 = AetherCommands.masqueOverHttp2(protocol, profile.transport, profile.tor, profile.psiphon)
        if (profile.obfuscation != AetherProfile.OBF_AUTO && !overHttp2) query["noize"] = profile.obfuscation
        query["ip"] = profile.ipVersion
        profile.dns.takeIf { it.isNotBlank() }?.let { query["dns"] = it }
        profile.exitLoc.takeIf { it.isNotBlank() }?.let { query["exit_loc"] = it }

        if (AetherProfile.overMasque(protocol)) {
            query["transport"] = profile.transport
            profile.masqueSni.trim().takeIf { it.isNotEmpty() && it != AetherProfile.DEFAULT_MASQUE_SNI }?.let { query["sni"] = it }
            if (profile.fragment) {
                query["fragment"] = "1"
                AetherCommands.parseRange(profile.fragmentSize, 1..4096)?.let { query["fragment_size"] = AetherCommands.rangeText(it) }
                AetherCommands.parseRange(profile.fragmentDelay, 0..1000)?.let { query["fragment_delay"] = AetherCommands.rangeText(it) }
            }
            val warpUsed = profile.psiphon != AetherProfile.CARRIER_ONLY && profile.tor != AetherProfile.CARRIER_ONLY
            if (profile.ech && warpUsed) {
                query["ech"] = "1"
                profile.echDns.trim().takeIf { it.isNotEmpty() }?.let { query["ech_dns"] = it }
                profile.echDomain.trim().takeIf { it.isNotEmpty() }?.let { query["ech_domain"] = it }
            }
            if (profile.fingerprint != AetherProfile.FINGERPRINT_CHROME) query["fingerprint"] = profile.fingerprint
        }
        if (AetherProfile.isTwoHops(protocol)) {
            profile.wiwOuter.trim().takeIf { it.isNotEmpty() }?.let { query["outer"] = it }
            profile.wiwInner.trim().takeIf { it.isNotEmpty() }?.let { query["inner"] = it }
        }
        if (profile.psiphon != AetherProfile.CARRIER_OFF) {
            query["psiphon"] = profile.psiphon
            query["psiphon_mode"] = profile.psiphonMode
            profile.psiphonCdnIps.takeIf { it.isNotBlank() }?.let { query["cdn_ips"] = it }
            profile.psiphonCdnSni.takeIf { it.isNotBlank() }?.let { query["cdn_sni"] = it }
            profile.psiphonCdnSets.takeIf { it.isNotBlank() }?.let { query["cdn_sets"] = it }
            profile.psiphonRegion.takeIf { it.isNotBlank() }?.let { query["region"] = it }
            if (!profile.psiphonBundledList) query["psiphon_bundled"] = "0"
            if (!profile.psiphonBeast) query["psiphon_beast"] = "0"
        }
        if (profile.tor != AetherProfile.CARRIER_OFF) {
            query["tor"] = profile.tor
            query["tor_bridges"] = profile.torBridges
            query["tor_relays"] = profile.torRelays
            // Bridge lines never hold a semicolon: the core itself separates them with one.
            profile.torBridgeLines.lines().map(String::trim).filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }?.let { query["bridges"] = it.joinToString(";") }
        }
        profile.finalMask.takeIf { it.isNotBlank() }?.let { query["fm"] = it }
        profile.dialMode.takeIf { it.isNotBlank() }?.let { query["dialMode"] = it }
        profile.targetStrategy.takeIf { it.isNotBlank() }?.let { query["target"] = it }
        profile.command.takeIf { it.isNotBlank() }?.let { query["cmd"] = it }

        val endpoint = if (AetherProfile.isTwoHops(protocol)) "" else profile.endpointText().orEmpty()
        val queryText = query.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        return "$SCHEME$endpoint?$queryText#${encode(name)}"
    }

    /** The name and the profile the link holds; null when it is no aether:// link. Values a core would not take fall back to the defaults. */
    fun parse(text: String): Pair<String, AetherProfile>? {
        val trimmed = text.trim()
        if (!isAetherLink(trimmed)) return null
        val body = trimmed.substring(SCHEME.length)
        val name = decode(body.substringAfter('#', "")).trim().ifEmpty { "Aether" }
        val beforeFragment = body.substringBefore('#')
        val authority = beforeFragment.substringBefore('?').trim().trimEnd('/')
        val params = beforeFragment.substringAfter('?', "").split('&')
            .filter { it.isNotEmpty() }
            .associate { pair -> decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', "")) }

        fun pick(key: String, options: List<Pair<String, String>>, default: String): String =
            params[key]?.trim()?.lowercase()?.takeIf { value -> options.any { it.first == value } } ?: default

        val protocol = pick("protocol", AetherProfile.protocols, AetherProfile.PROTO_MASQUE)
        val overMasque = AetherProfile.overMasque(protocol)
        val ech = params["ech"] == "1"
        val endpoint = if (AetherProfile.isTwoHops(protocol)) null else AetherCommands.parseEndpoint(authority)
        val outer = AetherCommands.parseEndpoint(params["outer"])
        val inner = AetherCommands.parseEndpoint(params["inner"])
            ?.takeUnless { AetherProfile.distinctHops(protocol) && it.first == outer?.first }

        val profile = AetherProfile(
            protocol = protocol,
            server = endpoint?.first.orEmpty(),
            serverPort = endpoint?.second ?: 0,
            scanMode = pick("scan", AetherProfile.scanModes, AetherProfile.SCAN_BALANCED),
            transport = pick("transport", AetherProfile.transports, AetherProfile.TRANSPORT_H3),
            obfuscation = pick("noize", AetherProfile.obfuscations, AetherProfile.OBF_AUTO),
            ipVersion = pick("ip", AetherProfile.ipVersions, AetherProfile.IP_V4),
            dns = params["dns"]?.trim().orEmpty(),
            exitLoc = params["exit_loc"]?.trim().orEmpty(),
            fingerprint = pick("fingerprint", AetherProfile.fingerprints, AetherProfile.FINGERPRINT_CHROME),
            fragment = params["fragment"] == "1",
            fragmentSize = AetherCommands.parseRange(params["fragment_size"], 1..4096)?.let(AetherCommands::rangeText).orEmpty(),
            fragmentDelay = AetherCommands.parseRange(params["fragment_delay"], 0..1000)?.let(AetherCommands::rangeText).orEmpty(),
            ech = ech,
            echDns = params["ech_dns"]?.trim()?.takeIf { ech }.orEmpty(),
            echDomain = params["ech_domain"]?.trim()?.takeIf { ech }.orEmpty(),
            masqueSni = params["sni"]?.trim()?.takeIf { overMasque && it.isNotEmpty() } ?: AetherProfile.DEFAULT_MASQUE_SNI,
            wiwOuter = outer?.let { AetherCommands.endpointOf(it.first, it.second.toString()) }.orEmpty(),
            wiwInner = inner?.let { AetherCommands.endpointOf(it.first, it.second.toString()) }.orEmpty(),
            tor = pick("tor", AetherProfile.carrierModes, AetherProfile.CARRIER_OFF),
            torBridges = pick("tor_bridges", AetherProfile.torBridgeModes, AetherProfile.TOR_BRIDGES_AUTO),
            torBridgeLines = params["bridges"]?.split(';')?.joinToString("\n").orEmpty(),
            torRelays = pick("tor_relays", AetherProfile.torRelayModes, AetherProfile.TOR_RELAYS_AUTO),
            psiphon = pick("psiphon", AetherProfile.carrierModes, AetherProfile.CARRIER_OFF),
            psiphonMode = pick("psiphon_mode", AetherProfile.psiphonModes, AetherProfile.PSIPHON_MODE_AUTO),
            psiphonRegion = params["region"]?.trim()?.uppercase().orEmpty(),
            psiphonCdnIps = params["cdn_ips"]?.trim().orEmpty(),
            psiphonCdnSni = params["cdn_sni"]?.trim().orEmpty(),
            psiphonCdnSets = params["cdn_sets"]?.trim().orEmpty(),
            psiphonBundledList = params["psiphon_bundled"] != "0",
            psiphonBeast = params["psiphon_beast"] != "0",
            finalMask = params["fm"]?.trim().orEmpty(),
            dialMode = params["dialMode"]?.trim().orEmpty(),
            targetStrategy = params["target"]?.trim().orEmpty(),
            command = params["cmd"]?.trim().orEmpty(),
        )
        return name to profile
    }

    private fun encode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val value = byte.toInt() and 0xFF
            val c = value.toChar()
            if (value < 0x80 && c in UNRESERVED) {
                append(c)
            } else {
                append('%')
                append(HEX[value shr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    private fun decode(text: String): String {
        if ('%' !in text) return text
        val bytes = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%' && i + 2 < text.length) {
                val hi = (text[i + 1].digitToIntOrNull(16) ?: -1)
                val lo = (text[i + 2].digitToIntOrNull(16) ?: -1)
                if (hi >= 0 && lo >= 0) {
                    bytes.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            bytes.addAll(c.toString().encodeToByteArray().toList())
            i++
        }
        return bytes.toByteArray().decodeToString()
    }
}
