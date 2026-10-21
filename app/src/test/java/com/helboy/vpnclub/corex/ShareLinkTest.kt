package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** رفت‌وبرگشت لینک (ساخت ← پارس) و ویرایش کانفیگ؛ اگر بشکند، کپی/QR/ویرایش کانفیگ خراب می‌دهد. */
class ShareLinkTest {

    private val uuid = "11111111-2222-3333-4444-555555555555"

    @Test fun vlessRoundTripWithInjectKeepsSni() {
        val c = LinkParser.parse("vless://$uuid@cdn.example.com:443?type=ws&security=tls&sni=cdn.example.com&host=cdn.example.com&path=%2Fws%3Fed%3D2048#My%20Server")!!
        val inj = c.withDial("104.16.1.1", 443)
        val back = LinkParser.parse(inj.toShareLink())!!
        assertEquals("104.16.1.1", back.host)
        assertEquals("cdn.example.com", back.query.toMap()["sni"])
        assertEquals("/ws?ed=2048", back.query.toMap()["path"])
        assertEquals("My Server", back.tag)
        assertEquals(uuid, back.uuidOrUser)
    }

    @Test fun trojanPasswordWithSpecialCharsSurvives() {
        val c = ProxyConfig("trojan", "t.example.com", 443, "p@ss+w/rd", listOf("security" to "tls", "type" to "ws"), tag = "T")
        val back = LinkParser.parse(ShareLinkBuilder.build(c))!!
        assertEquals("p@ss+w/rd", back.uuidOrUser)
        assertEquals("t.example.com", back.host)
    }

    @Test fun vmessRoundTripKeepsTransport() {
        val c = ProxyConfig("vmess", "v.example.com", 8443, uuid,
            listOf("tls" to "tls", "sni" to "v.example.com", "host" to "v.example.com", "path" to "/p", "type" to "ws", "fp" to "chrome"), tag = "VM")
        val back = LinkParser.parse(ShareLinkBuilder.build(c.withDial("104.17.2.2", 443)))!!
        val q = back.query.toMap()
        assertEquals("104.17.2.2", back.host); assertEquals(443, back.port)
        assertEquals("ws", q["type"]); assertEquals("/p", q["path"]); assertEquals("chrome", q["fp"]); assertEquals("v.example.com", q["sni"])
        assertEquals("VM", back.tag)
    }

    @Test fun ssRoundTrip() {
        val c = ProxyConfig("ss", "1.2.3.4", 8388, "secret", listOf("method" to "chacha20-ietf-poly1305"), tag = "SS")
        val back = LinkParser.parse(ShareLinkBuilder.build(c))!!
        assertEquals("secret", back.uuidOrUser)
        assertEquals("chacha20-ietf-poly1305", back.query.toMap()["method"])
        assertEquals(8388, back.port)
    }

    @Test fun editFieldsChangesOnlyWhatUserEdited() {
        val c = LinkParser.parse("vless://$uuid@old.example.com:443?type=ws&security=tls&sni=old.example.com&path=%2Fa#Old")!!
        val f = ConfigEditor.fieldsOf(c).copy(name = "New", address = "new.example.com", sni = "new.example.com", path = "")
        val r = ConfigEditor.apply(c, f)
        assertTrue(r is ConfigEditor.Result.Ok)
        val n = (r as ConfigEditor.Result.Ok).config
        assertEquals("new.example.com", n.host); assertEquals("New", n.tag); assertEquals(uuid, n.uuidOrUser)
        assertEquals("new.example.com", n.query.toMap()["sni"])
        assertFalse(n.query.toMap().containsKey("path"))
        assertEquals("ws", n.query.toMap()["type"])
        assertEquals(n.raw, n.toShareLink()) // لینک تازه همان raw است
    }

    @Test fun editRejectsBadPort() {
        val c = LinkParser.parse("vless://$uuid@a.example.com:443?type=ws&security=tls#A")!!
        assertTrue(ConfigEditor.apply(c, ConfigEditor.fieldsOf(c).copy(port = "70000")) is ConfigEditor.Result.Invalid)
        assertTrue(ConfigEditor.apply(c, ConfigEditor.fieldsOf(c).copy(address = "")) is ConfigEditor.Result.Invalid)
    }

    @Test fun parseManyReadsLinesAndBase64Sub() {
        val links = "vless://$uuid@a.example.com:443?type=ws#A\ntrojan://pw@b.example.com:443?security=tls#B\nnot a link"
        assertEquals(2, ConfigEditor.parseMany(links).size)
        val b64 = java.util.Base64.getEncoder().encodeToString(links.toByteArray())
        assertEquals(2, ConfigEditor.parseMany(b64).size)
    }

    @Test fun logRedactsSecrets() {
        val m = DiagLogger.redact("ACTIVATE vless://$uuid@h.com:443 {\"id\":\"abc\",\"password\":\"x\"} trojan://pw@h:1")
        assertFalse(m.contains(uuid)); assertFalse(m.contains("\"abc\"")); assertFalse(m.contains("pw@"))
        assertNotNull(m)
    }
}
