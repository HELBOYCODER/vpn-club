package com.helboy.vpnclub.corex.aether

/**
 * Builds command line arguments for the Rust Aether core daemon.
 * A faithful port of PattNG's AetherCoreManager.buildArguments.
 */
object AetherCommands {

    const val BIND = "--bind"
    const val UPSTREAM = "--upstream"
    const val TOR_BIND = "--tor-bind"
    const val PSIPHON_BIND = "--psiphon-bind"
    const val REGISTER = "--register"
    const val GOOL_CLASSIC = "--gool-classic"
    const val MASQUE_SNI = "--masque-sni"
    const val API_ADDRESS = "--api-address"
    const val ENROLL_ADDRESS = "--enroll-address"
    const val GOOL_PEER = "--gool-peer"
    const val LOG_LEVEL = "--log-level"
    const val DEFAULT_LOG_LEVEL = "info"

    /** The marker that asks the core to load the shipped Psiphon server entries file. */
    const val PSIPHON_SERVER_ENTRIES = "--psiphon-server-entries"
    const val SHIPPED_LIST = "shipped"
    const val PSIPHON_CONFIG = "--psiphon-config"

    /**
     * The words the profile writes after --psiphon-config: the file is the overlay of [PsiphonFronting.overlay], which the
     * app writes at start (see [withOverlay]); the second word says the overlay holds beast mode as well.
     */
    const val OVERLAY_WORD = "overlay"
    const val OVERLAY_BEAST_WORD = "overlay-beast"

    private fun settingValue(value: String?): String? =
        value?.trim()?.takeUnless { it.isEmpty() || it.startsWith('-') }

    /** MASQUE over HTTP/2, or a REVERSE carrier around the tunnel, takes no obfuscation noise. */
    fun masqueOverHttp2(
        protocol: String,
        transport: String,
        tor: String,
        psiphon: String,
    ): Boolean = AetherProfile.overMasque(protocol) &&
        (transport == AetherProfile.TRANSPORT_H2 || tor == AetherProfile.CARRIER_REVERSE || psiphon == AetherProfile.CARRIER_REVERSE)

    private fun fingerprintArguments(fingerprint: String): List<String> {
        val (ciphers, grease) = when (fingerprint) {
            AetherProfile.FINGERPRINT_FIREFOX -> (
                "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:" +
                    "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-SHA:ECDHE-RSA-AES256-SHA:" +
                    "AES128-GCM-SHA256:AES256-GCM-SHA384:AES128-SHA:AES256-SHA"
            ) to false

            AetherProfile.FINGERPRINT_SEMI_PYTHON -> (
                "ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:" +
                    "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA:" +
                    "ECDHE-ECDSA-AES128-SHA256:ECDHE-RSA-AES128-SHA256"
            ) to false

            AetherProfile.FINGERPRINT_GO -> (
                "ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:" +
                    "ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305:ECDHE-ECDSA-AES128-SHA:ECDHE-RSA-AES128-SHA:" +
                    "ECDHE-ECDSA-AES256-SHA:ECDHE-RSA-AES256-SHA"
            ) to false

            else -> "ALL:!aPSK:!ECDSA+SHA1:!3DES" to true
        }
        return listOf("--tls-ciphers", ciphers) + if (grease) emptyList() else listOf("--disable-grease")
    }

    /**
     * [text] as a range of [bounds]: "16" or "8-16", trimmed; null when out of bounds or malformed.
     * [AetherRange] equivalent.
     */
    fun parseRange(text: String?, bounds: IntRange): Pair<Int, Int>? {
        val parts = text?.trim()?.split('-') ?: return null
        if (parts.size !in 1..2) return null
        val values = parts.map { part -> part.trim().toIntOrNull()?.takeIf { it in bounds } ?: return null }
        return Pair(values.min(), values.max())
    }

    fun rangeText(range: Pair<Int, Int>): String =
        if (range.first == range.second) "${range.first}" else "${range.first}-${range.second}"

    /**
     * [profile] as an endpoint "host:port"; [AetherEndpoint]-equivalent validation, IPv6 brackets included.
     */
    fun endpointOf(host: String?, port: String?): String? {
        val h = host?.trim()?.removeSurrounding("[", "]")?.takeIf { it.isNotEmpty() } ?: return null
        val p = port?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return if (':' in h) "[$h]:$p" else "$h:$p"
    }

