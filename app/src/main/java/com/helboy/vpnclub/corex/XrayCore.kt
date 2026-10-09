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

    /** شروع هسته برای یک کانفیگ. true = موفق */
    fun start(config: ProxyConfig): Boolean = synchronized(lock) {
        stop()
        try {
            val json = buildConfig(config)
            val ctrl = Libv2ray.newCoreController(callback)
            ctrl.startLoop(json, 0)
            controller = ctrl
            true
        } catch (e: Exception) {
            stop()
            false
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
        val settings = JSONObject().put("servers", JSONArray().put(
            JSONObject()
                // dialOverride: عبور از DPI — اتصال از طریق پروکسی محلی تکه‌کننده
                .put("address", dialOverride?.substringBefore(':') ?: c.dialHost)
                .put("port", dialOverride?.substringAfter(':')?.toIntOrNull() ?: c.dialPort)
                .let { o ->
                    if (c.uuidOrUser.isNotEmpty()) o.put("users", JSONArray().put(
                        when (c.scheme) {
                            "vless" -> JSONObject()
                                .put("id", c.uuidOrUser)
                                .put("flow", c.query.firstOrNull { it.first == "flow" }?.second ?: "")
                                .put("encryption", "none")
                            "vmess" -> JSONObject()
                                .put("id", c.uuidOrUser)
                                .put("alterId", (c.query.firstOrNull { it.first == "alterId" }?.second ?: "0").toIntOrNull() ?: 0)
                                .put("security", c.query.firstOrNull { it.first == "encryption" }?.second ?: "auto")
                            "trojan" -> JSONObject().put("password", c.uuidOrUser)
                            else -> JSONObject().put("user", c.uuidOrUser)
                        }
                    )) else o
                }
        ))

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
            stream.put("security", "tls").put("tlsSettings", JSONObject()
                .put("serverName", c.sniHost)
                .put("allowInsecure", q["allowInsecure"] == "1")
                .put("fingerprint", q["fp"] ?: "chrome")
                .put("alpn", JSONArray((q["alpn"] ?: "h2,http/1.1").split(","))))
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

        val out = JSONObject().put("protocol", protocol).put("settings", settings).put("streamSettings", stream)

        if (c.scheme == "ss") {
            // shadowsocks فرمت settings متفاوت دارد
            out.put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject()
                    .put("address", c.dialHost).put("port", c.dialPort)
                    .put("method", c.query.toMap()["method"] ?: "aes-256-gcm")
                    .put("password", c.uuidOrUser))))
        }
        return out
    }

    fun setAppContext(context: android.content.Context) {
        Seq.setContext(context.applicationContext)
    }
}
