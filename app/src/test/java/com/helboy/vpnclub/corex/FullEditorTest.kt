package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ویرایشگر کامل (گزینه‌های PattNG): تغییر شبکه/امنیت باید لینک سالم و تمیز بسازد. */
class FullEditorTest {
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private fun ok(r: ConfigEditor.Result) = (r as ConfigEditor.Result.Ok).config

    @Test fun switchWsToGrpcKeepsOnlyRelevantParams() {
        val c = LinkParser.parse("vless://$uuid@a.example.com:443?type=ws&security=tls&sni=a.example.com&host=a.example.com&path=%2Fws&eh=Sec-WebSocket-Protocol#A")!!
        val p = ConfigEditor.paramsOf(c).toMutableMap()
        p["type"] = "grpc"; p["serviceName"] = "svc"; p["mode"] = "multi"; p["fp"] = "firefox"
        val n = ok(ConfigEditor.applyFull(c, "A2", "a.example.com", "443", uuid, p))
        val q = n.query.toMap()
        assertEquals("grpc", q["type"]); assertEquals("svc", q["serviceName"]); assertEquals("multi", q["mode"])
        assertEquals("firefox", q["fp"]); assertEquals("a.example.com", q["sni"])
        assertFalse(q.containsKey("path")); assertFalse(q.containsKey("host"))
        assertEquals("Sec-WebSocket-Protocol", q["eh"]) // پارامتر ناشناخته حفظ شد
        assertEquals("A2", n.tag)
    }

    @Test fun realityFields() {
        val c = LinkParser.parse("vless://$uuid@r.example.com:443?type=tcp&security=tls&sni=r.example.com#R")!!
        val p = ConfigEditor.paramsOf(c).toMutableMap()
        p["security"] = "reality"; p["pbk"] = "PUBKEY"; p["sid"] = "ab12"; p["spx"] = "/"; p["flow"] = "xtls-rprx-vision"
        val n = ok(ConfigEditor.applyFull(c, "R", "r.example.com", "443", uuid, p))
        val q = n.query.toMap()
        assertEquals("reality", q["security"]); assertEquals("PUBKEY", q["pbk"]); assertEquals("ab12", q["sid"])
        assertEquals("xtls-rprx-vision", q["flow"]); assertFalse(q.containsKey("alpn"))
        assertTrue(XrayCore.isSupported(n))
    }

    @Test fun vmessTlsUsesTlsKeyAndSecurityMethod() {
        val c = ProxyConfig("vmess", "v.example.com", 443, uuid, listOf("type" to "ws", "path" to "/p"), tag = "V")
        val p = ConfigEditor.paramsOf(c).toMutableMap()
        p["security"] = "tls"; p["sni"] = "v.example.com"; p["encryption"] = "aes-128-gcm"
        val n = ok(ConfigEditor.applyFull(c, "V", "v.example.com", "443", uuid, p))
        val q = n.query.toMap()
        assertEquals("tls", q["tls"]); assertEquals("aes-128-gcm", q["encryption"]); assertEquals("/p", q["path"])
        assertEquals("v.example.com", q["sni"])
    }

    @Test fun rejectsBadJsonAndEmptySecret() {
        val c = LinkParser.parse("vless://$uuid@x.example.com:443?type=xhttp&security=tls#X")!!
        val p = ConfigEditor.paramsOf(c).toMutableMap()
        p["extra"] = "{not json"
        assertTrue(ConfigEditor.applyFull(c, "X", "x.example.com", "443", uuid, p) is ConfigEditor.Result.Invalid)
        assertTrue(ConfigEditor.applyFull(c, "X", "x.example.com", "443", "", ConfigEditor.paramsOf(c)) is ConfigEditor.Result.Invalid)
    }

    @Test fun trojanSecurityNoneIsNotForcedToTls() {
        val c = LinkParser.parse("trojan://pw@t.example.com:80?type=ws&security=none&path=%2F#T")!!
        assertEquals("none", ConfigEditor.securityOf(c))
    }

    @Test fun delayTestDoesNotUseCloudflareUrl() {
        // ورکرهای کلادفلر به IP کلادفلر وصل نمی‌شوند — تست باید با مقصد غیرکلادفلر باشد
        assertFalse(HellboyEngine.TEST_URL.contains("cloudflare"))
    }
}
