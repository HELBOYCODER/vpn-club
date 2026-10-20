package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the no-root SNI spoof (the Rsta engine) needs besides the profile: the methods, and the reading and rewriting of the
 * proxy outbound of an Xray configuration.
 *
 * The engine is a local forwarder: the proxy outbound is pointed at it (127.0.0.1:port), and it connects to the real
 * server's address with the disguised ClientHello. A profile made for the Rsta app points at the engine already, at
 * 127.0.0.1:40443; its real address is then the "clean IP" of the profile, and the address of the profile as it stands
 * says nothing about the server, so a ping of it could only ever measure this device.
 */
object SniSpoofConfig {

    const val DEFAULT_METHOD = "combined"
    const val DEFAULT_CONNECT_IP = "104.18.38.202"
    const val DEFAULT_CONNECT_PORT = 443
    const val DEFAULT_FAKE_SNI = "cdnjs.cloudflare.com"

    /** The port a configuration made for the Rsta app points its proxy at. */
    const val RSTA_LOCAL_PORT = 40443

    var defaultConnectIp: String = DEFAULT_CONNECT_IP
    var defaultConnectPort: Int = DEFAULT_CONNECT_PORT
    var defaultFakeSni: String = DEFAULT_FAKE_SNI
    var defaultMethod: String = DEFAULT_METHOD

    val METHODS = listOf(
        "combined",
        "fragment",
        "random_split",
        "seg2delay",
        "sni_triplicate",
        "oob",
        "fake_sni",
        "auto_ttl",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** RstaNG's rule: a profile whose server is 127.0.0.1 (or localhost) on [RSTA_LOCAL_PORT] is routed through the spoof engine. */
    fun isRstaTarget(server: String?, port: Int?): Boolean {
        val s = server?.trim()?.lowercase() ?: return false
        return (s == "127.0.0.1" || s == "localhost") && port == RSTA_LOCAL_PORT
    }

    fun isSpoofTarget(address: String?, port: Int?): Boolean {
        val a = address?.trim()?.trim('[', ']')?.lowercase().orEmpty()
        return isLoopback(a) || a == "sni-spoof" || port == RSTA_LOCAL_PORT
    }

    fun method(raw: String): String = raw.trim().takeIf { it in METHODS } ?: defaultMethod

    fun isLoopback(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase()
        return h == "localhost" || h == "::1" || h.startsWith("127.")
    }

    /**
     * Where the engine connects for a profile whose proxy outbound is [realHost]:[realPort]: the clean IP when there is
     * one, else the host itself; the port of the profile, else the link's (443 when the link points at this device).
     * Falls back to [defaultConnectIp] and [defaultConnectPort] when pointing at loopback.
     */
    fun target(settings: SniSpoofProfile, realHost: String, realPort: Int): Pair<String, Int>? {
        val candidate = settings.cleanIp.trim().ifEmpty { realHost.trim() }
        val host = if (candidate.isEmpty() || isLoopback(candidate) || candidate == "sni-spoof") {
            defaultConnectIp
        } else {
            candidate
        }
        val port = settings.connectPort.takeIf { it in 1..65535 && it != RSTA_LOCAL_PORT }
            ?: if (isLoopback(realHost) || realPort !in 1..65535 || realPort == RSTA_LOCAL_PORT) defaultConnectPort else realPort
        return host to port
    }

    /** The address and port of the proxy outbound of [xray]: the outbound tagged "proxy", else the first one. */
    fun outboundOf(xray: String): Pair<String, Int>? {
        val root = parse(xray) ?: return null
        val node = nodeOf(root) ?: return null
        val host = node["address"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val port = node["port"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return null
        return host to port
    }

    /** [xray] with the proxy outbound pointed at [host]:[port]; unchanged when it has no such outbound. */
    fun withOutbound(xray: String, host: String, port: Int): String {
        val root = parse(xray) ?: return xray
        val outbounds = root["outbounds"] as? JsonArray ?: return xray
        val index = proxyIndex(outbounds).takeIf { it >= 0 } ?: return xray
        val outbound = outbounds[index] as? JsonObject ?: return xray
        val settings = outbound["settings"] as? JsonObject ?: return xray
        val key = if ("vnext" in settings) "vnext" else "servers"
        val list = settings[key] as? JsonArray ?: return xray
        val node = list.firstOrNull() as? JsonObject ?: return xray
        val newNode = JsonObject(node + mapOf("address" to JsonPrimitive(host), "port" to JsonPrimitive(port)))
        val newList = JsonArray(listOf(newNode) + list.drop(1))
        val newOutbound = JsonObject(outbound + ("settings" to JsonObject(settings + (key to newList))))
        val newOutbounds = JsonArray(outbounds.toMutableList().also { it[index] = newOutbound })
        return JsonObject(root + ("outbounds" to newOutbounds)).toString()
    }

    private fun parse(xray: String): JsonObject? = runCatching { json.parseToJsonElement(xray) as? JsonObject }.getOrNull()

    private fun proxyIndex(outbounds: JsonArray): Int {
        val tagged = outbounds.indexOfFirst { (it as? JsonObject)?.get("tag")?.jsonPrimitive?.contentOrNull == "proxy" }
        return if (tagged >= 0) tagged else if (outbounds.isNotEmpty()) 0 else -1
    }

    private fun nodeOf(root: JsonObject): JsonObject? {
        val outbounds = root["outbounds"] as? JsonArray ?: return null
        val index = proxyIndex(outbounds).takeIf { it >= 0 } ?: return null
        val settings = (outbounds[index] as? JsonObject)?.get("settings") as? JsonObject ?: return null
        val list = (settings["vnext"] as? JsonArray) ?: (settings["servers"] as? JsonArray) ?: return null
        return list.firstOrNull() as? JsonObject
    }
}
