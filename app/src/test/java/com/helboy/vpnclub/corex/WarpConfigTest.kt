package com.helboy.vpnclub.corex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** کانفیگ WARP (لینک wireguard:// و JSON هسته) — JSON برای بررسی با خود Xray هم در فایل نوشته می‌شود. */
class WarpConfigTest {
    private val acc = WarpManager.Account(
        privateKey = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() }),
        peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", v4 = "172.16.0.2",
        v6 = "2606:4700:110:867e:6166:7544:1900:2729", reserved = listOf(190, 168, 74), id = "x", token = "t")

    @Test fun linkRoundTripKeepsKeysAndReserved() {
        val c = WarpManager.toConfig(acc, "162.159.192.1", 2408)!!
        assertEquals("wireguard", c.scheme)
        assertEquals(acc.privateKey, c.uuidOrUser) // + و / و = سالم برگشت
        val q = c.query.toMap()
        assertEquals(acc.peerPublicKey, q["publickey"]); assertEquals("190,168,74", q["reserved"])
        assertTrue(q["address"]!!.contains("172.16.0.2/32")); assertTrue(XrayCore.isSupported(c))
        assertEquals("162.159.192.1", c.host); assertEquals(2408, c.port)
    }

    @Test fun coreJsonHasWireguardAndNoise() {
        val c = WarpManager.toConfig(acc, "162.159.193.10", 894)!!
        XrayCore.warpNoise = true
        val json = JSONObject(XrayCore(10808).buildConfig(c))
        val outs = json.getJSONArray("outbounds")
        val wg = outs.getJSONObject(0)
        assertEquals("wireguard", wg.getString("protocol"))
        val st = wg.getJSONObject("settings")
        assertEquals(acc.privateKey, st.getString("secretKey"))
        assertEquals("162.159.193.10:894", st.getJSONArray("peers").getJSONObject(0).getString("endpoint"))
        assertEquals(3, st.getJSONArray("reserved").length())
        assertEquals(XrayCore.NOISE_TAG, wg.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
        assertTrue((0 until outs.length()).any { outs.getJSONObject(it).optString("tag") == XrayCore.NOISE_TAG })
        java.io.File(System.getProperty("java.io.tmpdir"), "hb_warp_config.json").writeText(json.toString(2))
    }
}
