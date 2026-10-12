package com.helboy.vpnclub.corex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * «ساب‌های من»: لینک‌های ساب شخصی کاربر (پنل‌های BPB / Zeus نصب‌شده از داخل اپ، یا دستی).
 * سرورهای این ساب‌ها در اسکن‌ها همیشه اول و کامل تست می‌شوند (نه نمونه‌ی تصادفی).
 */
data class MySub(
    val id: String,
    val name: String,
    val url: String,
    /** bpb | zeus | manual */
    val kind: String = "manual",
    val panelUrl: String = "",
    val lastFetchAt: Long = 0,
    val configCount: Int = 0,
    val aliveCount: Int = 0,
    val bestMs: Long = -1,
    val lastError: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("url", url).put("kind", kind)
        .put("panel", panelUrl).put("at", lastFetchAt).put("n", configCount).put("alive", aliveCount)
        .put("best", bestMs).put("err", lastError)

    companion object {
        fun fromJson(o: JSONObject) = MySub(o.optString("id"), o.optString("name"), o.optString("url"),
            o.optString("kind", "manual"), o.optString("panel"), o.optLong("at"), o.optInt("n"),
            o.optInt("alive"), o.optLong("best", -1), o.optString("err"))

        fun newId() = "sub-" + java.lang.Long.toString(System.currentTimeMillis(), 36)
    }
}

/**
 * یک سرور از یک ساب: لینک خام + نتیجه‌ی آخرین تست (−۱ = پینگ نداد) + دلیل.
 * اگر با پروفایل فرگمنت/IP تمیز جواب داده، همان ترکیب ([variant]، [dialHost]) ذخیره می‌شود تا اتصال دستی هم همان باشد.
 */
data class SubServer(
    val raw: String, val ms: Long = 0, val note: String = "", val supported: Boolean = true,
    val variant: String = "", val dialHost: String = "", val dialPort: Int = 0, val frag: Boolean = false,
) {
    val name: String get() = LinkParser.parse(raw)?.let { it.tag.ifEmpty { it.host } } ?: raw.take(30)

    /** ServerEntry آماده‌ی اتصال با همان ترکیبی که تست شد. */
    fun toEntry(source: String): ServerEntry? {
        val c = LinkParser.parse(raw) ?: return null
        val cfg = if (dialHost.isNotEmpty() && (dialHost != c.host || dialPort != c.port)) c.withDial(dialHost, if (dialPort > 0) dialPort else c.port) else c
        return ServerEntry(cfg, variant.ifEmpty { null }, frag, latencyMs = ms.coerceAtLeast(0), source = source)
    }

    fun toJson(): JSONObject = JSONObject().put("raw", raw).put("ms", ms).put("note", note).put("ok", supported)
        .put("v", variant).put("dh", dialHost).put("dp", dialPort).put("frag", frag)

    companion object {
        fun fromJson(o: JSONObject) = SubServer(o.optString("raw"), o.optLong("ms"), o.optString("note"), o.optBoolean("ok", true),
            o.optString("v"), o.optString("dh"), o.optInt("dp"), o.optBoolean("frag"))

        fun of(e: ServerEntry, ms: Long, note: String) = SubServer(e.config.raw, ms, note, true,
            e.variantId.orEmpty(), e.config.dialHost, e.config.dialPort, e.useFrag)
    }
}

class SubStore(context: Context) {
    private val dir = context.filesDir
    private val file = File(context.filesDir, "my_subs.json")

    private fun serversFile(id: String) = File(dir, "my_sub_${id.filter { it.isLetterOrDigit() || it == '-' }}.json")

    fun loadServers(id: String): List<SubServer> = synchronized(lock) {
        try {
            val f = serversFile(id); if (!f.exists()) return emptyList()
            val a = JSONArray(f.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(SubServer::fromJson) }.filter { it.raw.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    fun saveServers(id: String, list: List<SubServer>) = synchronized(lock) {
        try { serversFile(id).writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()) } catch (_: Exception) {}
    }

    fun deleteServers(id: String) = synchronized(lock) { serversFile(id).delete() }
    private val lock = Any()

    fun load(): List<MySub> = synchronized(lock) {
        try {
            if (!file.exists()) return emptyList()
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(MySub::fromJson) }.filter { it.url.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
    }

    fun save(list: List<MySub>) = synchronized(lock) {
        try { file.writeText(JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()) } catch (_: Exception) {}
    }
}