    fun parseEndpoint(value: String?): Pair<String, Int>? {
        val text = value?.trim().orEmpty()
        val separator = text.lastIndexOf(':')
        if (separator <= 0) return null
        val host = text.substring(0, separator)
        if (':' in host && !(host.startsWith('[') && host.endsWith(']'))) return null
        val port = text.substring(separator + 1).trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val cleanHost = host.removeSurrounding("[", "]")
        return Pair(cleanHost, port)
    }

    fun buildArguments(
        profile: AetherProfile,
        port: Int,
        scan: Boolean = false,
        logLevel: String = DEFAULT_LOG_LEVEL,
    ): List<String> {
        // A scan looks for WARP endpoints from where the session will look: a carrier around the
        // tunnel stays (REVERSE), while one inside the tunnel (CHAIN / ONLY) has no part in it.
        val tor = profile.tor.takeUnless { scan && it != AetherProfile.CARRIER_REVERSE } ?: AetherProfile.CARRIER_OFF
        val psiphon = profile.psiphon.takeUnless { scan && it != AetherProfile.CARRIER_REVERSE } ?: AetherProfile.CARRIER_OFF

        val dialsPsiphon = psiphon == AetherProfile.CARRIER_CHAIN
        val dialsTor = tor == AetherProfile.CARRIER_CHAIN && !dialsPsiphon
        var next = port + 1
        val own = if (dialsPsiphon || dialsTor) next++ else port
        val torBind = when (tor) {
            AetherProfile.CARRIER_CHAIN -> if (dialsTor) port else next++
            AetherProfile.CARRIER_REVERSE -> next++
            else -> null
        }
        val psiphonBind = when (psiphon) {
            AetherProfile.CARRIER_CHAIN -> if (dialsPsiphon) port else next++
            AetherProfile.CARRIER_REVERSE -> 0
            else -> null
        }
        return buildList {
            addAll(listOf(BIND, "127.0.0.1:$own"))
            if (psiphon != AetherProfile.CARRIER_ONLY && tor != AetherProfile.CARRIER_ONLY) {
                // wg-over-masque runs on the core's gool protocol, with the MASQUE + masque-gool keys.
                addAll(listOf("--protocol", AetherProfile.coreNameOf(profile.protocol)))
                // gool has meant WireGuard over MASQUE since aether 2.3.0; WARP-in-WARP is the classic gool, asked for
                // by name rather than left to the hop settings that select it as well. A core older than that does not
                // know the flag, and its gool is the classic one already: AetherSupport.adapt drops it there.
                if (profile.protocol == AetherProfile.PROTO_GOOL) add(GOOL_CLASSIC)
                addAll(listOf("--scan", profile.scanMode))
                masqueOverHttp2(profile.protocol, profile.transport, tor, psiphon).let { overHttp2 ->
                    if (profile.obfuscation != AetherProfile.OBF_AUTO && !overHttp2) {
                        addAll(listOf("--noize", profile.obfuscation))
                    }
                }
                addAll(listOf("--ip", profile.ipVersion))
                settingValue(profile.dns)?.let { addAll(listOf("--dns", it)) }
                // A scan keeps the exit rule as well, so that it ends on an endpoint the session will accept.
                settingValue(profile.exitLoc)?.let { addAll(listOf("--exit-loc", it)) }

                if (AetherProfile.overMasque(profile.protocol) && profile.transport == AetherProfile.TRANSPORT_H2) {
                    add("--h2")
                    if (profile.fragment) {
                        add("--fragment")
                        parseRange(profile.fragmentSize, 1..4096)
                            ?.let { addAll(listOf("--fragment-size", rangeText(it))) }
                        parseRange(profile.fragmentDelay, 0..1000)
                            ?.let { addAll(listOf("--fragment-delay", rangeText(it))) }
                    }
                }
                if (AetherProfile.overMasque(profile.protocol) && profile.ech) {
                    addAll(listOf("--ech", "auto"))
                    addAll(listOf("--ech-dns", settingValue(profile.echDns) ?: AetherProfile.DEFAULT_ECH_DNS))
                    addAll(listOf("--ech-domain", settingValue(profile.echDomain) ?: AetherProfile.DEFAULT_ECH_DOMAIN))
                }
                if (AetherProfile.overMasque(profile.protocol)) {
                    // The server name the MASQUE handshakes put in their ClientHello, on either carrier and both
                    // hops, the default named as well so that the command shows what is sent. A core that does not
                    // know the flag (older than 2.3) has it dropped by AetherSupport.adapt.
                    addAll(listOf(MASQUE_SNI, settingValue(profile.masqueSni) ?: AetherProfile.DEFAULT_MASQUE_SNI))
                    addAll(fingerprintArguments(profile.fingerprint))
                }

                if (profile.protocol == AetherProfile.PROTO_WG_OVER_MASQUE) {
                    // The outer hop is a MASQUE gateway, which a scan looks for afresh. The inner one is the WireGuard
                    // endpoint dialled inside the tunnel, the one WARP assigned the key unless one is named; no scan
                    // looks for it, so a scan keeps the profile's own and finds a gateway that works with it.
                    // (It used to be passed as --wiw-scan / --wiw-outer, which is WARP-in-WARP, not this tunnel.)
                    parseEndpoint(profile.wiwOuter).takeUnless { scan }?.let { (host, p) ->
                        addAll(listOf("--peer", endpointOf(host, p.toString()) ?: "$host:$p"))
                    }
                    parseEndpoint(profile.wiwInner)?.let { (host, p) ->
                        addAll(listOf(GOOL_PEER, endpointOf(host, p.toString()) ?: "$host:$p"))
                    }
                } else if (AetherProfile.isTwoHops(profile.protocol)) {
                    val hop = if (profile.protocol == AetherProfile.PROTO_MIM) "--mim" else "--wiw"
                    val outer = profile.wiwOuter.trim().takeUnless { scan }
                    val inner = profile.wiwInner.trim().takeUnless { scan }
                    outer?.takeIf { it.isNotEmpty() }?.let { addAll(listOf("$hop-outer", it)) }
                    inner?.takeIf { it.isNotEmpty() }?.let { addAll(listOf("$hop-inner", it)) }
                    if (outer.isNullOrEmpty() && inner.isNullOrEmpty()) add("$hop-scan")
                } else if (!scan) {
                    profile.endpointText()?.let { addAll(listOf("--peer", it)) }
                }

                add(if (scan) "--no-quick-reconnect" else "--quick-reconnect")
            }

            when (tor) {
                AetherProfile.CARRIER_OFF -> Unit
                AetherProfile.CARRIER_CHAIN -> add("--tor")
                AetherProfile.CARRIER_REVERSE -> add("--tor-reverse")
                AetherProfile.CARRIER_ONLY -> add("--tor-only")
            }
            torBind?.let { addAll(listOf(TOR_BIND, "127.0.0.1:$it")) }
            if (tor != AetherProfile.CARRIER_OFF) {
                when (profile.torBridges) {
                    AetherProfile.TOR_BRIDGES_AUTO -> Unit
                    AetherProfile.TOR_BRIDGES_FIRST -> add("--tor-bridges")
                    AetherProfile.TOR_BRIDGES_NEVER -> add("--no-tor-bridges")
                    AetherProfile.TOR_BRIDGES_OWN -> profile.torBridgeLines.lines()
                        .map(String::trim)
                        .filter { it.isNotEmpty() }
                        .forEach { addAll(listOf("--tor-bridge", it)) }
                }
                if (profile.torBridges == AetherProfile.TOR_BRIDGES_AUTO ||
                    profile.torBridges == AetherProfile.TOR_BRIDGES_FIRST
                ) {
                    profile.torRelays.takeUnless { it == AetherProfile.TOR_RELAYS_AUTO }
                        ?.let { addAll(listOf("--tor-relays", it)) }
                }
            }

            when (psiphon) {
                AetherProfile.CARRIER_OFF -> Unit
                AetherProfile.CARRIER_CHAIN -> add("--psiphon")
                AetherProfile.CARRIER_REVERSE -> add("--psiphon-reverse")
                AetherProfile.CARRIER_ONLY -> add("--psiphon-only")
            }
            psiphonBind?.let { addAll(listOf(PSIPHON_BIND, "127.0.0.1:$it")) }
            if (psiphon != AetherProfile.CARRIER_OFF) {
                addAll(listOf("--psiphon-mode", profile.psiphonMode))
                // The addresses and names are cleaned the way Shirokhorshid cleans them (separators, schemes, junk); a list
                // as typed, with new lines or a "https://" in it, was not read by the core. A name alone is allowed: it
                // is then presented to the built-in edges.
                val cdnIps = PsiphonFronting.ipFlagValue(profile.psiphonCdnIps)?.takeIf { profile.psiphonMode != AetherProfile.PSIPHON_MODE_DIRECT }
                cdnIps?.let { addAll(listOf("--psiphon-cdn-ips", it)) }
                PsiphonFronting.sniFlagValue(profile.psiphonCdnSni)?.let { addAll(listOf("--psiphon-cdn-sni", it)) }
                if (profile.psiphonMode != AetherProfile.PSIPHON_MODE_DIRECT) {
                    settingValue(profile.psiphonCdnSets)?.let { addAll(listOf("--psiphon-cdn-sets", it)) }
                }
                settingValue(profile.psiphonRegion)?.let { addAll(listOf("--psiphon-region", it)) }
                // The bundled list, unless the profile wants Psiphon to fetch a fresh one before it dials anything.
                if (profile.psiphonBundledList) addAll(listOf(PSIPHON_SERVER_ENTRIES, SHIPPED_LIST))
                // Dial overrides of the CDN edges and beast mode, laid over the core's own Psiphon configuration.
                addAll(listOf(PSIPHON_CONFIG, if (profile.psiphonBeast) OVERLAY_BEAST_WORD else OVERLAY_WORD))
            }
            addAll(listOf(LOG_LEVEL, logLevel))
        }
    }

