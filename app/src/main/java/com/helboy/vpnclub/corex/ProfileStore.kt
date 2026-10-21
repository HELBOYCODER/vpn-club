package com.helboy.vpnclub.corex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * مخزن محلی کانفیگ‌ها و نتایج سلامت.
 *
 * یک فایل JSON ساده در filesDir — بدون دیتابیس، بدون وابستگی.
 * طرح داده ثابت است تا نسخه‌های بعدی بتوانند بخوانند (متن نسخه در ریشه فایل).
 */
class ProfileStore(context: Context) {

    private val file = File(context.filesDir, "profiles.json")
    private val lock = Any()

    data class Snapshot(
        val configs: List<ProxyConfig>,
        val health: Map<String, HealthResult>,
        val cleanIps: List<CloudflareScanner.CleanIp>,
        val lastSyncMs: Long
    )

    fun load(): Snapshot = synchronized(lock) {
        if (!file.exists()) return Snapshot(emptyList(), emptyMap(), emptyList(), 0L)
        return try {
            val root = JSONObject(file.readText())
            val configs = root.optJSONArray("configs")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> configFromJson(arr.optJSONObject(i)) }
            } ?: emptyList()
            val health = root.optJSONObject("health")?.let { obj ->
                obj.keys().asSequence().mapNotNull { key ->
                    obj.optJSONObject(key)?.let { key to healthFromJson(key, it) }
                }.toMap()
            } ?: emptyMap()
            val ips = root.optJSONArray("cleanIps")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let {
                        CloudflareScanner.CleanIp(
                            it.optString("ip"), it.optInt("port"), it.optLong("tlsMs")
                        )
                    }
                }
            } ?: emptyList()
            Snapshot(configs, health, ips, root.optLong("lastSyncMs"))
        } catch (_: Exception) {
            Snapshot(emptyList(), emptyMap(), emptyList(), 0L)
        }
    }

    fun save(
        configs: List<ProxyConfig>,
        health: Map<String, HealthResult>,
        cleanIps: List<CloudflareScanner.CleanIp>,
        lastSyncMs: Long
    ) = synchronized(lock) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("lastSyncMs", lastSyncMs)
        root.put("configs", JSONArray().apply { configs.forEach { put(configToJson(it)) } })
        root.put("health", JSONObject().apply {
            health.forEach { (k, v) ->
                put(k, JSONObject().apply {
                    put("tcpMs", v.tcpMs); put("down", v.downloadBps)
                    put("up", v.uploadBps); put("error", v.error ?: "")
                })
            }
        })
        root.put("cleanIps", JSONArray().apply {
            cleanIps.forEach {
                put(JSONObject().apply {
                    put("ip", it.ip); put("port", it.port); put("tlsMs", it.tlsMs)
                })
            }
        })
        // ponytail: نوشتن اتمیک لازم نیست — فایل مشتق‌شده است و از شبکه بازسازی می‌شود.
        file.writeText(root.toString())
    }

    fun clear() = synchronized(lock) { file.delete() }

    private fun configToJson(c: ProxyConfig) = JSONObject().apply {
        put("scheme", c.scheme); put("host", c.host); put("port", c.port)
        put("user", c.uuidOrUser); put("tag", c.tag); put("raw", c.raw)
        put("dialHost", c.dialHost); put("dialPort", c.dialPort)
        put("q", JSONArray().apply {
            c.query.forEach { (k, v) -> put(JSONArray().apply { put(k); put(v) }) }
        })
    }

    private fun configFromJson(o: JSONObject?): ProxyConfig? {
        o ?: return null
        val host = o.optString("host").takeIf { it.isNotEmpty() } ?: return null
        val q = o.optJSONArray("q")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONArray(i)?.let { pair ->
                    pair.optString(0) to pair.optString(1)
                }
            }
        } ?: emptyList()
        val c = ProxyConfig(
            scheme = o.optString("scheme"), host = host, port = o.optInt("port"),
            uuidOrUser = o.optString("user"), query = q,
            tag = o.optString("tag"), raw = o.optString("raw")
        )
        val dh = o.optString("dialHost")
        if (dh.isNotEmpty()) c.dialHost = dh
        val dp = o.optInt("dialPort", 0)
        if (dp > 0) c.dialPort = dp
        return c
    }

    private fun healthFromJson(key: String, o: JSONObject): HealthResult {
        val config = ProxyConfig(scheme = "", host = key.substringBefore(':'), port = 0)
        val err = o.optString("error").takeIf { it.isNotEmpty() }
        return HealthResult(config, o.optLong("tcpMs"), o.optDouble("down", 0.0), o.optDouble("up", 0.0), err)
    }
}
