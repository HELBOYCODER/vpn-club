package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelDeployTest {

    @Test fun tokenUrlRequestsNeededPermissions() {
        val u = java.net.URLDecoder.decode(CloudflareDeployer.tokenUrl(), "UTF-8")
        for (k in listOf("workers_scripts", "workers_kv_storage", "account_settings"))
            assertTrue("missing $k", u.contains("\"key\":\"$k\""))
        assertFalse(u.contains("\"key\":\"d1\""))
        assertTrue(java.net.URLDecoder.decode(CloudflareDeployer.tokenUrl(CloudflareDeployer.Panel.ZEUS), "UTF-8").contains("\"key\":\"d1\""))
        assertTrue(u.startsWith("https://dash.cloudflare.com/profile/api-tokens?"))
        assertTrue(u.contains("accountId=*") && u.contains("zoneId=all"))
    }

    @Test fun netraBindingsAndPrivatePathMatchPinnedWorker() {
        val bindings = CloudflareDeployer.netraBindings("namespace", "unique-uuid", "unique-password", "private-path")
        val names = (0 until bindings.length()).map { bindings.getJSONObject(it).getString("name") }.toSet()
        assertEquals(setOf("kv", "UUID", "TR_PASS", "SUB_PATH"), names)
        assertFalse(bindings.toString().contains("CF_API_TOKEN"))
        val (panel, sub) = CloudflareDeployer.netraUrls("hb.example.workers.dev", "private-path")
        assertEquals("https://hb.example.workers.dev/private-path/panel", panel)
        assertEquals("https://hb.example.workers.dev/private-path/sub/raw?app=xray&raw=1", sub)
    }

    @Test fun workerNameRules() {
        assertTrue(CloudflareDeployer.isValidName("bpb-a1b2c3d4"))
        assertFalse(CloudflareDeployer.isValidName("-bad"))
        assertFalse(CloudflareDeployer.isValidName("bad-"))
        assertFalse(CloudflareDeployer.isValidName("Bad"))
        assertFalse(CloudflareDeployer.isValidName(""))
        repeat(20) { assertTrue(CloudflareDeployer.isValidName(CloudflareDeployer.randomName("zeus"))) }
    }

    @Test fun recognizesBothWorkerExportForms() {
        assertTrue(CloudflareDeployer.looksLikeWorkerModule("var a=1;export{Bo as default};"))   // BPB (bundled)
        assertTrue(CloudflareDeployer.looksLikeWorkerModule("export default { async fetch(){} }")) // Zeus
        assertFalse(CloudflareDeployer.looksLikeWorkerModule("<html>404 Not Found</html>"))
    }

    @Test fun mySubJsonRoundTrip() {
        val s = MySub("sub-1", "BPB · x", "https://x.y.workers.dev/p/sub/raw?app=xray", "bpb", "https://x.y.workers.dev/p/panel",
            123L, 40, 7, 210L, "")
        assertEquals(s, MySub.fromJson(s.toJson()))
    }

    @Test fun subServerKeepsWorkingVariantAndDial() {
        val raw = "vless://11111111-2222-3333-4444-555555555555@w.example.workers.dev:443?type=ws&security=tls&sni=w.example.workers.dev&host=w.example.workers.dev#B"
        val s = SubServer(raw, 180, "frag", true, "irancell-ff", "104.16.1.1", 443, true)
        val back = SubServer.fromJson(s.toJson())
        assertEquals(s, back)
        val e = back.toEntry("sub-1")!!
        assertEquals("104.16.1.1", e.config.dialHost)
        assertEquals("w.example.workers.dev", e.config.sniHost)
        assertEquals("irancell-ff", e.variantId); assertTrue(e.useFrag); assertEquals("sub-1", e.source)
        assertEquals(raw, e.config.raw)
        // سرور تست‌نشده: بدون dial/variant
        val plain = SubServer(raw).toEntry("sub-1")!!
        assertFalse(plain.injected); assertEquals(null, plain.variantId)
    }

    @Test fun serverEntryKeepsSource() {
        val c = LinkParser.parse("vless://11111111-2222-3333-4444-555555555555@a.example.com:443?type=ws&security=tls#A")!!
        val e = ServerEntry(c, cf = true, source = "sub-9", latencyMs = 120)
        assertEquals("sub-9", ServerEntry.fromJson(e.toJson())!!.source)
    }
}