    fun words(command: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var open = false
        for (c in command) {
            when {
                quote != null -> if (c == quote) quote = null else word.append(c)
                c == '"' || c == '\'' -> {
                    quote = c
                    open = true
                }
                c.isWhitespace() -> if (open) {
                    words.add(word.toString())
                    word.setLength(0)
                    open = false
                }
                else -> {
                    word.append(c)
                    open = true
                }
            }
        }
        if (open) words.add(word.toString())
        return words
    }

    fun isCustom(profile: AetherProfile): Boolean = profile.command.trim().isNotEmpty()

    fun argumentsOf(command: String): List<String> {
        val words = words(command)
        return if (words.firstOrNull()?.startsWith("-") == false) words.drop(1) else words
    }

    /**
     * The arguments a session or a scan runs on. A command the profile carries runs as written, except that
     * it gets what the app depends on when it names none of it: a listener on [port] for the app to dial
     * (Psiphon's or Tor's when one of them runs inside the tunnel, see [listenerFlagOf]) and a log level
     * that writes the core's info lines, which the readiness check of a Psiphon session waits on.
     */
    fun runArguments(
        profile: AetherProfile,
        port: Int,
        scan: Boolean = false,
        logLevel: String = DEFAULT_LOG_LEVEL,
    ): List<String> {
        if (!isCustom(profile)) return buildArguments(profile, port, scan, logLevel)
        var arguments = argumentsOf(profile.command)
        val listener = listenerFlagOf(arguments)
        if (listener !in arguments) arguments = withListener(arguments, listener, port)
        if (LOG_LEVEL !in arguments && "--verbose" !in arguments) arguments = arguments + listOf(LOG_LEVEL, logLevel)
        return arguments
    }

