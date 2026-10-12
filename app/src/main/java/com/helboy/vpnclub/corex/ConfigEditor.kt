package com.helboy.vpnclub.corex

/**
 * ویرایش، کپی و وارد کردن کانفیگ‌ها.
 *
 * ویرایش فیلدی: نام، آدرس، پورت، SNI، Host، Path → لینک تازه ساخته و دوباره پارس می‌شود؛
 * اگر لینک حاصل پارس نشود یا هسته پشتیبانی نکند، تغییر رد می‌شود (کانفیگ خراب ذخیره نمی‌شود).
 */
object ConfigEditor {

    data class Fields(
        val name: String,
        val address: String,
        val port: String,
        val sni: String,
        val host: String,
        val path: String,
    )

    /** نتیجه‌ی ویرایش: کانفیگ تازه یا دلیل رد. */
    sealed interface Result {
        data class Ok(val config: ProxyConfig) : Result
        data class Invalid(val reason: String) : Result
    }

    fun fieldsOf(c: ProxyConfig): Fields {
        val q = c.query.toMap()
        return Fields(c.tag, c.host, c.port.toString(), q["sni"].orEmpty(), q["host"].orEmpty(), q["path"].orEmpty())
    }

    /** آیا این پروتکل فیلدهای SNI/Host/Path دارد؟ (ss و socks ندارند) */
    fun hasTransportFields(c: ProxyConfig) = c.scheme !in setOf("ss", "socks", "socks5")

    fun apply(c: ProxyConfig, f: Fields): Result {
        val address = f.address.trim().trim('[', ']')
        if (address.isEmpty() || address.any { it.isWhitespace() }) return Result.Invalid("آدرس نامعتبر است")
        val port = f.port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return Result.Invalid("پورت باید بین ۱ تا ۶۵۵۳۵ باشد")
        var q = c.query
        if (hasTransportFields(c)) {
            q = setKey(q, "sni", f.sni.trim())
            q = setKey(q, "host", f.host.trim())
            q = setKey(q, "path", f.path.trim())
        }
        val draft = ProxyConfig(scheme = c.scheme, host = address, port = port, uuidOrUser = c.uuidOrUser,
            query = q, tag = f.name.trim())
        return fromLink(ShareLinkBuilder.build(draft))
    }

    // ───────────── ویرایش کامل (همه‌ی گزینه‌های PattNG) ─────────────

    /** یک فیلد قابل‌ویرایش: کلید پارامتر لینک، برچسب، گزینه‌های پیشنهادی (خالی = متن آزاد). */
    data class Spec(val key: String, val label: String, val options: List<String> = emptyList(), val hint: String = "")

    val FLOWS = listOf("", "xtls-rprx-vision", "xtls-rprx-vision-udp443")
    val NETWORKS = listOf("tcp", "ws", "httpupgrade", "xhttp", "grpc")
    val SECURITIES = listOf("none", "tls", "reality")
    val FINGERPRINTS = listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized", "unsafe")
    val ALPNS = listOf("", "h3", "h2", "http/1.1", "h3,h2,http/1.1", "h3,h2", "h2,http/1.1")
    val VMESS_SECURITY = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero")
    val SS_METHODS = listOf("aes-256-gcm", "aes-128-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305",
        "xchacha20-poly1305", "xchacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305")
    val GRPC_MODES = listOf("gun", "multi", "guna")
    val XHTTP_MODES = listOf("auto", "packet-up", "stream-up", "stream-one")
    val TCP_HEADERS = listOf("none", "http")

    /** فیلدهای پروتکل (بخش «سرور»). */
    fun protocolSpecs(scheme: String): List<Spec> = when (scheme) {
        "vless" -> listOf(Spec("flow", "Flow", FLOWS), Spec("encryption", "Encryption", listOf("none"), "none یا mlkem768x25519plus…"))
        "vmess" -> listOf(Spec("alterId", "AlterId", listOf("0")), Spec("encryption", "Security (رمزنگاری)", VMESS_SECURITY))
        "ss" -> listOf(Spec("method", "Method (روش رمزنگاری)", SS_METHODS))
        else -> emptyList()
    }

