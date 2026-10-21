package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.DnsTunnelProfile
import dev.valid7996.narcicgeteway.domain.config.MasterDnsProfile

/** The DNS tunnel (DNSTT / VayDNS) form. Numbers stay text while they are typed. */
data class DnsTunnelForm(
    val name: String = "",
    val engine: String = DnsTunnelProfile.ENGINE_DNSTT,
    val domain: String = "",
    val publicKey: String = "",
    val transport: String = DnsTunnelProfile.TRANSPORT_UDP,
    val resolvers: String = "",
    val dohUrl: String = "",
    val resolverMode: String = DnsTunnelProfile.MODE_FANOUT,
    val spread: Int = 3,
    val authoritative: Boolean = false,
    val autoTune: Boolean = false,
    val payloadSize: Int = 0,
    val recordType: String = "txt",
    val qnameLen: Int = 101,
    val rps: String = "",
    val idleTimeout: String = "",
    val keepalive: String = "",
    val udpTimeout: String = "",
    val maxLabels: String = "",
    val clientIdSize: String = "",
    val socksUser: String = "",
    val socksPass: String = "",
    val sshEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUser: String = "",
    val sshAuth: String = DnsTunnelProfile.AUTH_PASSWORD,
    val sshPass: String = "",
    val sshKey: String = "",
    val sshKeyPass: String = "",
    val forwardDns: Boolean = false,
) {
    val isVaydns: Boolean get() = engine == DnsTunnelProfile.ENGINE_VAYDNS
    val isDoh: Boolean get() = transport == DnsTunnelProfile.TRANSPORT_DOH

    /** More than one resolver typed: only then does the resolver mode matter. */
    val multiResolver: Boolean get() = FormText.items(resolvers).size > 1

    fun check(): FormCheck = formCheck {
        val d = domain.trim()
        if (d.isEmpty()) error(F_DOMAIN, "دامنهٔ تونل را وارد کنید.")
        else if (FormText.hasWhitespace(d) || !d.contains('.')) error(F_DOMAIN, "دامنه معتبر نیست؛ مثل t.example.com.")
        if (publicKey.isBlank()) error(F_KEY, "کلید عمومی سرور را وارد کنید.")
        else if (FormText.hasWhitespace(publicKey)) error(F_KEY, "کلید نباید فاصله داشته باشد.")
        if (isDoh && dohUrl.isNotBlank() && !dohUrl.trim().startsWith("https://", ignoreCase = true)) {
            error(F_DOH, "آدرس DoH باید با https:// شروع شود.")
        }
        if (authoritative && !isVaydns) warn(F_AUTHORITATIVE, "فقط وقتی روشن کنید که رزالور سرور خودتان باشد.")
        decimalProblem(rps)?.let { error(F_RPS, it) }
        listOf(F_IDLE to idleTimeout, F_KEEPALIVE to keepalive, F_UDP to udpTimeout, F_LABELS to maxLabels, F_CLIENT_ID to clientIdSize)
            .forEach { (key, value) -> if (value.isNotBlank() && value.toIntOrNull() == null) error(key, "عدد صحیح وارد کنید.") }
        if (sshEnabled) {
            if (sshHost.isBlank()) error(F_SSH_HOST, "آدرس سرور SSH را وارد کنید.")
            if (FormText.port(sshPort) == null) error(F_SSH_PORT, "پورت باید عددی بین ۱ تا ۶۵۵۳۵ باشد.")
            if (sshAuth == DnsTunnelProfile.AUTH_KEY && sshKey.isBlank()) error(F_SSH_KEY, "کلید خصوصی SSH را وارد کنید.")
        }
    }

    private fun decimalProblem(text: String): String? =
        if (text.isBlank()) null else if ((text.toDoubleOrNull() ?: -1.0) < 0.0) "عدد معتبر وارد کنید." else null

    fun toProfile(): DnsTunnelProfile = DnsTunnelProfile(
        engine = engine,
        domain = domain.trim(),
        publicKey = publicKey.trim(),
        dnsTransport = transport,
        resolvers = FormText.items(resolvers).joinToString(","),
        dohUrl = dohUrl.trim(),
        resolverMode = resolverMode,
        rrSpreadCount = spread.coerceIn(1, 5),
        authoritative = authoritative,
        autoTune = autoTune,
        dnsPayloadSize = payloadSize,
        recordType = recordType,
        maxQnameLen = qnameLen.coerceIn(60, 253),
        rps = rps.toDoubleOrNull() ?: 0.0,
        idleTimeout = idleTimeout.toIntOrNull() ?: 0,
        keepalive = keepalive.toIntOrNull() ?: 0,
        udpTimeout = udpTimeout.toIntOrNull() ?: 0,
        maxNumLabels = maxLabels.toIntOrNull() ?: 0,
        clientIdSize = clientIdSize.toIntOrNull() ?: 0,
        socksUser = socksUser.trim(),
        socksPass = socksPass,
        sshEnabled = sshEnabled,
        sshHost = sshHost.trim(),
        sshPort = FormText.port(sshPort) ?: 22,
        sshUsername = sshUser.trim(),
        sshAuthType = sshAuth,
        sshPassword = sshPass,
        sshPrivateKey = sshKey.trim(),
        sshKeyPassphrase = sshKeyPass,
        forwardDnsThroughSsh = forwardDns,
    )

    companion object {
        const val F_DOMAIN = "domain"
        const val F_KEY = "key"
        const val F_DOH = "doh"
        const val F_AUTHORITATIVE = "authoritative"
        const val F_RPS = "rps"
        const val F_IDLE = "idle"
        const val F_KEEPALIVE = "keepalive"
        const val F_UDP = "udp"
        const val F_LABELS = "labels"
        const val F_CLIENT_ID = "clientId"
        const val F_SSH_HOST = "sshHost"
        const val F_SSH_PORT = "sshPort"
        const val F_SSH_KEY = "sshKey"

        fun from(p: DnsTunnelProfile?, name: String = ""): DnsTunnelForm {
            if (p == null) return DnsTunnelForm(name = name)
            fun opt(n: Int) = n.takeIf { it > 0 }?.toString() ?: ""
            return DnsTunnelForm(
                name = name,
                engine = p.engine,
                domain = p.domain,
                publicKey = p.publicKey,
                transport = p.dnsTransport,
                resolvers = p.resolvers.split(',').joinToString("\n") { it.trim() }.trim(),
                dohUrl = p.dohUrl,
                resolverMode = p.resolverMode,
                spread = p.rrSpreadCount,
                authoritative = p.authoritative,
                autoTune = p.autoTune,
                payloadSize = p.dnsPayloadSize,
                recordType = p.recordType,
                qnameLen = p.maxQnameLen,
                rps = p.rps.takeIf { it > 0 }?.toString() ?: "",
                idleTimeout = opt(p.idleTimeout),
                keepalive = opt(p.keepalive),
                udpTimeout = opt(p.udpTimeout),
                maxLabels = opt(p.maxNumLabels),
                clientIdSize = opt(p.clientIdSize),
                socksUser = p.socksUser,
                socksPass = p.socksPass,
                sshEnabled = p.sshEnabled,
                sshHost = p.sshHost,
                sshPort = p.sshPort.toString(),
                sshUser = p.sshUsername,
                sshAuth = p.sshAuthType,
                sshPass = p.sshPassword,
                sshKey = p.sshPrivateKey,
                sshKeyPass = p.sshKeyPassphrase,
                forwardDns = p.forwardDnsThroughSsh,
            )
        }
    }
}

