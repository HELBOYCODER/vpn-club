package com.helboy.vpnclub.corex

import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import org.json.JSONObject

/**
 * پارسر لینک‌های اشتراک به مدل [ProxyConfig].
 * پشتیبانی: vless, trojan, hysteria2/hy2, vmess, ss (SIP002 + legacy), socks.
 *
 * اصل پونی‌تِیل: رشته‌ها را دستی باز و بسته می‌کنیم — بدون وابستگی خارجی.
 * هر فرمتی که نشناختیم، بی‌سروصدا رد می‌شود (null).
 */
object LinkParser {

    private val schemes = setOf("vless", "trojan", "hysteria2", "hy2", "vmess", "ss", "socks", "socks5")

    fun parse(link: String): ProxyConfig? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null
        val scheme = trimmed.substringBefore("://").lowercase()
        if (scheme !in schemes) return null
        return try {
            when (scheme) {
                "vmess" -> parseVmess(trimmed)
                "ss" -> parseSs(trimmed)
                "socks", "socks5" -> parseSocks(trimmed, scheme)
                else -> parseQueryStyle(trimmed, scheme)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** vless://uuid@host:port?k=v&k2=v2#tag — همچنین trojan و hysteria2 */
    private fun parseQueryStyle(link: String, scheme: String): ProxyConfig? {
        val uri = URI(link)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val uuid = uri.userInfo ?: ""
        val q = uri.rawQuery.orEmpty().split("&")
            .filter { it.contains('=') }
            .map { p ->
                val i = p.indexOf('=')
                URLDecoder.decode(p.take(i), "UTF-8") to URLDecoder.decode(p.substring(i + 1), "UTF-8")
            }
        val tag = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
        return ProxyConfig(
            scheme = scheme, host = host, port = port, uuidOrUser = uuid,
            query = q, tag = tag, raw = link
        )
    }

    /** vmess://base64(json) */
    private fun parseVmess(link: String): ProxyConfig? {
        val payloadB64 = link.removePrefix("vmess://")
        val json = String(Base64.getMimeDecoder().decode(payloadB64))
        val o = JSONObject(json)
        val host = o.optString("add").takeIf { it.isNotEmpty() } ?: return null
        val port = o.optString("port").toIntOrNull() ?: return null
        val q = buildList {
            o.optString("tls").takeIf { it.isNotEmpty() }?.let { add("tls" to it) }
            o.optString("sni").takeIf { it.isNotEmpty() }?.let { add("sni" to it) }
            o.optString("host").takeIf { it.isNotEmpty() }?.let { add("host" to it) }
            o.optString("path").takeIf { it.isNotEmpty() }?.let { add("path" to it) }
            o.optString("type").takeIf { it.isNotEmpty() }?.let { add("type" to it) }
            o.optString("scy").takeIf { it.isNotEmpty() }?.let { add("encryption" to it) }
            o.optString("alterId").takeIf { it.isNotEmpty() }?.let { add("alterId" to it) }
        }
        return ProxyConfig(
            scheme = "vmess", host = host, port = port,
            uuidOrUser = o.optString("id"), query = q,
            payload = payloadB64,
            tag = o.optString("ps"), raw = link
        )
    }

    /** ss://base64(method:pass)@host:port#tag — SIP002 و legacy */
    private fun parseSs(link: String): ProxyConfig? {
        val body = link.removePrefix("ss://")
        val frag = body.substringAfter('#', "")
        val tag = if (frag.isEmpty()) "" else URLDecoder.decode(frag, "UTF-8")
        val main = body.substringBefore('#')

        val (userinfo, hostport) = if (main.contains('@')) {
            val at = main.lastIndexOf('@')
            main.take(at) to main.substring(at + 1)
        } else {
            // legacy: ss://base64(method:pass@host:port)
            val decoded = String(Base64.getMimeDecoder().decode(main))
            val at = decoded.lastIndexOf('@')
            if (at < 0) return null
            decoded.take(at) to decoded.substring(at + 1)
        }

        val methodAndPass = try {
            String(Base64.getMimeDecoder().decode(userinfo))
        } catch (_: Exception) { userinfo }
        val method = methodAndPass.substringBefore(':')
        val pass = methodAndPass.substringAfter(':', "")

        val colon = hostport.lastIndexOf(':')
        if (colon < 0) return null
        val host = hostport.take(colon).trim('[', ']')
        val port = hostport.substring(colon + 1).toIntOrNull() ?: return null

        return ProxyConfig(
            scheme = "ss", host = host, port = port, uuidOrUser = pass,
            query = listOf("method" to method), tag = tag, raw = link
        )
    }

    private fun parseSocks(link: String, scheme: String): ProxyConfig? {
        val uri = URI(link)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        return ProxyConfig(
            scheme = scheme, host = host, port = port,
            uuidOrUser = uri.userInfo ?: "", tag = "", raw = link
        )
    }

    private fun Base64.getMimeDecoder() = Base64.getMimeDecoder()
}
