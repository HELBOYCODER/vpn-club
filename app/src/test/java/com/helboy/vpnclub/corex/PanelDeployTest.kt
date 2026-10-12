package com.helboy.vpnclub.corex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelDeployTest {

    @Test fun tokenUrlRequestsNeededPermissions() {
        val u = java.net.URLDecoder.decode(CloudflareDeployer.tokenUrl(), "UTF-8")
        for (k in listOf("workers_scripts", "workers_kv_storage", "d1", "user_details", "account_settings"))
            assertTrue("missing $k", u.contains("\"key\":\"$k\""))
        assertTrue(u.startsWith("https://dash.cloudflare.com/profile/api-tokens?"))
        assertTrue(u.contains("accountId=*") && u.contains("zoneId=all"))
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

    @Test fun serverEntryKeepsSource() {
        val c = LinkParser.parse("vless://11111111-2222-3333-4444-555555555555@a.example.com:443?type=ws&security=tls#A")!!
        val e = ServerEntry(c, cf = true, source = "sub-9", latencyMs = 120)
        assertEquals("sub-9", ServerEntry.fromJson(e.toJson())!!.source)
    }
}
