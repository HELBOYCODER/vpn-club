package com.helboy.vpnclub.ui.proto.editors2

import dev.valid7996.narcicgeteway.domain.config.AwgConfig
import dev.valid7996.narcicgeteway.domain.config.ConfigParser
import dev.valid7996.narcicgeteway.domain.config.EchOutbound
import dev.valid7996.narcicgeteway.domain.config.Protocol
import dev.valid7996.narcicgeteway.domain.config.SecurityConfig
import dev.valid7996.narcicgeteway.domain.config.ServerConfig
import dev.valid7996.narcicgeteway.domain.config.ShareLink
import dev.valid7996.narcicgeteway.domain.config.TransportConfig

/*
 * The form behind the manual editor for link profiles: every field of ServerConfig that a share link can carry,
 * as text, and the two-way mapping to ServerConfig. The link a form produces is always ShareLink.build of the
 * ServerConfig, and it is re-read by ConfigParser before it is called valid, so a saved link is one the engine reads.
 */

/** Everything a person can set on a link profile, as typed. All numbers are text until they are checked. */
data class LinkForm(
    val protocol: Protocol = Protocol.VLESS,
    val remark: String = "",
    val address: String = "",
    val port: String = "443",

    // credentials
    val userId: String = "",
    val alterId: String = "0",
    val encryption: String = "none",
    val vmessSecurity: String = "auto",
    val flow: String = "",
    val ssMethod: String = "aes-256-gcm",
    val username: String = "",

    // WireGuard / AmneziaWG
    val secretKey: String = "",
    val peerPublicKey: String = "",
    val presharedKey: String = "",
    val localAddress: String = "172.16.0.2/32",
    val allowedIps: String = "",
    val wgDns: String = "",
    val reserved: String = "",
    val wgMtu: String = "",
    val wgKeepalive: String = "",
    val awg: AwgConfig = AwgConfig(),

    // Hysteria
    val obfsPassword: String = "",
    val portHopping: String = "",
    val pinnedCert: String = "",
    val bandwidthUp: String = "",
    val bandwidthDown: String = "",

    // transport
    val network: String = "tcp",
    val headerType: String = "none",
    val host: String = "",
    val path: String = "",
    val seed: String = "",
    val streamMode: String = "",
    val serviceName: String = "",
    val authority: String = "",
    val xhttpExtra: String = "",
    val kcpMtu: String = "",
    val kcpTti: String = "",
    val finalMask: String = "",

    // security
    val security: String = "",
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val echConfigList: String = "",
    val echOutbound: String = "",
    val cipherSuites: String = "",
    val verifyCertName: String = "",
    val mldsa65Verify: String = "",
    val allowInsecure: Boolean = false,
)

/** What is wrong with a form, per field, and the link it produces when nothing is. */
data class FormCheck(
    val errors: Map<String, String>,
    val warnings: List<String>,
    val link: String?,
) {
    val valid: Boolean get() = errors.isEmpty() && link != null
}

object LinkFormRules {