    /** Where Tor stands in the tunnel [argv] runs, read the way the core reads it: the last mode flag wins. */
    fun torModeOf(argv: List<String>): String = argv.fold(AetherProfile.CARRIER_OFF) { mode, word ->
        when (word) {
            "--tor" -> AetherProfile.CARRIER_CHAIN
            "--tor-reverse" -> AetherProfile.CARRIER_REVERSE
            "--tor-only" -> AetherProfile.CARRIER_ONLY
            else -> mode
        }
    }

    /** Where Psiphon stands in the tunnel [argv] runs, read the way the core reads it: the last mode flag wins. */
    fun psiphonModeOf(argv: List<String>): String = argv.fold(AetherProfile.CARRIER_OFF) { mode, word ->
        when (word) {
            "--psiphon" -> AetherProfile.CARRIER_CHAIN
            "--psiphon-reverse" -> AetherProfile.CARRIER_REVERSE
            "--psiphon-only" -> AetherProfile.CARRIER_ONLY
            else -> mode
        }
    }

    /** The option naming the listener the app dials: Psiphon's when Psiphon runs inside the tunnel, Tor's when Tor does, else the core's own. */
    fun listenerFlagOf(argv: List<String>): String = when {
        psiphonModeOf(argv) == AetherProfile.CARRIER_CHAIN -> PSIPHON_BIND
        torModeOf(argv) == AetherProfile.CARRIER_CHAIN -> TOR_BIND
        else -> BIND
    }

