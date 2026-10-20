package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the minimal Xray exit configuration that the Aether core process dials out through,
 * so that finalMask and dialMode apply to what leaves the device.
 * Ported from PattNG's CoreOutboundBuilder.toOutboundAetherExit.
 */
object AetherExitJson {

    const val EXIT_SOCKS_PORT = LocalPorts.AETHER_EXIT

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The tag of the exit's inbound: the front's routing sends everything that arrives on it straight out. */
    const val EXIT_INBOUND_TAG = "socks-exit"
    const val EXIT_OUTBOUND_TAG = "freedom-exit"

    /**
     * [frontConfig] with the exit in it: Xray is one process per app, so the routing front of an Aether session and
     * the exit the core dials out through have to be one configuration. The exit's inbound and its freedom outbound
     * are added, and a rule at the head of the routing sends what arrives on the exit's inbound out of the exit's
     * outbound before the user's rules see it; a rule of theirs could otherwise send the core's own dialling back
     * into the core.
     */
    fun mergeExit(frontConfig: String, finalMaskJson: String?, dialMode: String?, domainStrategy: String?): String {
        val front = json.parseToJsonElement(frontConfig) as JsonObject
        val exit = json.parseToJsonElement(exitConfig(finalMaskJson, dialMode, domainStrategy)) as JsonObject
        fun array(root: JsonObject, key: String) = (root[key] as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
        val routing = front["routing"] as? JsonObject ?: JsonObject(emptyMap())
        val sendExitOut = buildJsonObject {
            put("type", "field")
            putJsonArray("inboundTag") { add(JsonPrimitive(EXIT_INBOUND_TAG)) }
            put("outboundTag", EXIT_OUTBOUND_TAG)
        }
        val merged = buildJsonObject {
            front.forEach { (key, value) ->
                when (key) {
                    "inbounds" -> put(key, kotlinx.serialization.json.JsonArray(array(front, "inbounds") + array(exit, "inbounds")))
                    "outbounds" -> put(key, kotlinx.serialization.json.JsonArray(array(front, "outbounds") + array(exit, "outbounds")))
                    "routing" -> put(
                        key,
                        JsonObject(routing + ("rules" to kotlinx.serialization.json.JsonArray(listOf(sendExitOut) + array(routing, "rules")))),
                    )
                    else -> put(key, value)
                }
            }
            if ("routing" !in front) put("routing", JsonObject(mapOf("rules" to kotlinx.serialization.json.JsonArray(listOf(sendExitOut)))))
        }
        return merged.toString()
    }

    fun exitConfig(finalMaskJson: String?, dialMode: String?, domainStrategy: String?): String {
        val maskObj = finalMaskJson?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        }
        val dial = dialMode?.trim()?.takeIf { it.isNotEmpty() }
        val strategy = domainStrategy?.trim()?.takeIf { it.isNotEmpty() }

        val root = buildJsonObject {
            putJsonObject("log") {
                put("loglevel", "warning")
            }
            putJsonArray("inbounds") {
                add(
                    buildJsonObject {
                        put("tag", EXIT_INBOUND_TAG)
                        put("port", EXIT_SOCKS_PORT)
                        put("listen", "127.0.0.1")
                        put("protocol", "socks")
                        putJsonObject("settings") {
                            put("auth", "noauth")
                            put("udp", true)
                        }
                    },
                )
            }
            putJsonArray("outbounds") {
                add(
                    buildJsonObject {
                        put("tag", EXIT_OUTBOUND_TAG)
                        put("protocol", "freedom")
                        putJsonObject("settings") {
                            if (dial != null) put("dialMode", dial)
                            if (maskObj != null) put("finalMask", maskObj)
                            if (strategy != null) put("domainStrategy", strategy)
                        }
                    },
                )
            }
        }
        return root.toString()
    }
}
