package com.helboy.vpnclub.corex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * روش Netra، Hysteria2، بدون سرور، مسیریابی کاربر و اصلاحات ZeroNet.
 * JSON هر کانفیگ در tmp نوشته می‌شود تا با باینری همان کامیت هسته (xray run -test) هم بررسی شود.
 */
class ZeroNetFeaturesTest {
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val netraFm = """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","lengths":["0","104","1"],"delays":["0"],"maxSplit":"0"}},{"type":"fragment","settings":{"packets":"1-1","lengths":["114","1"],"delays":["1"],"maxSplit":"11"}}]}"""
    private val netraCs = "TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256:TLS_AES_128_GCM_SHA256"

    private fun dump(name: String, json: JSONObject) =
        java.io.File(System.getProperty("java.io.tmpdir"), "hb_$name.json").writeText(json.toString(2))

    private fun stream(json: JSONObject) = json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")

    /** لینک همان شکلی که پنل Netra می‌سازد (vless + ws + tls + ed=2560 + cs + fm). */
    private fun netraLink(): String {
        val e = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
        return "vless://$uuid@104.18.2.2:443?encryption=none&host=a.b.workers.dev&type=ws&security=tls" +
            "&path=${e("/vl/abcdEFGH1234?ed=2560")}&sni=a.b.workers.dev&fp=chrome&alpn=http%2F1.1&cs=${e(netraCs)}&fm=${e(netraFm)}#netra"
    }

    @Test fun netraLinkKeepsItsOwnCsAndFm() {
        val c = LinkParser.parse(netraLink())!!
        assertTrue(XrayCore.isSupported(c))
        val xr = XrayCore(10808).apply { setVariant(IranVariants.byId(XrayCore.NETRA_VARIANT)) }
        val json = JSONObject(xr.buildConfig(c))
        val st = stream(json)
        val tls = st.getJSONObject("tlsSettings")
        assertEquals(netraCs, tls.getString("cipherSuites"))
        assertEquals("a.b.workers.dev", tls.getString("serverName"))
        assertEquals(JSONObject(netraFm).toString(), st.getJSONObject("finalmask").toString())
        assertEquals("/vl/abcdEFGH1234?ed=2560", st.getJSONObject("wsSettings").getString("path"))
        assertFalse(tls.has("pinnedPeerCertSha256"))
        dump("netra", json)
    }

    @Test fun netraVariantOnPlainCdnLink() {
        val c = LinkParser.parse("vless://$uuid@x.workers.dev:443?encryption=none&type=ws&security=tls&host=x.workers.dev&path=%2F#p")!!
        val v = IranVariants.byId(XrayCore.NETRA_VARIANT)!!
        assertFalse(v.useFrag) // فرگمنت داخل هسته، نه FragmentProxy
        val json = JSONObject(XrayCore(10808).apply { setVariant(v) }.buildConfig(c))
        val st = stream(json)
        assertEquals(IranVariants.CIPHER_NETRA, st.getJSONObject("tlsSettings").getString("cipherSuites"))
        val frag = st.getJSONObject("finalmask").getJSONArray("tcp").getJSONObject(0).getJSONObject("settings")
        assertEquals("tlshello", frag.getString("packets"))
        assertEquals("1", frag.getJSONArray("lengths").getString(2)) // آخرین length نباید ۰ باشد (قانون هسته)
        assertEquals(listOf("http/1.1"), (0 until st.getJSONObject("tlsSettings").getJSONArray("alpn").length())
            .map { st.getJSONObject("tlsSettings").getJSONArray("alpn").getString(it) })
        dump("netra_variant", json)
        // ترتیب خودکار: «مستقیم» اول، Netra اولین پروفایل جایگزین
        assertEquals(listOf("plain", XrayCore.NETRA_VARIANT), IranVariants.order("auto").take(2).map { it.id })
    }

