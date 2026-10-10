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
class XrayCore(private val socksPort: Int = 10808) {

    private var controller: CoreController? = null
    private val lock = Any()
    private val envInitialized = java.util.concurrent.atomic.AtomicBoolean(false)

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

    private val variantCache: Map<String, IranVariants.Variant> get() =
        activeVariant?.let { mapOf("" to it) } ?: emptyMap()
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
    private fun initCoreEnv() {
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
        val outbound = outboundFor(c)
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "socks-in")
                    .put("listen", "127.0.0.1")
                    .put("port", socksPort)
                    .put("protocol", "socks")
                    .put("settings", JSONObject()
                        .put("auth", "noauth")
                        .put("udp", true))
            ))
            .put("outbounds", JSONArray().put(outbound).put(
                JSONObject().put("tag", "direct").put("protocol", "freedom")
            ))
            .put("routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", JSONArray()
                    // ترافیک داخلی ایران از تونل خارج — کاهش بار و حفظ دسترسی به سرویس‌های داخلی
                    .put(JSONObject().put("type", "field")
                        .put("ip", JSONArray(listOf("geoip:ir")))
                        .put("outboundTag", "direct"))
                    .put(JSONObject().put("type", "field")
                        .put("domain", JSONArray(listOf("geosite:category-ir")))
                        .put("outboundTag", "direct"))))
        return root.toString()
    }

    private fun outboundFor(c: ProxyConfig): JSONObject {
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
                .put("encryption", "none")
            "vmess" -> settings.put("id", c.uuidOrUser)
                .put("alterId", (q0["alterId"] ?: "0").toIntOrNull() ?: 0)
                .put("security", q0["encryption"] ?: "auto")
            "trojan" -> settings.put("password", c.uuidOrUser)
            else -> settings.put("user", c.uuidOrUser)
        }

        val stream = JSONObject()
        val q = c.query.toMap()
        val net = q["type"] ?: q["network"] ?: "tcp"
        when (net) {
            "ws" -> stream.put("network", "ws").put("wsSettings", JSONObject()
                .put("path", q["path"] ?: "/")
                .put("headers", JSONObject().put("Host", q["host"] ?: c.sniHost)))
            "grpc" -> stream.put("network", "grpc").put("grpcSettings", JSONObject()
                .put("serviceName", q["serviceName"] ?: q["path"] ?: ""))
            "tcp" -> {
                val headerType = q["headerType"]
                if (headerType == "http") {
                    stream.put("network", "tcp").put("tcpSettings", JSONObject()
                        .put("header", JSONObject().put("type", "http")
                            .put("request", JSONObject()
                                .put("headers", JSONObject().put("Host", q["host"] ?: c.sniHost)))))
                } else {
                    stream.put("network", "tcp")
                }
            }
            else -> stream.put("network", net)
        }

        // TLS: همیشه با SNI واقعی (دامنه اصلی)، روی IP تزریق‌شده
        val tlsEnabled = q["tls"] == "tls" || q["security"] == "tls" || c.scheme == "trojan"
        if (tlsEnabled) {
            // پروفایل ایران — اگر این کانفیگ یک Variant داشت، تنظیماتش را اعمال کن
            val variantTag = c.tag
            val variant = variantCache[variantTag]
            val tls = JSONObject()
                .put("serverName", c.sniHost)
                .put("allowInsecure", q["allowInsecure"] == "1")
                .put("fingerprint", variant?.fingerprint ?: q["fp"] ?: "chrome")
                .put("alpn", JSONArray((variant?.alpn ?: q["alpn"] ?: "h2,http/1.1").split(",")))
            // ECH — روش همراه‌اول ۱
            val ech = variant?.echConfigList
            if (!ech.isNullOrBlank()) tls.put("echConfigList", ech)
            // cipherSuites — روش ایرانسل (semi-python)
            val cs = variant?.cipherSuites
            if (!cs.isNullOrBlank()) tls.put("cipherSuites", cs)
            stream.put("security", "tls").put("tlsSettings", tls)
        }
        val reality = q["security"] == "reality"
        if (reality) {
            stream.put("security", "reality").put("realitySettings", JSONObject()
                .put("serverName", c.sniHost)
                .put("fingerprint", q["fp"] ?: "chrome")
                .put("publicKey", q["pbk"] ?: "")
                .put("shortId", q["sid"] ?: "")
                .put("spiderX", q["spx"] ?: ""))
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

        // finalmask — تکه‌بندی داخل هسته (فورک patterniha/Xray-core) — روش F&F
        activeVariant?.finalMask?.let { stream.put("finalmask", it) }

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

    fun setAppContext(context: android.content.Context) {
        Seq.setContext(context.applicationContext)
        appContext = context.applicationContext
    }

    @Volatile private var appContext: android.content.Context? = null

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
