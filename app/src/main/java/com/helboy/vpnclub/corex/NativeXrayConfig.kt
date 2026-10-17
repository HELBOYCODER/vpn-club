package com.helboy.vpnclub.corex

import org.json.JSONArray
import org.json.JSONObject

/**
 * The MSN-GUARD libxray.so is the actual Xray command-line engine.
 * PattNG libv2ray.aar cannot coexist with the Psiphon GoMobile AAR:
 * both ship different libgojni.so and incompatible go.Seq runtimes.
 * Translate the existing flat PattNG outbounds into standard Xray JSON,
 * and invoke the executable in its own OS process (no GoMobile JNI).
 */
internal object NativeXrayConfig {
    fun standardize(config: String): String {
        val root = JSONObject(config)
        val outbounds = root.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val out = outbounds.getJSONObject(i)
            val protocol = out.optString("protocol")
            val flat = out.optJSONObject("settings") ?: continue
            if (!flat.has("address") || !flat.has("port")) continue
            val addr = flat.getString("address")
            val port = flat.getInt("port")
            val server = JSONObject().put("address", addr).put("port", port)
            when (protocol) {
                "vless" -> {
                    val user = JSONObject().put("id", flat.getString("id"))
                        .put("encryption", flat.optString("encryption", "none"))
                    flat.optString("flow").takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
                    server.put("users", JSONArray().put(user))
                    out.put("settings", JSONObject().put("vnext", JSONArray().put(server)))
                }
                "vmess" -> {
                    val user = JSONObject().put("id", flat.getString("id"))
                        .put("alterId", flat.optInt("alterId", 0))
                        .put("security", flat.optString("security", "auto"))
                    server.put("users", JSONArray().put(user))
                    out.put("settings", JSONObject().put("vnext", JSONArray().put(server)))
                }
                "trojan" -> {
                    server.put("password", flat.getString("password"))
                    out.put("settings", JSONObject().put("servers", JSONArray().put(server)))
                }
                "shadowsocks" -> {
                    server.put("method", flat.getString("method"))
                        .put("password", flat.getString("password"))
                    out.put("settings", JSONObject().put("servers", JSONArray().put(server)))
                }
                else -> continue
            }
            // finalmask is specific to the previous PattNG fork. Standard
            // Xray does not understand that option; preserve ordinary TLS.
            out.optJSONObject("streamSettings")?.remove("finalmask")
        }
        return root.toString()
    }

    fun withSocksPort(outboundOnly: String, port: Int): String {
        val json = JSONObject(outboundOnly)
        json.put("inbounds", JSONArray().put(JSONObject()
            .put("tag", "socks-in")
            .put("listen", "127.0.0.1")
            .put("port", port)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))))
        return standardize(json.toString())
    }
}