/** The MasterDNS form. The local SOCKS port and the log level are not on screen; they travel through unchanged. */
data class MasterDnsForm(
    val name: String = "",
    val domains: String = "",
    val key: String = "",
    val method: Int = MasterDnsProfile.ENC_XOR,
    val resolvers: String = "",
    val balancing: Int = 3,
    val duplication: Int = 3,
    val compression: Int = 0,
    val autoDisable: Boolean = true,
    val keptListenPort: Int = 18000,
    val keptLogLevel: String = "INFO",
) {
    /** Resolver lines that are not IP addresses (the core ignores them). */
    val badResolvers: List<String> get() = MasterDnsProfile.invalidResolvers(resolvers)
    private val allResolversBad: Boolean
        get() = badResolvers.isNotEmpty() && badResolvers.size == MasterDnsProfile.splitResolvers(resolvers).size

    fun toProfile(): MasterDnsProfile = MasterDnsProfile(
        domains = domains.trim(),
        encryptionKey = key,
        encryptionMethod = method,
        resolvers = resolvers.trim(),
        listenPort = keptListenPort,
        balancingStrategy = balancing,
        packetDuplication = duplication.coerceIn(1, 10),
        compression = compression,
        autoDisableDeadResolvers = autoDisable,
        logLevel = keptLogLevel,
    )

    /** Mirrors the profile's own `isValid` (domains, key, resolvers) with a message per field. */
    fun check(): FormCheck = formCheck {
        val p = toProfile()
        if (p.domains.isBlank()) error(F_DOMAINS, "دامنهٔ تونل را وارد کنید.")
        if (p.encryptionKey.isBlank()) error(F_KEY, "کلید رمزنگاری را وارد کنید.")
        if (p.resolversText().isBlank()) error(F_RESOLVERS, "دست‌کم یک رزالور وارد کنید.")
        else if (allResolversBad) error(F_RESOLVERS, "رزالورها باید آدرس IP باشند نه نام دامنه.")
        else if (badResolvers.isNotEmpty()) warn(F_RESOLVERS, "این‌ها نادیده گرفته می‌شوند (آدرس IP نیستند): ${badResolvers.joinToString("، ")}")
    }

    companion object {
        const val F_DOMAINS = "domains"
        const val F_KEY = "key"
        const val F_RESOLVERS = "resolvers"

        val METHODS = listOf(
            MasterDnsProfile.ENC_NONE to "بدون رمزنگاری",
            MasterDnsProfile.ENC_XOR to "XOR",
            MasterDnsProfile.ENC_CHACHA20 to "ChaCha20",
            MasterDnsProfile.ENC_AES128 to "AES-128-GCM",
            MasterDnsProfile.ENC_AES192 to "AES-192-GCM",
            MasterDnsProfile.ENC_AES256 to "AES-256-GCM",
        )
        val BALANCING = listOf(
            1 to "تصادفی", 2 to "چرخشی", 3 to "کمترین اتلاف", 4 to "کمترین تأخیر", 5 to "ترکیبی",
            6 to "اتلاف سپس تأخیر", 7 to "کمترین اتلاف (تصادفی میان برترین‌ها)", 8 to "کمترین اتلاف (چرخشی میان برترین‌ها)",
        )
        val COMPRESSION = listOf(0 to "خاموش", 1 to "ZSTD", 2 to "LZ4", 3 to "ZLIB")

        fun from(p: MasterDnsProfile?, name: String = ""): MasterDnsForm =
            if (p == null) MasterDnsForm(name = name) else MasterDnsForm(
                name = name,
                domains = p.domains,
                key = p.encryptionKey,
                method = p.encryptionMethod,
                resolvers = p.resolvers,
                balancing = p.balancingStrategy,
                duplication = p.packetDuplication,
                compression = p.compression,
                autoDisable = p.autoDisableDeadResolvers,
                keptListenPort = p.listenPort,
                keptLogLevel = p.logLevel,
            )
    }
}