    val NETWORKS = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp")
    val SECURITIES = listOf("" to "هیچ", "tls" to "TLS", "reality" to "REALITY")
    val FLOWS = listOf(
        "" to "هیچ",
        "xtls-rprx-vision" to "xtls-rprx-vision",
        "xtls-rprx-vision-udp443" to "xtls-rprx-vision-udp443",
    )
    val VMESS_SECURITIES = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")
    val SS_METHODS = listOf(
        "aes-256-gcm", "aes-128-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
        "xchacha20-poly1305", "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    )
    val FINGERPRINTS = listOf(
        "" to "هیچ", "chrome" to "chrome", "firefox" to "firefox", "safari" to "safari",
        "ios" to "ios", "android" to "android", "edge" to "edge", "360" to "360", "qq" to "qq",
        "random" to "random", "randomized" to "randomized", "unsafe" to "unsafe",
    )
    val TCP_HEADERS = listOf("none", "http")
    val KCP_HEADERS = listOf("none", "srtp", "utp", "wechat-video", "dtls", "wireguard", "dns")
    val GRPC_MODES = listOf("gun", "multi")
    val XHTTP_MODES = listOf("auto", "packet-up", "stream-up", "stream-one")
    val ALPNS = listOf(
        "" to "هیچ", "h3" to "h3", "h2" to "h2", "http/1.1" to "http/1.1",
        "h3,h2,http/1.1" to "h3,h2,http/1.1", "h3,h2" to "h3,h2", "h2,http/1.1" to "h2,http/1.1",
    )

    /** [options] with [current] added when it is not among them, so a value from an imported link is never hidden. */
    fun withCurrent(options: List<String>, current: String): List<String> =
        if (current.isEmpty() || current in options) options else options + current

    fun withCurrentPairs(options: List<Pair<String, String>>, current: String): List<Pair<String, String>> =
        if (options.any { it.first == current }) options else options + (current to current)

    fun defaultPort(protocol: Protocol): String = when (protocol) {
        Protocol.SOCKS -> "1080"
        Protocol.HTTP -> "80"
        Protocol.WIREGUARD, Protocol.AMNEZIAWG -> "51820"
        else -> "443"
    }

    /** The form for a new profile of [protocol]. */
    fun blank(protocol: Protocol = Protocol.VLESS): LinkForm = withProtocol(LinkForm(), protocol, creating = true)

    /** [form] switched to [protocol]; a new profile also gets that protocol's usual port and TLS default. */
    fun withProtocol(form: LinkForm, protocol: Protocol, creating: Boolean): LinkForm {
        var next = form.copy(protocol = protocol)
        if (creating) {
            next = next.copy(port = defaultPort(protocol))
            if ((protocol == Protocol.TROJAN || protocol == Protocol.HYSTERIA) && next.security.isBlank()) {
                next = next.copy(security = "tls")
            }
        }
        return next
    }

    fun fromConfig(c: ServerConfig): LinkForm = LinkForm(
        protocol = c.protocol,
        remark = c.remark,
        address = c.address,
        port = c.port.toString(),
        userId = c.userId,
        alterId = (c.alterId ?: 0).toString(),
        encryption = c.encryption,
        vmessSecurity = c.vmessSecurity,
        flow = c.flow.orEmpty(),
        ssMethod = c.shadowsocksMethod ?: "aes-256-gcm",
        username = c.username,
        secretKey = c.secretKey,
        peerPublicKey = c.peerPublicKey,
        presharedKey = c.preSharedKey.orEmpty(),
        localAddress = c.localAddresses.joinToString(","),
        allowedIps = c.allowedIps.joinToString(", "),
        wgDns = c.dnsServers.joinToString(", "),
        reserved = c.reserved.orEmpty(),
        wgMtu = c.wireguardMtu?.toString().orEmpty(),
        wgKeepalive = c.wireguardKeepalive?.toString().orEmpty(),
        awg = c.awg,
        obfsPassword = c.obfsPassword.orEmpty(),
        portHopping = c.portHopping.orEmpty(),
        pinnedCert = c.pinnedCertSha256.orEmpty(),
        bandwidthUp = c.bandwidthUp.orEmpty(),
        bandwidthDown = c.bandwidthDown.orEmpty(),
        network = c.transport.network,
        headerType = c.transport.headerType ?: "none",
        host = c.transport.host.orEmpty(),
        path = c.transport.path.orEmpty(),
        seed = c.transport.seed.orEmpty(),
        streamMode = c.transport.mode.orEmpty(),
        serviceName = c.transport.serviceName.orEmpty(),
        authority = c.transport.authority.orEmpty(),
        xhttpExtra = c.transport.xhttpExtra.orEmpty(),
        kcpMtu = c.transport.kcpMtu?.toString().orEmpty(),
        kcpTti = c.transport.kcpTti?.toString().orEmpty(),
        finalMask = c.transport.finalMask.orEmpty(),
        security = c.tls.security,
        sni = c.tls.sni.orEmpty(),
        alpn = c.tls.alpn.orEmpty(),
        // A link without a fingerprint stays without one; a new profile starts on chrome (see LinkForm).
        fingerprint = c.tls.fingerprint.orEmpty(),
        publicKey = c.tls.publicKey.orEmpty(),
        shortId = c.tls.shortId.orEmpty(),
        spiderX = c.tls.spiderX.orEmpty(),
        echConfigList = c.tls.echConfigList.orEmpty(),
        echOutbound = c.tls.echOutbound.orEmpty(),
        cipherSuites = c.tls.cipherSuites.orEmpty(),
        verifyCertName = c.tls.verifyPeerCertByName.orEmpty(),
        mldsa65Verify = c.tls.mldsa65Verify.orEmpty(),
        allowInsecure = c.tls.allowInsecure,
    )

    /** True when the AmneziaWG parameters beyond the classic ones are set (the advanced switch starts on). */
    fun hasAdvancedAwg(awg: AwgConfig): Boolean =
        AwgConfig.KEYS.any { it !in AwgConfig.CLASSIC_KEYS && awg[it].isNotBlank() }

    private fun nz(s: String): String? = s.trim().ifBlank { null }

    private fun list(s: String): List<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * The ServerConfig of [f]. [base] is the config the form was loaded from: what the form has no field for
     * (the legacy QUIC settings of a transport) is carried over from it.
     */
    fun toConfig(f: LinkForm, base: ServerConfig? = null): ServerConfig {
        val wg = f.protocol.isWireguardFamily
        val baseTransport = base?.transport ?: TransportConfig()
        val transport = if (f.protocol.supportsTransport) {
            baseTransport.copy(
                network = f.network,
                headerType = f.headerType.takeIf { it.isNotBlank() && it != "none" },
                host = nz(f.host),
                path = nz(f.path),
                seed = nz(f.seed),
                mode = nz(f.streamMode),
                serviceName = nz(f.serviceName),
                authority = nz(f.authority),
                xhttpExtra = nz(f.xhttpExtra),
                kcpMtu = f.kcpMtu.trim().toIntOrNull(),
                kcpTti = f.kcpTti.trim().toIntOrNull(),
                finalMask = nz(f.finalMask),
            )
        } else {
            baseTransport
        }
        return ServerConfig(
            protocol = f.protocol,
            remark = f.remark.trim().ifBlank { f.address.trim() },
            address = f.address.trim(),
            port = f.port.trim().toIntOrNull() ?: 443,
            userId = f.userId.trim(),
            alterId = if (f.protocol == Protocol.VMESS) f.alterId.trim().toIntOrNull() ?: 0 else base?.alterId,
            encryption = f.encryption.ifBlank { "none" },
            vmessSecurity = f.vmessSecurity.ifBlank { "auto" },
            flow = nz(f.flow),
            shadowsocksMethod = nz(f.ssMethod),
            transport = transport,
            tls = SecurityConfig(
                security = f.security,
                sni = nz(f.sni),
                fingerprint = nz(f.fingerprint),
                alpn = nz(f.alpn),
                publicKey = nz(f.publicKey),
                shortId = nz(f.shortId),
                spiderX = nz(f.spiderX),
                echConfigList = nz(f.echConfigList),
                echOutbound = nz(f.echOutbound),
                cipherSuites = nz(f.cipherSuites),
                verifyPeerCertByName = nz(f.verifyCertName),
                mldsa65Verify = nz(f.mldsa65Verify),
                allowInsecure = f.allowInsecure,
            ),
            username = f.username.trim(),
            secretKey = f.secretKey.trim(),
            peerPublicKey = f.peerPublicKey.trim(),
            preSharedKey = nz(f.presharedKey),
            reserved = nz(f.reserved),
            wireguardMtu = f.wgMtu.trim().toIntOrNull(),
            awg = if (f.protocol == Protocol.AMNEZIAWG) f.awg else AwgConfig(),
            obfsPassword = nz(f.obfsPassword),
            portHopping = nz(f.portHopping),
            pinnedCertSha256 = nz(f.pinnedCert),
            localAddresses = if (wg) list(f.localAddress) else emptyList(),
            dnsServers = if (wg) list(f.wgDns) else emptyList(),
            wireguardKeepalive = f.wgKeepalive.trim().toIntOrNull()?.takeIf { it > 0 },
            allowedIps = if (wg) list(f.allowedIps) else emptyList(),
            bandwidthUp = nz(f.bandwidthUp),
            bandwidthDown = nz(f.bandwidthDown),
        )
    }

    fun echErrorText(error: EchOutbound.Error): String = when (error) {
        EchOutbound.Error.INVALID_JSON -> "echOutbound باید یک شیء JSON باشد، به شکل { OutboundObject }."
        EchOutbound.Error.NEEDS_ECH_CONFIG_LIST -> "echOutbound بدون «لیست کانفیگ ECH» کار نمی‌کند؛ آن را هم پر کنید."
        EchOutbound.Error.INVALID_TAG ->
            "echOutbound یک «tag» می‌خواهد که خالی نباشد، direct یا block نباشد و با proxy شروع نشود."
    }

    const val INSECURE_NEEDS_SNI =
        "«اجازهٔ ناامن» در این هسته نمی‌تواند بررسی گواهی را رد کند؛ فقط گواهی را به‌جای آدرس با SNI تطبیق می‌دهد. " +
            "یک SNI بگذارید، یا SHA-256 گواهی را در فیلد گواهی پین‌شده بچسبانید."

    private fun notNumber(text: String): Boolean = text.isNotBlank() && text.trim().toIntOrNull() == null

    /**
     * Checks [f]: the errors per field (keyed "address", "port", "userId", "secretKey", "peerPublicKey", "alterId",
     * "wgMtu", "wgKeepalive", "kcpMtu", "kcpTti", "echOutbound", "link"), the non-blocking warnings, and the link.
     */
    fun check(f: LinkForm, base: ServerConfig? = null): FormCheck {
        val errors = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()

        if (f.address.isBlank()) errors["address"] = "آدرس سرور را وارد کنید."
        val port = f.port.trim().toIntOrNull()
        if (port == null || port !in 1..65535) errors["port"] = "پورت باید عددی از ۱ تا ۶۵۵۳۵ باشد."

        when (f.protocol) {
            Protocol.SOCKS, Protocol.HTTP -> Unit
            Protocol.WIREGUARD, Protocol.AMNEZIAWG -> {
                if (f.secretKey.isBlank()) errors["secretKey"] = "کلید خصوصی را وارد کنید."
                if (f.peerPublicKey.isBlank()) errors["peerPublicKey"] = "کلید عمومی همتا را وارد کنید."
                if (notNumber(f.wgMtu)) errors["wgMtu"] = "MTU باید عدد باشد."
                if (notNumber(f.wgKeepalive)) errors["wgKeepalive"] = "Keepalive باید عدد باشد."
            }
            Protocol.HYSTERIA -> if (f.secretKey.isBlank()) errors["secretKey"] = "رشتهٔ احراز را وارد کنید."
            Protocol.VLESS -> if (f.userId.isBlank()) errors["userId"] = "شناسه (UUID) را وارد کنید."
            Protocol.VMESS -> {
                if (f.userId.isBlank()) errors["userId"] = "شناسه (UUID) را وارد کنید."
                if (notNumber(f.alterId)) errors["alterId"] = "Alter ID باید عدد باشد."
            }
            Protocol.TROJAN, Protocol.SHADOWSOCKS -> if (f.userId.isBlank()) errors["userId"] = "رمز را وارد کنید."
        }

        if (f.protocol.supportsTransport && (f.network == "kcp" || f.network == "mkcp")) {
            if (notNumber(f.kcpMtu)) errors["kcpMtu"] = "MTU باید عدد باشد."
            if (notNumber(f.kcpTti)) errors["kcpTti"] = "TTI باید عدد باشد."
        }

        val tlsLike = f.security == "tls" || f.protocol == Protocol.HYSTERIA
        if (tlsLike && f.echOutbound.isNotBlank()) {
            EchOutbound.validate(f.echOutbound.trim(), f.echConfigList.trim())
                ?.let { errors["echOutbound"] = echErrorText(it) }
        }
        if (f.security == "tls" && f.allowInsecure && f.sni.isBlank() && f.pinnedCert.isBlank()) {
            warnings += INSECURE_NEEDS_SNI
        }

        var link: String? = null
        if (errors.isEmpty()) {
            try {
                val built = ShareLink.build(toConfig(f, base))
                ConfigParser.parse(built)
                link = built
            } catch (e: Exception) {
                errors["link"] = "لینک ساخته‌شده خوانده نمی‌شود: ${e.message?.take(100) ?: "دلیل نامشخص"}"
            }
        }
        return FormCheck(errors, warnings, link)
    }

    /** Whether a raw link text is one the engine reads as a link profile; null when it is, else the reason. */
    fun rawLinkProblem(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return "لینک را وارد کنید."
        return try {
            ConfigParser.parse(t)
            null
        } catch (e: Exception) {
            "این لینک خوانده نمی‌شود: ${e.message?.take(100) ?: "دلیل نامشخص"}"
        }
    }
}
