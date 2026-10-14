package com.helboy.vpnclub.corex

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import go.Seq

/**
 * پل به هسته‌ی Xray (libv2ray.aar از پروژه‌ی PattNG / AndroidLibXrayLite).
 *
 * هسته با یک کانفیگ JSON استاندارد Xray بالا می‌آید:
 *   - inbound: SOCKS5 روی 127.0.0.1 (پورت [socksPort])
 *   - outbound: پروتکل کانفیگ انتخابی
 *
 * تونل سراسری از VpnService + hev-socks5-tunnel (libhev-socks5-tunnel.so) تامین می‌شود
 * که ترافیک تون را به این SOCKS5 محلی می‌فرستد.
 */
class XrayCore(
    private val socksPort: Int = 10808,
    /** قوانین مسیریابی geoip/geosite فقط برای هسته‌ی سرویس. در هسته‌های تست بارگذاری geodata
     *  هر استارت را کند می‌کرد و با اجرای هم‌زمان core_fail می‌داد (لاگ واقعی کاربر). */
    private val useRouting: Boolean = false
) {
    val port: Int get() = socksPort


    private var controller: CoreController? = null
    private val lock = Any()

    private val callback = object : CoreCallbackHandler {
        override fun onEmitStatus(p0: Long, p1: String?): Long { return 0 }
        override fun shutdown(): Long { return 0 }
        override fun startup(): Long { return 0 }
    }

    /**
     * وقتی ست شود، هسته به‌جای آدرس واقعی کانفیگ به این آدرس وصل می‌شود
     * (پروکسی محلی تکه‌کننده‌ی ClientHello برای عبور از DPI).
     * قالب: "127.0.0.1:PORT"
     */
    @Volatile var dialOverride: String? = null

    /**
     * پروفایل ایرانِ فعال برای هر کانفیگ (کلید = tag کانفیگ).
     * [XrayCore.setVariant] قبل از [start] آن را ست می‌کند.
     */
    @Volatile private var activeVariant: IranVariants.Variant? = null

    /** ست‌کردن پروفایل ایران برای کانفیگ بعدی. */
    fun setVariant(v: IranVariants.Variant?) { activeVariant = v }

    private val effectiveVariant: IranVariants.Variant? get() = activeVariant

    /** شروع هسته برای یک کانفیگ. true = موفق */
    fun start(config: ProxyConfig): Boolean = synchronized(lock) {
        stop()
        try {
            initCoreEnv()
            val json = buildConfig(config)
            lastConfigJson = json
            val ctrl = Libv2ray.newCoreController(callback)
            ctrl.startLoop(json, 0)
            controller = ctrl
            // منتظر بالا آمدن SOCKS listener بمانیم (تا ۵ ثانیه)
            val deadline = System.currentTimeMillis() + 5_000
            while (!isRunning && System.currentTimeMillis() < deadline) Thread.sleep(100)
            isRunning
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            stop()
            false
        }
    }

    /** آخرین خطای هسته — برای دیباگ و نمایش وضعیت. */
    @Volatile var lastError: String = ""
        private set

    /** آخرین JSON کانفیگ — برای دیباگ. */
    @Volatile var lastConfigJson: String = ""
        private set

    /**
     * راه‌اندازی یک‌باره‌ی محیط هسته (الزام libv2ray):
     * کپی geoip.dat/geosite.dat از assets به دایرکتوری assets خصوصی + initCoreEnv.
     * بدون این، startLoop exception می‌دهد (مخصوصاً با قوانین geoip:ir).
     */
    internal fun initCoreEnvNow() = initCoreEnv()

    private fun initCoreEnv() {
        // یک‌بار در کل پردازه (تست‌ها و سرویس چند نمونه‌ی XrayCore می‌سازند)
        if (!envInitialized.compareAndSet(false, true)) return
        try {
            val ctx = appContext ?: return
            val assetDir = ctx.getDir("assets", android.content.Context.MODE_PRIVATE)
            for (name in listOf("geoip.dat", "geosite.dat")) {
                val f = java.io.File(assetDir, name)
                if (!f.exists() || f.length() == 0L) {
                    ctx.assets.open(name).use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    }
                }
            }
            val deviceId = deviceIdForXudp()
            Libv2ray.initCoreEnv(assetDir.absolutePath, deviceId)
        } catch (e: Exception) {
            envInitialized.set(false)
            lastError = "env: ${e.message}"
        }
    }

    fun stop() = synchronized(lock) {
        try { controller?.stopLoop() } catch (_: Exception) {}
        controller = null
    }

    val isRunning: Boolean get() = synchronized(lock) { controller?.getIsRunning() == true }

    /**
     * ساخت JSON کانفیگ Xray.
     *
     * نکته‌ی ضد DPI: آدرس اتصال (dial) = IP تزریق‌شده کلادفلر،
     * ولی serverName (SNI) = دامنه اصلی. این همان «رفتار پروتکلی پتنگ» برای عبور از
     * فیلترینگ SNI-based ایران است.
     */
    fun buildConfig(c: ProxyConfig): String {
        val outbound = outboundFor(c).put("tag", "proxy")
        val rp = routingPrefs()
        val prefs = if (useRouting) appContext?.let { AppSettings.of(it) } else null
        val resolver = prefs?.dnsServer?.takeIf { DnsEndpoint.isValid(it) } ?: "1.1.1.1"
        val coreDns = prefs != null
        val socksIn = JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject()
                .put("auth", "noauth")
                .put("udp", true))
        // قوانین دامنه‌ای (ایران مستقیم، لیست‌های کاربر) بدون sniff هرگز مطابقت نمی‌کنند — تون فقط IP می‌دهد.
        // routeOnly: مقصد عوض نمی‌شود، فقط برای مسیریابی خوانده می‌شود.
        if (useRouting) socksIn.put("sniffing", JSONObject().put("enabled", true)
            .put("destOverride", JSONArray(listOf("http", "tls", "quic"))).put("routeOnly", true))
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
             .put("inbounds", JSONArray().put(socksIn).also { ins ->
                if (prefs?.lanShare == true && prefs.lanPassword.length >= 12)
                    lanInbounds(prefs.lanPassword).forEach { ins.put(it) }
            })
            .put("outbounds", JSONArray().put(outbound).put(
                JSONObject().put("tag", "direct").put("protocol", "freedom")
            ).put(
                JSONObject().put("tag", "block").put("protocol", "blackhole")
            ).also { arr ->
                if (coreDns) arr.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
            })
            .put("routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", JSONArray().also { rules ->
                    fun rule(key: String, values: List<String>, out: String) {
                        if (values.isNotEmpty()) rules.put(JSONObject().put("type", "field")
                            .put(key, JSONArray(values)).put("outboundTag", out))
                    }
                    if (coreDns) {
                        rules.put(JSONObject().put("type", "field").put("network", "tcp,udp").put("port", "53").put("outboundTag", "dns-out"))
                    }
                    if (useRouting) {
                        // قوانین کاربر: مسدود > مستقیم (مثل پروفایل‌های مسیریابی ZeroNet)
                        val (bIp, bDom) = splitRules(rp.block)
                        rule("domain", bDom, "block"); rule("ip", bIp, "block")
                        val (dIp, dDom) = splitRules(rp.direct)
                        rule("domain", dDom, "direct"); rule("ip", dIp, "direct")
                        // ترافیک داخلی ایران از تونل خارج — حفظ دسترسی به سرویس‌های داخلی (قابل خاموش‌کردن)
                        if (rp.bypassIran) {
                            rule("ip", listOf("geoip:ir", "geoip:private"), "direct")
                            rule("domain", listOf("geosite:category-ir"), "direct")
                        }
                        val (pIp, pDom) = splitRules(rp.proxy)
                        rule("domain", pDom, "proxy"); rule("ip", pIp, "proxy")
                    }
                }))
        if (coreDns) root.put("dns", JSONObject()
            // از DNS انتخاب‌شده استفاده کن؛ شکست DoH نباید به DNS سیستم عقب‌نشینی کند.
            .put("servers", JSONArray(listOf(resolver)))
            .put("queryStrategy", "UseIPv4"))
        return root.toString()
    }

    internal fun lanInbounds(password: String): List<JSONObject> {
        require(password.length >= 12)
        fun inbound(protocol: String, port: Int): JSONObject = JSONObject()
            .put("tag", "lan-$protocol").put("listen", "0.0.0.0").put("port", port).put("protocol", protocol)
            .put("settings", JSONObject().put("accounts", JSONArray().put(
                JSONObject().put("user", "hellboy").put("pass", password))).also {
                    if (protocol == "socks") it.put("auth", "password").put("udp", false)
                })
            .put("sniffing", JSONObject().put("enabled", true).put("routeOnly", true)
                .put("destOverride", JSONArray(listOf("http", "tls"))))
        return listOf(inbound("socks", 10818), inbound("http", 10819))
    }

    /** تنظیمات مسیریابی کاربر (فقط هسته‌ی سرویس). */
    data class RoutingPrefs(val bypassIran: Boolean, val direct: String, val block: String, val proxy: String)

    private fun routingPrefs(): RoutingPrefs = try {
        appContext?.let { val s = AppSettings.of(it)
            RoutingPrefs(s.bypassIran, s.directRules, s.blockRules, s.proxyRules) }
            ?: RoutingPrefs(true, "", "", "")
    } catch (_: Exception) { RoutingPrefs(true, "", "", "") }

    /**
     * Hysteria2 (hy2://) روی هسته‌ی patterniha: پروتکل hysteria نسخه‌ی ۲، ترنسپورت hysteria روی QUIC،
     * obfs=salamander به‌صورت finalmask ی UDP، و pinSHA256/insecure با پین گواهی.
     */
    private fun hysteriaOutbound(c: ProxyConfig): JSONObject {
        val q = c.query.toMap()
        val host = dialOverride?.substringBefore(':') ?: c.dialHost
        val port = dialOverride?.substringAfter(':')?.toIntOrNull() ?: c.dialPort
        val tls = JSONObject().put("serverName", c.sniHost)
            .put("alpn", JSONArray((q["alpn"]?.ifBlank { null } ?: "h3").split(",").map { it.trim() }))
        pinFor(c, quic = true)?.let { tls.put("pinnedPeerCertSha256", it) }
        val stream = JSONObject().put("network", "hysteria")
            .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", c.uuidOrUser))
            .put("security", "tls").put("tlsSettings", tls)
        if (q["obfs"] == "salamander") stream.put("finalmask", JSONObject().put("udp", JSONArray().put(
            JSONObject().put("type", "salamander").put("settings", JSONObject().put("password", q["obfs-password"] ?: "")))))
        return JSONObject().put("protocol", "hysteria")
            .put("settings", JSONObject().put("version", 2).put("address", host).put("port", port))
            .put("streamSettings", stream)
    }

    /**
     * پین گواهی (جایگزین allowInsecure که هسته‌ی جدید حذف کرده — مثل v2rayNG):
     *  pcs / pinSHA256 خود لینک، وگرنه برای کانفیگ insecure گواهی سرور یک بار گرفته و پین می‌شود.
     */
    private fun pinFor(c: ProxyConfig, quic: Boolean): String? {
        val q = c.query.toMap()
        listOf(q["pcs"], q["pinSHA256"], q["pinsha256"]).firstNotNullOfOrNull { validPin(it) }?.let { return it }
        if (!isInsecure(q)) return null
        val host = dialOverride?.substringBefore(':') ?: c.dialHost
        val port = dialOverride?.substringAfter(':')?.toIntOrNull() ?: c.dialPort
        val key = "$host:$port/${c.sniHost}/$quic"
        pinCache[key]?.let { return it }
        return try {
            val req = JSONObject().put("address", host).put("port", port).put("serverName", c.sniHost).put("timeoutMs", 3_000).toString()
            val res = JSONObject(if (quic) Libv2ray.fetchQuicCertSha256(req) else Libv2ray.fetchTlsCertSha256(req))
            validPin(res.optString("sha256"))?.also { pinCache[key] = it }
                .also { DiagLogger.log("CERT_PIN ${c.host}:${c.port} quic=$quic -> ${if (it != null) "ok" else "fail ${res.optString("error").take(80)}"}") }
        } catch (e: Throwable) { DiagLogger.log("CERT_PIN ${c.host} err ${e.message?.take(80)}"); null }
    }

    private fun outboundFor(c: ProxyConfig): JSONObject {
        if (c.scheme == "hysteria2" || c.scheme == "hy2") return hysteriaOutbound(c)
        val q0 = c.query.toMap()
        val addr = dialOverride?.substringBefore(':') ?: c.dialHost
        val port = dialOverride?.substringAfter(':')?.toIntOrNull() ?: c.dialPort
        // فرمت فورک patterniha: settings تخت (address/port/id روی خود settings) — مثل CoreOutboundBuilder PattNG.
        // (فرمت استاندارد servers[] در این فورک parse نمی‌شود — باگ «config error» روی redroid)
        val settings = JSONObject()
            .put("address", addr)
            .put("port", port)
        when (c.scheme) {
            "vless" -> settings.put("id", c.uuidOrUser)
                .put("flow", q0["flow"] ?: "")
                // VLESS Encryption (mlkem768x25519plus…) از لینک؛ پیش‌فرض none
                .put("encryption", q0["encryption"]?.takeIf { it.startsWith("mlkem") } ?: "none")
            "vmess" -> settings.put("id", c.uuidOrUser)
                .put("alterId", (q0["alterId"] ?: "0").toIntOrNull() ?: 0)
                .put("security", q0["encryption"] ?: "auto")
            "trojan" -> settings.put("password", c.uuidOrUser)
            else -> settings.put("user", c.uuidOrUser)
        }

        val stream = JSONObject()
        // مقدار خالی (host= یا path=) مثل «نبودن» رفتار شود — ZeroNet 0.3.2: کانفیگ پنل‌ها با host خالی
        // پینگ می‌دادند ولی چیزی رد نمی‌کردند (Host خالی در درخواست WS)
        val q = c.query.toMap().filterValues { it.isNotBlank() }
        val net = q["type"] ?: q["network"] ?: "tcp"
        val hostHdr = q["host"] ?: c.sniHost
        when (net) {
            "ws" -> stream.put("network", "ws").put("wsSettings", JSONObject()
                .put("path", q["path"] ?: "/")
                .put("headers", JSONObject().put("Host", hostHdr)))
            "xhttp", "splithttp" -> {
                val xs = JSONObject()
                    .put("path", q["path"] ?: "/")
                    .put("host", hostHdr)
                    .put("mode", q["mode"] ?: "auto")
                q["extra"]?.let { ex -> runCatching { JSONObject(ex) }.getOrNull()?.let { xs.put("extra", it) } }
                stream.put("network", "xhttp").put("xhttpSettings", xs)
            }
            "httpupgrade" -> stream.put("network", "httpupgrade").put("httpupgradeSettings", JSONObject()
                .put("path", q["path"] ?: "/")
                .put("host", hostHdr))
            "grpc" -> stream.put("network", "grpc").put("grpcSettings", JSONObject()
                .put("serviceName", q["serviceName"] ?: q["path"] ?: "")
                .put("multiMode", q["mode"] == "multi")
                .also { g -> q["authority"]?.ifBlank { null }?.let { g.put("authority", it) } })
            "tcp" -> {
                val headerType = q["headerType"]
                if (headerType == "http") {
                    stream.put("network", "tcp").put("tcpSettings", JSONObject()
                        .put("header", JSONObject().put("type", "http")
                            .put("request", JSONObject()
                                .put("path", JSONArray((q["path"]?.ifBlank { null } ?: "/").split(",")))
                                .put("headers", JSONObject().put("Host", JSONArray((q["host"] ?: c.sniHost).split(",")))))))
                } else {
                    stream.put("network", "tcp")
                }
            }
            else -> stream.put("network", net)
        }

        // TLS: همیشه با SNI واقعی (دامنه اصلی)، روی IP تزریق‌شده
        val tlsEnabled = q["security"] != "reality" &&
            (q["tls"] == "tls" || q["security"] == "tls" || (c.scheme == "trojan" && q["security"] != "none"))
        if (tlsEnabled) {
            // پروفایل ایران — اگر این کانفیگ یک Variant داشت، تنظیماتش را اعمال کن
            val variant = effectiveVariant
            val tls = JSONObject()
                .put("serverName", c.sniHost)
                .put("fingerprint", variant?.fingerprint ?: q["fp"]?.ifEmpty { null } ?: "chrome")
                .put("alpn", JSONArray(alpnFor(net, variant?.alpn, q["alpn"])))
            // ECH — روش همراه‌اول ۱ (پروفایل) یا ech خود لینک
            val ech = variant?.echConfigList ?: q["ech"]
            if (!ech.isNullOrBlank()) tls.put("echConfigList", ech)
            // cipherSuites — پروفایل (semi-python / Netra) یا cs خود لینک. لینک Netra cs خودش را دارد و اولویت با آن است.
            // نکته: pcs در هسته‌ی جدید «pinnedPeerCertSha256» است، نه سوئیت رمز (قبلاً اشتباه اینجا می‌رفت)
            val cs = if (variant?.id == NETRA_VARIANT) q["cs"] ?: variant.cipherSuites else variant?.cipherSuites ?: q["cs"]
            if (!cs.isNullOrBlank()) tls.put("cipherSuites", cs)
            pinFor(c, quic = false)?.let { tls.put("pinnedPeerCertSha256", it) }
            q["vcn"]?.let { tls.put("verifyPeerCertByName", it) }
            stream.put("security", "tls").put("tlsSettings", tls)
        }
        val reality = q["security"] == "reality"
        if (reality) {
            stream.put("security", "reality").put("realitySettings", JSONObject()
                .put("serverName", c.sniHost)
                .put("fingerprint", q["fp"] ?: "chrome")
                .put("publicKey", q["pbk"] ?: "")
                .put("shortId", q["sid"] ?: "")
                .put("spiderX", q["spx"] ?: "")
                .also { r -> q["pqv"]?.ifBlank { null }?.let { r.put("mldsa65Verify", it) } })
        }

        val protocol = when (c.scheme) {
            "vless" -> "vless"
            "vmess" -> "vmess"
            "trojan" -> "trojan"
            "ss" -> "shadowsocks"
            "socks", "socks5" -> "socks"
            "hysteria2", "hy2" -> "hysteria2"
            else -> "vless"
        }

        // finalmask — تکه‌بندی داخل هسته (فورک patterniha/Xray-core) — روش F&F / Netra
        // پروفایل فعال اولویت دارد (به‌جز Netra که fm خود لینک را ترجیح می‌دهد)؛ وگرنه fm خود لینک (JSON)
        val linkFm = q["fm"]?.let { runCatching { JSONObject(it) }.getOrNull() }
        val fm = if (activeVariant?.id == NETRA_VARIANT) linkFm ?: activeVariant?.finalMask else activeVariant?.finalMask ?: linkFm
        fm?.let { stream.put("finalmask", it) }

        val out = JSONObject().put("protocol", protocol).put("settings", settings).put("streamSettings", stream)

        if (c.scheme == "ss") {
            // shadowsocks فرمت settings متفاوت دارد
            out.put("settings", JSONObject()
                .put("address", c.dialHost).put("port", c.dialPort)
                .put("method", c.query.toMap()["method"] ?: "aes-256-gcm")
                .put("password", c.uuidOrUser))
        }
        return out
    }

    /** ALPN: پروفایل ایران > لینک > پیش‌فرض (ws/httpupgrade فقط http/1.1). */
    private fun alpnFor(net: String, variantAlpn: String?, linkAlpn: String?): List<String> {
        // ws/httpupgrade با h2 کار نمی‌کنند (Upgrade فقط روی HTTP/1.1) — بعد از انتخاب اعمال می‌شود
        if (net == "ws" || net == "httpupgrade") return listOf("http/1.1")
        val chosen = variantAlpn?.takeIf { it.isNotBlank() } ?: linkAlpn?.takeIf { it.isNotBlank() }
        if (chosen != null) return chosen.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return listOf("h2", "http/1.1")
    }

    fun setAppContext(context: android.content.Context) {
        Seq.setContext(context.applicationContext)
        appContext = context.applicationContext
    }

    @Volatile private var appContext: android.content.Context? = null

    companion object {
        private val envInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

        /** آماده‌سازی محیط هسته (geo assets + initCoreEnv) — برای measureOutboundDelay بدون CoreController. */
        fun ensureEnv(ctx: android.content.Context) {
            if (envInitialized.get()) return
            XrayCore(0).also { it.setAppContext(ctx) }.initCoreEnvNow()
        }

        private val SS_METHODS = setOf(
            "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "chacha20-poly1305",
            "xchacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
            "2022-blake3-chacha20-poly1305")

        private val SUPPORTED_NETS = setOf("tcp", "ws", "grpc", "xhttp", "splithttp", "httpupgrade")

        /** آیا این کانفیگ را هسته‌ی ما می‌تواند اجرا کند؟ (hysteria2/kcp/quic/socks حذف) */
        /** گواهی‌های پین‌شده‌ی کانفیگ‌های insecure (host:port/sni/quic → sha256 hex). */
        const val NETRA_VARIANT = "netra-ff"

        private val pinCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        internal fun isInsecure(q: Map<String, String>) = listOf("allowInsecure", "insecure").any { q[it] == "1" || q[it] == "true" }

        /** فقط هش ۳۲ بایتی hex (با یا بدون «:») — بقیه نادیده (وگرنه هسته کل کانفیگ را رد می‌کند). */
        internal fun validPin(v: String?): String? = v?.split(",")?.map { it.trim().replace(":", "") }
            ?.filter { it.length == 64 && it.all { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' } }
            ?.takeIf { it.isNotEmpty() }?.joinToString(",")

        /**
         * لیست قوانین کاربر (هر خط یا با کاما) → (IP ها، دامنه‌ها) برای routing هسته.
         * «example.com» → domain:example.com ؛ geosite:/full:/regexp:/keyword:/domain: همان‌طور ؛
         * IP یا CIDR یا geoip: → لیست ip.
         */
        internal fun splitRules(text: String): Pair<List<String>, List<String>> {
            val items = text.lines().map { it.substringBefore('#') }.flatMap { it.split(',', ';', ' ', '\t') }
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val ipRe = Regex("^[0-9a-fA-F:.]+(/\\d{1,3})?$")
            val ips = items.filter { it.startsWith("geoip:") || (ipRe.matches(it) && (it.contains('.') || it.count { c -> c == ':' } >= 2)) }
            val doms = (items - ips.toSet()).map { d ->
                if (listOf("geosite:", "full:", "regexp:", "keyword:", "domain:", "ext:").any { d.startsWith(it) }) d
                else "domain:" + d.removePrefix("*.").removePrefix(".").lowercase()
            }
            return ips to doms
        }

        fun isSupported(c: ProxyConfig): Boolean {
            // Hysteria2: فقط پورت تکی (پورت‌هاپینگ «443,20000-30000» پشتیبانی نمی‌شود)
            if (c.scheme == "hysteria2" || c.scheme == "hy2") return c.uuidOrUser.isNotBlank() && c.port in 1..65535 &&
                (c.query.toMap()["obfs"].isNullOrBlank() || c.query.toMap()["obfs"] == "salamander")
            if (c.scheme !in setOf("vless", "vmess", "trojan", "ss")) return false
            if (c.uuidOrUser.isBlank()) return false
            val q = c.query.toMap()
            val net = q["type"]?.ifBlank { null } ?: q["network"]?.ifBlank { null } ?: "tcp"
            if (c.scheme != "ss" && net !in SUPPORTED_NETS) return false
            val sec = q["security"] ?: ""
            if (sec == "reality" && (q["pbk"].isNullOrBlank())) return false
            // allowInsecure: هسته‌ی جدید حذفش کرده؛ به‌جایش گواهی سرور هنگام ساخت کانفیگ پین می‌شود ([pinFor])
            if (c.scheme == "ss" && (q["method"] ?: "") !in SS_METHODS) return false
            return true
        }
    }

    /**
     * BaseKey ی XUDP — باید base64 بی‌پدینگِ یک بایت‌آرایه‌ی ۳۲ بیتی باشد
     * (دقیقاً مثل v2rayNG: ANDROID_ID → ۳۲ بایت → base64 URL_SAFE بدون padding).
     * UUID خالی → خطای "BaseKey must be ..." در هسته.
     */
    private fun deviceIdForXudp(): String = try {
        val ctx = appContext
        val androidId = if (ctx != null)
            android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                ?: "vpnclubfallback"
        else "vpnclubfallback"
        val bytes = androidId.toByteArray(Charsets.UTF_8).copyOf(32)
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    } catch (_: Exception) { "" }
}