    @Test fun pcsIsPinnedCertNotCipherSuites() {
        val pin = "a".repeat(64)
        val c = LinkParser.parse("trojan://pw@h.example.com:443?security=tls&type=tcp&pcs=$pin#t")!!
        val tls = stream(JSONObject(XrayCore(10808).buildConfig(c))).getJSONObject("tlsSettings")
        assertEquals(pin, tls.getString("pinnedPeerCertSha256"))
        assertFalse(tls.has("cipherSuites"))
        // pcs خراب (مثلاً لیست سوئیت رمز از نسخه‌های قدیمی) نادیده گرفته می‌شود تا هسته کانفیگ را رد نکند
        assertNull(XrayCore.validPin("TLS_AES_128_GCM_SHA256"))
        assertEquals("aa".repeat(32), XrayCore.validPin(List(32) { "AA" }.joinToString(":"))?.lowercase())
    }

    @Test fun emptyHostFallsBackToSni() {
        val c = LinkParser.parse("vless://$uuid@1.2.3.4:443?encryption=none&type=ws&security=tls&host=&sni=s.example.com&path=#e")!!
        assertEquals("s.example.com", c.sniHost)
        val st = stream(JSONObject(XrayCore(10808).buildConfig(c)))
        assertEquals("s.example.com", st.getJSONObject("wsSettings").getJSONObject("headers").getString("Host"))
        assertEquals("/", st.getJSONObject("wsSettings").getString("path"))
        val c2 = LinkParser.parse("vless://$uuid@d.example.com:443?encryption=none&type=ws&security=tls&sni=#e")!!
        assertEquals("d.example.com", c2.sniHost)
    }

    @Test fun hysteria2WithSalamander() {
        val c = LinkParser.parse("hysteria2://secretpass@hy.example.com:8443/?sni=hy.example.com&obfs=salamander&obfs-password=obfspw#hy")!!
        assertTrue(XrayCore.isSupported(c))
        val json = JSONObject(XrayCore(10808).buildConfig(c))
        val out = json.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria", out.getString("protocol"))
        assertEquals(2, out.getJSONObject("settings").getInt("version"))
        assertEquals(8443, out.getJSONObject("settings").getInt("port"))
        val st = out.getJSONObject("streamSettings")
        assertEquals("hysteria", st.getString("network"))
        assertEquals("secretpass", st.getJSONObject("hysteriaSettings").getString("auth"))
        val mask = st.getJSONObject("finalmask").getJSONArray("udp").getJSONObject(0)
        assertEquals("salamander", mask.getString("type"))
        assertEquals("obfspw", mask.getJSONObject("settings").getString("password"))
        dump("hy2", json)
        assertFalse(XrayCore.isSupported(LinkParser.parse("hy2://p@h.example.com:443?obfs=unknown#x")!!))
    }

    @Test fun routingRulesSplit() {
        val (ips, doms) = XrayCore.splitRules("digikala.com\n*.bank.ir, 10.0.0.0/8\ngeosite:category-ads-all\ngeoip:ir\n2001:db8::/32\n# comment")
        assertEquals(listOf("10.0.0.0/8", "geoip:ir", "2001:db8::/32"), ips)
        assertEquals(listOf("domain:digikala.com", "domain:bank.ir", "geosite:category-ads-all"), doms)
        // هسته‌ی سرویس: sniff برای قوانین دامنه + ایران مستقیم
        val c = LinkParser.parse("vless://$uuid@r.example.com:443?encryption=none&type=tcp&security=tls#r")!!
        val json = JSONObject(XrayCore(10808, useRouting = true).buildConfig(c))
        assertTrue(json.getJSONArray("inbounds").getJSONObject(0).getJSONObject("sniffing").getBoolean("routeOnly"))
        dump("routing", json)
    }

    @Test fun editorHasHy2AndCorrectTlsFields() {
        val keys = ConfigEditor.securitySpecs("tls").map { it.key }
        assertTrue("cs" in keys && "pcs" in keys && "vcn" in keys)
        assertTrue(ConfigEditor.protocolSpecs("hysteria2").any { it.key == "obfs-password" })
        val c = LinkParser.parse("hysteria2://pw@h.example.com:443?obfs=salamander&obfs-password=a#x")!!
        val r = ConfigEditor.applyFull(c, "x", "h.example.com", "443", "pw",
            ConfigEditor.paramsOf(c) + ("obfs-password" to "b"))
        val out = (r as ConfigEditor.Result.Ok).config.query
        assertEquals(1, out.count { it.first == "obfs-password" })
        assertEquals("b", out.toMap()["obfs-password"])
    }
}