    /** The loopback port the app dials on a core started with [argv]; null when the listener names none that reads as a port. */
    fun listenerPortOf(argv: List<String>): Int? =
        valueAfter(argv, listenerFlagOf(argv))?.substringAfterLast(':', "")?.toIntOrNull()

    /** [arguments] without every [flag] and the value after it. */
    fun withoutOption(arguments: List<String>, flag: String): List<String> {
        val kept = mutableListOf<String>()
        var index = 0
        while (index < arguments.size) {
            if (arguments[index] == flag) index += 2 else kept.add(arguments[index++])
        }
        return kept
    }

    /** [arguments] with the listener [flag] names on the loopback port [port], in place of whatever they named for it. */
    fun withListener(arguments: List<String>, flag: String, port: Int): List<String> =
        withoutOption(arguments, flag) + listOf(flag, "127.0.0.1:$port")

    private fun valueAfter(argv: List<String>, flag: String): String? =
        argv.lastIndexOf(flag).takeIf { it >= 0 }?.let { argv.getOrNull(it + 1) }

    /**
     * True when the listener the app dials is Psiphon's, inside the tunnel or alone. It answers the SOCKS greeting as
     * soon as it is bound and closes every connection until Psiphon has a tunnel, so the greeting alone says nothing
     * and readiness needs the core's word [PSIPHON_READY] as well.
     */
    fun readyNeedsWord(argv: List<String>): Boolean =
        psiphonModeOf(argv).let { it == AetherProfile.CARRIER_CHAIN || it == AetherProfile.CARRIER_ONLY }

    /** True when a core started with [argv] writes its info lines, the ready word among them. */
    fun showsInfo(argv: List<String>): Boolean =
        "--verbose" in argv || (valueAfter(argv, LOG_LEVEL) ?: DEFAULT_LOG_LEVEL) in setOf("info", "debug", "trace")

    /** [argv] at a level that writes the info lines: as they are, unless they name a quieter level, which gives way to the default. */
    fun withInfoLines(argv: List<String>): List<String> =
        if (showsInfo(argv)) argv else withoutOption(argv, LOG_LEVEL) + listOf(LOG_LEVEL, DEFAULT_LOG_LEVEL)

    /** The core's word that Psiphon has a tunnel. */
    val PSIPHON_READY = Regex("""psiphon is ready""")

    fun isReadyWord(line: String): Boolean = PSIPHON_READY.containsMatchIn(line)

    /**
     * [arguments] as the core is started with them: the word [SHIPPED_LIST] after [PSIPHON_SERVER_ENTRIES] gives way
     * to [entries], the app's unpacked list, or the flag goes when there is no such file, so that the core never
     * reads a file called "shipped". A file of the command's own is left as written.
     */
    fun withShippedList(arguments: List<String>, entries: String?): List<String> {
        val at = arguments.indexOf(PSIPHON_SERVER_ENTRIES)
        if (at < 0 || arguments.getOrNull(at + 1) != SHIPPED_LIST) return arguments
        return if (entries == null) {
            arguments.filterIndexed { index, _ -> index != at && index != at + 1 }
        } else {
            arguments.toMutableList().also { it[at + 1] = entries }
        }
    }

    /**
     * [arguments] with the overlay word after --psiphon-config replaced by [path], the file the app wrote; the flag goes
     * when there is no file. A file of the command's own is left as written.
     */
    fun withOverlay(arguments: List<String>, path: String?): List<String> {
        val at = arguments.indexOf(PSIPHON_CONFIG)
        val word = arguments.getOrNull(at + 1)
        if (at < 0 || (word != OVERLAY_WORD && word != OVERLAY_BEAST_WORD)) return arguments
        return if (path == null) {
            arguments.filterIndexed { index, _ -> index != at && index != at + 1 }
        } else {
            arguments.toMutableList().also { it[at + 1] = path }
        }
    }

