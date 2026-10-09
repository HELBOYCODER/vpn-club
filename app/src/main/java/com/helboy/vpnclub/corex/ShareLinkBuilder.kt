package com.helboy.vpnclub.corex

/**
 * ساخت دوباره‌ی لینک اشتراک از روی مدل [ProxyConfig] با آدرس تزریق‌شده.
 *
 * قاعده: SNI و Host و ws-headers دست‌نخورده می‌مانند (TLS باید سرور اصلی را ببیند)،
 * فقط `address` و `port` تعویض می‌شوند. این همان کاری است که «کلاودفلر اینجکتور»
 * در اپ‌های معروف می‌کند: اتصال به IP پاک، هندشیک به نام دامنه.
 */
object ShareLinkBuilder {

    fun build(c: ProxyConfig): String = when (c.scheme) {
        "vmess" -> buildVmess(c)
        "ss" -> buildSs(c)
        "socks" -> buildSocks(c)
        else -> buildQueryStyle(c) // vless / trojan / hysteria2
    }

    private fun buildVmess(c: ProxyConfig): String {
        // vmess payload is base64 JSON: {"add":..., "port":..., "ps":..., "v":"2", ...}
        val json = org.json.JSONObject().apply {
            put("v", "2")
            put("ps", c.tag.ifEmpty { "inj" })
            put("add", c.dialHost)
            put("port", c.dialPort.toString())
            put("id", c.uuidOrUser)
            put("aid", queryOf(c)["alterId"] ?: "0")
            put("net", queryOf(c)["type"] ?: "tcp")
            put("scy", queryOf(c)["encryption"] ?: "auto")
            // host/tls stays pointing to the real server name
            queryOf(c)["sni"]?.let { put("sni", it) }
            queryOf(c)["host"]?.let { put("host", it) }
            queryOf(c)["path"]?.let { put("path", it) }
            queryOf(c)["tls"]?.let { put("tls", it) }
        }
        return "vmess://" + android.util.Base64.encodeToString(
            json.toString().toByteArray(), android.util.Base64.NO_WRAP
        )
    }

    private fun buildQueryStyle(c: ProxyConfig): String {
        val qs = c.query.joinToString("&") { (k, v) ->
            k + "=" + java.net.URLEncoder.encode(v, "UTF-8")
        }
        val uuidPart = if (c.uuidOrUser.isEmpty()) "" else c.uuidOrUser + "@"
        val frag = if (c.tag.isEmpty()) "" else "#" + java.net.URLEncoder.encode(c.tag, "UTF-8")
        return "${c.scheme}://$uuidPart${c.dialHost}:${c.dialPort}?$qs$frag"
    }

    private fun buildSs(c: ProxyConfig): String {
        // ss://base64(method:pass)@host:port#tag
        val userinfo = android.util.Base64.encodeToString(
            "${queryOf(c)["method"] ?: "aes-256-gcm"}:${c.uuidOrUser}".toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
        )
        val frag = if (c.tag.isEmpty()) "" else "#" + java.net.URLEncoder.encode(c.tag, "UTF-8")
        return "ss://$userinfo@${c.dialHost}:${c.dialPort}$frag"
    }

    private fun buildSocks(c: ProxyConfig): String {
        val auth = if (c.uuidOrUser.isEmpty()) "" else "$c.uuidOrUser@"
        return "socks://$auth${c.dialHost}:${c.dialPort}"
    }

    private fun queryOf(c: ProxyConfig): Map<String, String> = c.query.toMap()
}
