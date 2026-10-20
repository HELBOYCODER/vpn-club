package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.Serializable

/**
 * The manual Aether profile, faithful to PattNG's Aether architecture.
 * Holds all settings for the Rust Aether core, its tunnel shapes, its WARP protocols,
 * and the Psiphon / Tor carriers embedded inside or around it.
 */
@Serializable
data class AetherProfile(
    val protocol: String = PROTO_WG,
    val server: String = "",
    val serverPort: Int = 0,
    val scanMode: String = SCAN_BALANCED,
    val transport: String = TRANSPORT_H3,
    val obfuscation: String = OBF_AUTO,
    val ipVersion: String = IP_V4,
    val dns: String = "",
    val exitLoc: String = "",
    val fingerprint: String = FINGERPRINT_CHROME,
    val fragment: Boolean = false,
    val fragmentSize: String = "",
    val fragmentDelay: String = "",
    val ech: Boolean = false,
    val echDns: String = "",
    val echDomain: String = "",
    val masqueSni: String = "",
    val wiwOuter: String = "",
    val wiwInner: String = "",
    val tor: String = CARRIER_OFF,
    val torBridges: String = TOR_BRIDGES_AUTO,
    val torBridgeLines: String = "",
    val torRelays: String = TOR_RELAYS_AUTO,
    val psiphon: String = CARRIER_OFF,
    val psiphonMode: String = PSIPHON_MODE_AUTO,
    val psiphonRegion: String = "",
    val psiphonCdnIps: String = "",
    val psiphonCdnSni: String = "",
    val psiphonCdnSets: String = "",
    val psiphonBundledList: Boolean = true,

    /** Beast mode of the Psiphon client: every protocol on every server at once. */
    val psiphonBeast: Boolean = true,
    val finalMask: String = "",
    val dialMode: String = "",
    val targetStrategy: String = "",
        val command: String = "",
    ) {
        val isTwoHops: Boolean get() = isTwoHops(protocol)
        val overMasque: Boolean get() = overMasque(protocol)

        fun endpointText(): String? {
            val host = server.trim()
            if (host.isEmpty() || serverPort !in 1..65535) return null
            return if (':' in host) "[$host]:$serverPort" else "$host:$serverPort"
        }

    companion object {
        const val PROTO_MASQUE = "masque"
        const val PROTO_WG = "wg"
        const val PROTO_GOOL = "gool"
        const val PROTO_MIM = "mim"
        const val PROTO_WG_OVER_MASQUE = "wg-over-masque"

        const val TRANSPORT_H3 = "h3"
        const val TRANSPORT_H2 = "h2"

        const val SCAN_TURBO = "turbo"
        const val SCAN_BALANCED = "balanced"
        const val SCAN_THOROUGH = "thorough"
        const val SCAN_VERIFIED = "verified"
        const val SCAN_IRONCLAD = "ironclad"

        const val OBF_AUTO = "auto"
        const val OBF_OFF = "off"
        const val OBF_LIGHT = "light"
        const val OBF_FIREWALL = "firewall"
        const val OBF_BALANCED = "balanced"
        const val OBF_GFW = "gfw"
        const val OBF_AGGRESSIVE = "aggressive"

        const val IP_V4 = "v4"
        const val IP_V6 = "v6"
        const val IP_DUAL = "both"

        const val FINGERPRINT_CHROME = "chrome"
        const val FINGERPRINT_FIREFOX = "firefox"
        const val FINGERPRINT_SEMI_PYTHON = "semi-python"
        const val FINGERPRINT_GO = "go"

        const val CARRIER_OFF = "off"
        const val CARRIER_CHAIN = "chain"
        const val CARRIER_REVERSE = "reverse"
        const val CARRIER_ONLY = "only"

        const val TOR_BRIDGES_AUTO = "auto"
        const val TOR_BRIDGES_FIRST = "first"
        const val TOR_BRIDGES_NEVER = "never"
        const val TOR_BRIDGES_OWN = "own"

        const val TOR_RELAYS_AUTO = "auto"
        const val TOR_RELAYS_ONLY = "only"
        const val TOR_RELAYS_OFF = "off"

        const val PSIPHON_MODE_AUTO = "auto"
        const val PSIPHON_MODE_CDN = "cdn"
        const val PSIPHON_MODE_DIRECT = "direct"

        const val DEFAULT_MASQUE_SNI = "www.cloudflare.com"
        const val DEFAULT_ECH_DNS = "udp://1.1.1.1"
        const val DEFAULT_ECH_DOMAIN = "cloudflare-ech.com"
        const val DEFAULT_ENROLL_ADDRESS = "api.cloudflareclient.com"

        /**
         * The protocols as PattNG's dropdown offers them: [first] is what a profile and a link hold,
         * [second] the word the core's --protocol takes for it.
         */
        val protocols = listOf(
            PROTO_MASQUE to "MASQUE",
            PROTO_WG to "WireGuard",
            PROTO_GOOL to "WARP-in-WARP",
            PROTO_MIM to "MASQUE-in-MASQUE",
            PROTO_WG_OVER_MASQUE to "WireGuard over MASQUE",
        )

        /** The word the core's --protocol takes for a profile protocol; gool serves wg-over-masque. */
        fun coreNameOf(protocol: String): String = when (protocol) {
            PROTO_WG_OVER_MASQUE -> "gool"
            else -> protocol
        }

        fun isTwoHops(protocol: String): Boolean =
            protocol == PROTO_GOOL || protocol == PROTO_MIM || protocol == PROTO_WG_OVER_MASQUE

        fun overMasque(protocol: String): Boolean =
            protocol == PROTO_MASQUE || protocol == PROTO_MIM || protocol == PROTO_WG_OVER_MASQUE

        /** Whether the two hops must be different addresses, as the core requires of WARP-in-WARP and MASQUE-in-MASQUE. */
        fun distinctHops(protocol: String): Boolean = protocol == PROTO_GOOL || protocol == PROTO_MIM

        val transports = listOf(
            TRANSPORT_H3 to "HTTP/3",
            TRANSPORT_H2 to "HTTP/2",
        )

        val scanModes = listOf(
            SCAN_TURBO to "Turbo",
            SCAN_BALANCED to "Balanced",
            SCAN_THOROUGH to "Thorough",
            SCAN_VERIFIED to "Verified",
            SCAN_IRONCLAD to "Ironclad",
        )

        val obfuscations = listOf(
            OBF_AUTO to "Auto",
            OBF_OFF to "Off",
            OBF_LIGHT to "Light",
            OBF_FIREWALL to "Firewall",
            OBF_BALANCED to "Balanced",
            OBF_GFW to "GFW",
            OBF_AGGRESSIVE to "Aggressive",
        )

        val ipVersions = listOf(
            IP_V4 to "IPv4",
            IP_V6 to "IPv6",
            IP_DUAL to "IPv4 + IPv6",
        )

        val fingerprints = listOf(
            FINGERPRINT_CHROME to "Chrome",
            FINGERPRINT_FIREFOX to "Firefox",
            FINGERPRINT_SEMI_PYTHON to "Semi-Python",
            FINGERPRINT_GO to "Go",
        )

        val carrierModes = listOf(
            CARRIER_OFF to "Off",
            CARRIER_CHAIN to "Chain",
            CARRIER_REVERSE to "Reverse",
            CARRIER_ONLY to "Only",
        )

        val torBridgeModes = listOf(
            TOR_BRIDGES_AUTO to "Auto",
            TOR_BRIDGES_FIRST to "First",
            TOR_BRIDGES_NEVER to "Never",
            TOR_BRIDGES_OWN to "Own",
        )

        val torRelayModes = listOf(
            TOR_RELAYS_AUTO to "Auto",
            TOR_RELAYS_ONLY to "Only",
            TOR_RELAYS_OFF to "Off",
        )

        val psiphonModes = listOf(
            PSIPHON_MODE_AUTO to "Auto",
            PSIPHON_MODE_CDN to "CDN",
            PSIPHON_MODE_DIRECT to "Direct",
        )

        val psiphonCdnSets = listOf(
            "cloudflare" to "Cloudflare",
            "fastly" to "Fastly",
            "cloudfront" to "CloudFront",
            "psiphon-akamai" to "Akamai",
            "psiphon-bunny" to "Bunny",
            "vercel" to "Vercel",
            "github" to "GitHub",
            "curated-fronting" to "Curated Fronting",
            "legacy-android-overrides" to "Legacy App Overrides",
        )

        val targetStrategies = listOf(
            "AsIs", "UseIP", "UseIPv4", "UseIPv6", "UseIPv4v6", "UseIPv6v4",
            "ForceIP", "ForceIPv4", "ForceIPv6", "ForceIPv4v6", "ForceIPv6v4",
        )

        val finalMaskPresets = listOf(
            "tlshello-0-len (0-104-1-0-0-114-1-1-11)" to """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","lengths":["0","104","1"],"delays":["0"],"maxSplit":"0"}},{"type":"fragment","settings":{"packets":"1-1","lengths":["114","1"],"delays":["1"],"maxSplit":"11"}}]}""",
            "tlshello (6-98-1-0-0-114-1-1-11)" to """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","lengths":["6","98","1"],"delays":["0"],"maxSplit":"0"}},{"type":"fragment","settings":{"packets":"1-1","lengths":["114","1"],"delays":["1"],"maxSplit":"11"}}]}""",
            "udp-noise (rnd-24-1200-1230)" to """{"udp":[{"type":"noise","settings":{"reset":"28","noise":[{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"},{"rand":"1200-1230","delay":"10"}]}}]}""",
        )

        val defaultEchDnsOptions = listOf(
            "udp://1.1.1.1",
            "udp://8.8.8.8",
            "https://1.1.1.1/dns-query@sni=www.microsoft.com",
            "https://doq.dns4all.eu/dns-query@address=194.0.5.3",
            "tcp://1.1.1.1",
            "tcp://8.8.8.8",
        )

        val defaultEchDomainOptions = listOf(
            "cloudflare-ech.com",
            "crypto.cloudflare.com",
            "ip.gs",
            "api.cloudflareclient.com",
            "consumer-masque.cloudflareclient.com",
            "consumer-masque-proxy.cloudflareclient.com",
        )

        /** The WARP key kinds the keys page registers, with the word the core's --register takes. */
        val keyKinds = listOf(
            "all" to "All",
            PROTO_WG to "WireGuard",
            PROTO_MASQUE to "MASQUE",
            "gool-classic" to "WARP-in-WARP",
            PROTO_MIM to "MASQUE-in-MASQUE",
            "gool" to "WireGuard over MASQUE",
        )

        /** The word the core's --register takes for a protocol's keys: gool serves wg-over-masque. */
        fun registerKindOf(protocol: String): String = when (protocol) {
            PROTO_GOOL -> "gool-classic"
            PROTO_WG_OVER_MASQUE -> "gool"
            else -> protocol
        }
    }
}