    /** فیلدهای ترنسپورت بر اساس نوع شبکه. */
    fun transportSpecs(net: String): List<Spec> = when (net) {
        "tcp" -> listOf(Spec("headerType", "Header type", TCP_HEADERS), Spec("host", "Host (برای header=http)"), Spec("path", "Path (برای header=http)"))
        "ws", "httpupgrade" -> listOf(Spec("host", "Host"), Spec("path", "Path", hint = "/ws?ed=2560"))
        "xhttp", "splithttp" -> listOf(Spec("host", "Host"), Spec("path", "Path"), Spec("mode", "XHTTP mode", XHTTP_MODES),
            Spec("extra", "XHTTP extra (JSON)", hint = "{\"xPaddingBytes\":\"100-1000\"}"))
        "grpc" -> listOf(Spec("serviceName", "Service name"), Spec("mode", "gRPC mode", GRPC_MODES), Spec("authority", "Authority"))
        else -> emptyList()
    }

    /** فیلدهای امنیت (TLS / Reality) + فرگمنت داخلی PattNG. */
    fun securitySpecs(sec: String): List<Spec> = when (sec) {
        "tls" -> listOf(Spec("sni", "SNI"), Spec("fp", "Fingerprint (uTLS)", FINGERPRINTS), Spec("alpn", "ALPN", ALPNS),
            Spec("ech", "ECH config list", hint = "AEX+DQBB…"), Spec("pcs", "Cipher suites", hint = "TLS_AES_128_GCM_SHA256:…"))
        "reality" -> listOf(Spec("sni", "SNI"), Spec("fp", "Fingerprint (uTLS)", FINGERPRINTS), Spec("pbk", "PublicKey"),
            Spec("sid", "ShortId"), Spec("spx", "SpiderX"), Spec("pqv", "mldsa65Verify"))
        else -> emptyList()
    }

    val FINALMASK = Spec("fm", "FinalMask (فرگمنت داخلی PattNG — JSON)", hint = "{\"tcp\":[{\"type\":\"fragment\",…}]}")

    /** امنیت فعلی کانفیگ (vmess کلید «tls» دارد، بقیه «security»؛ trojan پیش‌فرض tls). */
    fun securityOf(c: ProxyConfig): String {
        val q = c.query.toMap()
        return q["security"]?.ifBlank { null } ?: if (q["tls"] == "tls" || c.scheme == "trojan") "tls" else "none"
    }

    fun networkOf(c: ProxyConfig): String { val q = c.query.toMap(); return q["type"] ?: q["network"] ?: "tcp" }

    /** همه‌ی مقادیر فعلی پارامترها برای پر کردن فرم. */
    fun paramsOf(c: ProxyConfig): Map<String, String> = c.query.toMap() + mapOf("type" to networkOf(c), "security" to securityOf(c))

