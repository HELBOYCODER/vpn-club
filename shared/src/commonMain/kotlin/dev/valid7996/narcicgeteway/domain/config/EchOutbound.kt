package dev.valid7996.narcicgeteway.domain.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * PattNG / v2rayNG: the ECH outbound of a profile is a whole Xray outbound, written as raw JSON,
 * that the ECH config query is sent through (tlsSettings.echSockopt.dialerProxy).
 * It is appended to the generated configuration after every other outbound, exactly as written.
 */
object EchOutbound {

    enum class Error { INVALID_JSON, NEEDS_ECH_CONFIG_LIST, INVALID_TAG }

    sealed interface Result {
        data class Done(val content: String) : Result
        data class Invalid(val error: Error) : Result
        data class TagConflict(val tag: String) : Result
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    fun tagOf(outbound: JsonObject): String? {
        val tag = outbound["tag"] ?: return null
        return (tag as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    fun parse(text: String?): JsonObject? =
        text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    /**
     * Checks the ECH outbound:
     * - Must be valid JSON object
     * - Requires echConfigList to be non-blank
     * - Must have a string "tag" that is not blank, not "direct" or "block", and does not start with "proxy"
     */
    fun validate(echOutbound: String?, echConfigList: String?): Error? {
        if (echOutbound.isNullOrBlank()) return null
        val outbound = parse(echOutbound) ?: return Error.INVALID_JSON
        if (echConfigList.isNullOrBlank()) return Error.NEEDS_ECH_CONFIG_LIST
        val tag = tagOf(outbound)
        if (tag.isNullOrBlank() || tag == "direct" || tag == "block" || tag.startsWith("proxy")) {
            return Error.INVALID_TAG
        }
        return null
    }

    /** [tag] as written, or, when an earlier ECH outbound has it, its first number that no outbound has. */
    fun freeTag(tag: String, usedTags: Set<String>, linkedTags: List<String>): String {
        if (tag !in linkedTags) return tag
        var number = 2
        while ("$tag-$number" in linkedTags || "$tag-$number" in usedTags) number++
        return "$tag-$number"
    }

    /**
     * Checks whether [outbound] can go into a configuration whose outbounds are tagged [tags].
     */
    fun takes(outbound: JsonObject, tags: Set<String>): Boolean {
        val streamSettings = outbound["streamSettings"] as? JsonObject ?: return true
        val tlsSettings = streamSettings["tlsSettings"] as? JsonObject ?: return true
        val text = (tlsSettings["echOutbound"] as? JsonPrimitive)?.contentOrNull ?: return true
        val echConfigList = (tlsSettings["echConfigList"] as? JsonPrimitive)?.contentOrNull
        if (validate(text, echConfigList) != null) return false
        val parsed = parse(text) ?: return false
        val tag = tagOf(parsed) ?: return false
        return tag !in tags
    }

    /**
     * Serializes [configJson] with ECH outbounds:
     * - Extracts `echOutbound` from `streamSettings.tlsSettings` (if present)
     * - Validates each
     * - Adds `echSockopt: { dialerProxy: tag }`
     * - Appends unique ECH outbounds to `outbounds`
     */
    fun serialize(configJson: String): Result {
        val root = try {
            json.parseToJsonElement(configJson) as? JsonObject
                ?: return Result.Done(configJson)
        } catch (_: Exception) {
            return Result.Done(configJson)
        }

        val outboundsArray = root["outbounds"] as? JsonArray ?: return Result.Done(configJson)

        val usedTags = mutableSetOf<String>()
        for (elem in outboundsArray) {
            if (elem is JsonObject) {
                (elem["tag"] as? JsonPrimitive)?.contentOrNull?.let { usedTags.add(it) }
            }
        }

        val linked = mutableListOf<Pair<JsonObject, String>>()
        val modifiedOutbounds = mutableListOf<JsonObject>()
        var hadEch = false

        for (elem in outboundsArray) {
            val outbound = elem as? JsonObject
            if (outbound == null) {
                continue
            }
            val streamSettings = outbound["streamSettings"] as? JsonObject
            val tlsSettings = streamSettings?.get("tlsSettings") as? JsonObject
            val echOutboundText = (tlsSettings?.get("echOutbound") as? JsonPrimitive)?.contentOrNull

            if (tlsSettings != null && !echOutboundText.isNullOrBlank()) {
                hadEch = true
                val echConfigList = (tlsSettings["echConfigList"] as? JsonPrimitive)?.contentOrNull
                validate(echOutboundText, echConfigList)?.let { return Result.Invalid(it) }
                val parsedEch = parse(echOutboundText) ?: return Result.Invalid(Error.INVALID_JSON)

                val tag = linked.firstOrNull { it.first == parsedEch }?.second
                    ?: freeTag(tagOf(parsedEch).orEmpty(), usedTags, linked.map { it.second }).also {
                        linked.add(parsedEch to it)
                    }

                // Rewrite tlsSettings with echSockopt and remove echOutbound
                val newTlsSettings = buildJsonObject {
                    tlsSettings.forEach { (k, v) ->
                        if (k != "echOutbound") put(k, v)
                    }
                    putJsonObject("echSockopt") {
                        put("dialerProxy", tag)
                    }
                }
                val newStreamSettings = buildJsonObject {
                    streamSettings.forEach { (k, v) ->
                        if (k == "tlsSettings") put(k, newTlsSettings)
                        else put(k, v)
                    }
                }
                val newOutbound = buildJsonObject {
                    outbound.forEach { (k, v) ->
                        if (k == "streamSettings") put(k, newStreamSettings)
                        else put(k, v)
                    }
                }
                modifiedOutbounds.add(newOutbound)
            } else {
                modifiedOutbounds.add(outbound)
            }
        }

        if (!hadEch) return Result.Done(configJson)

        linked.firstOrNull { it.second in usedTags }?.let { return Result.TagConflict(it.second) }

        // Append echOutbounds
        for ((echObj, tag) in linked) {
            val taggedEch = buildJsonObject {
                echObj.forEach { (k, v) ->
                    if (k != "tag") put(k, v)
                }
                put("tag", tag)
            }
            modifiedOutbounds.add(taggedEch)
        }

        val newRoot = buildJsonObject {
            root.forEach { (k, v) ->
                if (k == "outbounds") {
                    putJsonArray("outbounds") {
                        modifiedOutbounds.forEach { add(it) }
                    }
                } else {
                    put(k, v)
                }
            }
        }

        return Result.Done(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), newRoot))
    }
}
