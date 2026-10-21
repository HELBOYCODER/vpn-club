package com.helboy.vpnclub.corex

import java.net.URLEncoder
import java.util.Base64

/**
 * ساخت دوباره‌ی لینک اشتراک از روی مدل [ProxyConfig] با آدرس تزریق‌شده.
 *
 * قاعده: SNI و Host و ws-headers دست‌نخورده می‌مانند (TLS باید سرور اصلی را ببیند)،
 * فقط `address` و `port` تعویض می‌شوند. این همان کاری است که «کلاودفلر اینجکتور»
 * در اپ‌های معروف می‌کند: اتصال به IP پاک، هندشیک به نام دامنه.
 *
 * خروجی باید دوباره با [LinkParser] همان کانفیگ را بدهد (رفت‌وبرگشت) — تست: ShareLinkTest.
 */
object ShareLinkBuilder {

    fun build(c: ProxyConfig): String = when (c.scheme) {
        "vmess" -> buildVmess(c)
        "ss" -> buildSs(c)
        "socks", "socks5" -> buildSocks(c)
        else -> buildQueryStyle(c) // vless / trojan / hysteria2
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    /** IPv6 در لینک باید داخل [ ] باشد. */
    private fun hostPart(h: String): String = if (h.contains(':') && !h.startsWith("[")) "[$h]" else h

    private fun buildVmess(c: ProxyConfig): String {
        val q = c.query.toMap()
        // vmess payload is base64 JSON: {"add":..., "port":..., "ps":..., "v":"2", ...}
        val json = org.json.JSONObject().apply {
            put("v", "2")
            put("ps", c.tag)
            put("add", c.dialHost)
            put("port", c.dialPort.toString())
            put("id", c.uuidOrUser)
            put("aid", q["alterId"] ?: "0")
            put("net", q["type"] ?: "tcp")
            put("type", q["headerType"] ?: "none")
            put("scy", q["encryption"] ?: "auto")
            // host/tls stays pointing to the real server name
            q["sni"]?.let { put("sni", it) }
            q["host"]?.let { put("host", it) }
            q["path"]?.let { put("path", it) }
            q["tls"]?.let { put("tls", it) }
            q["alpn"]?.let { put("alpn", it) }
            q["fp"]?.let { put("fp", it) }
        }
        return "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
    }

    private fun buildQueryStyle(c: ProxyConfig): String {
        val qs = c.query.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        val uuidPart = if (c.uuidOrUser.isEmpty()) "" else enc(c.uuidOrUser) + "@"
        val frag = if (c.tag.isEmpty()) "" else "#" + enc(c.tag)
        val query = if (qs.isEmpty()) "" else "?$qs"
        return "${c.scheme}://$uuidPart${hostPart(c.dialHost)}:${c.dialPort}$query$frag"
    }

    private fun buildSs(c: ProxyConfig): String {
        // ss://base64url(method:pass)@host:port#tag  (SIP002)
        val method = c.query.toMap()["method"] ?: "aes-256-gcm"
        val userinfo = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$method:${c.uuidOrUser}".toByteArray())
        val frag = if (c.tag.isEmpty()) "" else "#" + enc(c.tag)
        return "ss://$userinfo@${hostPart(c.dialHost)}:${c.dialPort}$frag"
    }

    private fun buildSocks(c: ProxyConfig): String {
        val auth = if (c.uuidOrUser.isEmpty()) "" else "${c.uuidOrUser}@"
        return "${c.scheme}://$auth${hostPart(c.dialHost)}:${c.dialPort}"
    }
}