    /**
     * ویرایش کامل: نام/آدرس/پورت/UUID-رمز + هر پارامتر لینک. مقدار خالی = حذف پارامتر.
     * پارامترهایی که به شبکه/امنیت انتخاب‌شده مربوط نیستند حذف می‌شوند تا لینک تمیز بماند.
     */
    fun applyFull(c: ProxyConfig, name: String, address: String, port: String, secret: String, params: Map<String, String>): Result {
        val addr = address.trim().trim('[', ']')
        if (addr.isEmpty() || addr.any { it.isWhitespace() }) return Result.Invalid("آدرس نامعتبر است")
        val p = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return Result.Invalid("پورت باید بین ۱ تا ۶۵۵۳۵ باشد")
        if (secret.isBlank()) return Result.Invalid(if (c.scheme == "vless" || c.scheme == "vmess") "UUID خالی است" else "رمز خالی است")
        params["extra"]?.takeIf { it.isNotBlank() }?.let { if (runCatching { org.json.JSONObject(it) }.isFailure) return Result.Invalid("XHTTP extra باید JSON معتبر باشد") }
        params["fm"]?.takeIf { it.isNotBlank() }?.let { if (runCatching { org.json.JSONObject(it) }.isFailure) return Result.Invalid("FinalMask باید JSON معتبر باشد") }

        val net = params["type"]?.ifBlank { null } ?: "tcp"
        val sec = params["security"]?.ifBlank { null } ?: "none"
        val allowed = buildSet {
            protocolSpecs(c.scheme).forEach { add(it.key) }
            if (hasTransportFields(c)) {
                add("type"); transportSpecs(net).forEach { add(it.key) }; securitySpecs(sec).forEach { add(it.key) }; add("fm")
                if (c.scheme != "vmess") add("security")
            }
        }
        // ترتیب پارامترهای قبلی حفظ می‌شود؛ پارامترهای ناشناخته‌ی لینک (مثل eh/ed) دست نمی‌خورند
        val managed = (protocolSpecs("vless") + protocolSpecs("vmess") + protocolSpecs("ss") +
            NETWORKS.flatMap { transportSpecs(it) } + SECURITIES.flatMap { securitySpecs(it) } + FINALMASK).map { it.key }.toSet() +
            setOf("type", "network", "security", "tls")
        var q = c.query.filterNot { it.first in managed }
        val order = listOf("type", "security") + protocolSpecs(c.scheme).map { it.key } +
            transportSpecs(net).map { it.key } + securitySpecs(sec).map { it.key } + "fm"
        for (k in order.distinct()) {
            if (k !in allowed) continue
            val v = params[k]?.trim().orEmpty()
            if (v.isNotEmpty()) q = q + (k to v)
        }
        if (c.scheme == "vmess" && sec == "tls") q = q + ("tls" to "tls")
        if (c.scheme == "vmess" && sec == "reality") return Result.Invalid("VMess با Reality کار نمی‌کند")
        val draft = ProxyConfig(scheme = c.scheme, host = addr, port = p, uuidOrUser = secret.trim(), query = q, tag = name.trim())
        return fromLink(ShareLinkBuilder.build(draft))
    }

    /** ویرایش متن خام لینک. */
    fun fromLink(link: String): Result {
        val cfg = LinkParser.parse(link.trim()) ?: return Result.Invalid("لینک قابل خواندن نیست")
        if (!XrayCore.isSupported(cfg)) return Result.Invalid("این نوع کانفیگ پشتیبانی نمی‌شود (مثلاً allowInsecure یا ss قدیمی)")
        return Result.Ok(cfg)
    }

    /** وارد کردن چند لینک (هر خط یک لینک، یا ساب base64). */
    fun parseMany(text: String): List<ProxyConfig> {
        val body = text.trim()
        val lines = if (body.contains("://")) body.lines()
        else runCatching { String(java.util.Base64.getMimeDecoder().decode(body)).lines() }.getOrDefault(emptyList())
        return lines.mapNotNull { LinkParser.parse(it.trim()) }.distinctBy { it.raw }
    }

    /** لینک قابل اشتراک: با IP تمیز تزریق‌شده (اگر هست) تا دیگران هم مستقیم وصل شوند. */
    fun shareLink(e: ServerEntry): String = e.config.toShareLink()

    /** تنظیم/حذف یک کلید پرس‌وجو با حفظ ترتیب؛ مقدار خالی = حذف. */
    internal fun setKey(q: List<Pair<String, String>>, key: String, value: String): List<Pair<String, String>> {
        val has = q.any { it.first.equals(key, true) }
        return when {
            value.isEmpty() -> q.filterNot { it.first.equals(key, true) }
            has -> q.map { if (it.first.equals(key, true)) it.first to value else it }
            else -> q + (key to value)
        }
    }
}
