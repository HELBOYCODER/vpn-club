package com.helboy.vpnclub.corex

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Native libxray binary accepts standard Xray JSON, not PattNG flat settings. */
class NativeXrayConfigTest {
    @Test fun vlessBecomesStandardVnext() {
        val input = outbound("vless", "id" to "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            "encryption" to "none", "flow" to "xtls-rprx-vision")
        val result = NativeXrayConfig.standardize(input)
        val node = proxy(result).getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("node.example", node.getString("address"))
        assertEquals(443, node.getInt("port"))
        val user = node.getJSONArray("users").getJSONObject(0)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", user.getString("id"))
        assertEquals("xtls-rprx-vision", user.getString("flow"))
        assertFalse(proxy(result).getJSONObject("streamSettings").has("finalmask"))
    }

    @Test fun vmessBecomesStandardVnext() {
        val node = proxy(NativeXrayConfig.standardize(
            outbound("vmess", "id" to "test-id", "alterId" to 0, "security" to "auto")
        )).getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("auto", node.getJSONArray("users").getJSONObject(0).getString("security"))
    }

    @Test fun trojanAndShadowsocksUseServerLists() {
        val t = proxy(NativeXrayConfig.standardize(outbound("trojan", "password" to "secret")))
        assertEquals("secret", t.getJSONObject("settings").getJSONArray("servers").getJSONObject(0).getString("password"))
        val ss = proxy(NativeXrayConfig.standardize(outbound("shadowsocks", "password" to "secret",
            "method" to "aes-256-gcm")))
        assertEquals("aes-256-gcm", ss.getJSONObject("settings").getJSONArray("servers")
            .getJSONObject(0).getString("method"))
    }

    @Test fun testsHaveRealSocksInboundWithUniquePort() {
        val json = JSONObject(NativeXrayConfig.withSocksPort(outbound("trojan", "password" to "secret"), 13456))
        assertEquals(13456, json.getJSONArray("inbounds").getJSONObject(0).getInt("port"))
        assertEquals("socks", json.getJSONArray("inbounds").getJSONObject(0).getString("protocol"))
    }

    private fun proxy(json: String): JSONObject =
        JSONObject(json).getJSONArray("outbounds").getJSONObject(0)

    private fun outbound(protocol: String, vararg properties: Pair<String, Any>): String {
        val settings = JSONObject().put("address", "node.example").put("port", 443)
        for ((name, value) in properties) settings.put(name, value)
        return JSONObject().put("outbounds", JSONArray().put(JSONObject()
            .put("protocol", protocol)
            .put("settings", settings)
            .put("streamSettings", JSONObject().put("finalmask", JSONObject().put("tcp", JSONArray())))))
            .toString()
    }
}