    /** The overlay a run of [arguments] asks for: the first of its CDN server names and beast mode; null when it asks for none. */
    fun overlayOf(arguments: List<String>): String? {
        val at = arguments.indexOf(PSIPHON_CONFIG)
        val word = arguments.getOrNull(at + 1)
        if (at < 0 || (word != OVERLAY_WORD && word != OVERLAY_BEAST_WORD)) return null
        return PsiphonFronting.overlay(valueAfter(arguments, "--psiphon-cdn-sni").orEmpty(), word == OVERLAY_BEAST_WORD)
    }

    /** The settings of the WARP keys page: they are that page's alone, no profile's settings reach them. */
    data class KeysRequest(
        val kind: String = "all",
        val enrollAddress: String = AetherProfile.DEFAULT_ENROLL_ADDRESS,
        val fragment: Boolean = false,
        val fragmentSize: String = "",
        val fragmentDelay: String = "",
        val ech: Boolean = false,
        val echDns: String = AetherProfile.DEFAULT_ECH_DNS,
        val echDomain: String = AetherProfile.DEFAULT_ECH_DOMAIN,
        val fingerprint: String = AetherProfile.FINGERPRINT_CHROME,
        val finalMask: String = "",
        val dialMode: String = "",
    )

    /**
     * The run that registers the keys of [request] and ends: where the calls to the WARP API go, whether they hide
     * their server name with ECH, whether the ClientHello goes in pieces, and the cipher suites of the fingerprint.
     */
    fun keysArguments(request: KeysRequest, logLevel: String = DEFAULT_LOG_LEVEL): List<String> = buildList {
        addAll(listOf(REGISTER, request.kind))
        request.enrollAddress.trim().takeIf { it.isNotEmpty() && !it.startsWith('-') }?.let { addAll(listOf(API_ADDRESS, it)) }
        if (request.fragment) {
            add("--fragment")
            parseRange(request.fragmentSize, 1..4096)?.let { addAll(listOf("--fragment-size", rangeText(it))) }
            parseRange(request.fragmentDelay, 0..1000)?.let { addAll(listOf("--fragment-delay", rangeText(it))) }
        }
        if (request.ech) {
            addAll(listOf("--ech", "auto"))
            addAll(listOf("--ech-dns", settingValue(request.echDns) ?: AetherProfile.DEFAULT_ECH_DNS))
            addAll(listOf("--ech-domain", settingValue(request.echDomain) ?: AetherProfile.DEFAULT_ECH_DOMAIN))
        }
        addAll(fingerprintArguments(request.fingerprint))
        addAll(listOf(LOG_LEVEL, logLevel))
    }

    /** Why [profile] cannot run, as the rules of PattNG's editor say; null when it can. */
    enum class Problem { CARRIER_NEEDS_MASQUE, TOR_PSIPHON_CONFLICT, TOR_BRIDGES_MISSING }

    fun problemOf(profile: AetherProfile): Problem? {
        val tor = profile.tor
        val psiphon = profile.psiphon
        // Tor and Psiphon carry TCP alone and WARP's WireGuard endpoints answer on UDP: the core refuses the pair.
        // WireGuard over MASQUE goes, as its WireGuard rides inside the MASQUE tunnel.
        if ((tor == AetherProfile.CARRIER_REVERSE || psiphon == AetherProfile.CARRIER_REVERSE) &&
            !AetherProfile.overMasque(profile.protocol)
        ) {
            return Problem.CARRIER_NEEDS_MASQUE
        }
        // Both go together only nested, one inside the tunnel and the other around it.
        val nested = tor == AetherProfile.CARRIER_OFF || psiphon == AetherProfile.CARRIER_OFF ||
            (tor == AetherProfile.CARRIER_CHAIN && psiphon == AetherProfile.CARRIER_REVERSE) ||
            (tor == AetherProfile.CARRIER_REVERSE && psiphon == AetherProfile.CARRIER_CHAIN)
        if (!nested) return Problem.TOR_PSIPHON_CONFLICT
        if (tor != AetherProfile.CARRIER_OFF && profile.torBridges == AetherProfile.TOR_BRIDGES_OWN &&
            profile.torBridgeLines.lines().none { it.isNotBlank() }
        ) {
            return Problem.TOR_BRIDGES_MISSING
        }
        return null
    }
}
