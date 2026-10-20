package dev.valid7996.narcicgeteway.domain.config

/**
 * The routing front of an Aether session. Aether's core serves every connection it is given through its tunnel, and
 * the routing settings (rule sets, bypass of a country, ad blocking, the proxy / direct / block of a domain or an
 * address) are rules of Xray: they only ever applied to Xray profiles. With a front, the tun hands its connections to
 * an Xray that applies the rules, and the connections the rules leave on "proxy" go on to the core's SOCKS listener,
 * the others go direct or are dropped. Without rules the front is left out: it would only add a hop.
 */
object AetherFront {

    /** True when the settings route something: the front is only worth its hop then. */
    fun needed(options: XrayJsonBuilder.BuildOptions): Boolean =
        options.rulesets.any { it.enabled } ||
            options.bypassIran ||
            (options.blockAds && options.geoAssetsAvailable) ||
            options.customProxyRules.isNotEmpty() ||
            options.customDirectRules.isNotEmpty() ||
            options.customBlockRules.isNotEmpty()

    /**
     * The Xray configuration of the front: its SOCKS inbound on [socksPort] for the tun, the routing of [options], a
     * "proxy" outbound that is the core's listener on [LocalPorts.AETHER_SOCKS], and the exit the core dials out through.
     */
    fun config(
        profile: AetherProfile,
        options: XrayJsonBuilder.BuildOptions,
        socksPort: Int = LocalProxy.SOCKS_PORT,
    ): String {
        val core = ServerConfig(
            protocol = Protocol.SOCKS,
            remark = "Aether",
            address = "127.0.0.1",
            port = LocalPorts.AETHER_SOCKS,
        )
        // The core's listener is on this device: what a link to a server needs (fragments, mux, a carrier, a pinned
        // address of the server) has no meaning for it.
        val local = options.copy(
            carrier = false,
            fragmentEnabled = false,
            muxEnabled = false,
            dialerSocksPort = null,
            serverDns = emptyList(),
            resolvedServerHosts = emptyMap(),
        )
        val front = XrayJsonBuilder.build(core, socksPort, local)
        return AetherExitJson.mergeExit(front, profile.finalMask, profile.dialMode, profile.targetStrategy)
    }
}
